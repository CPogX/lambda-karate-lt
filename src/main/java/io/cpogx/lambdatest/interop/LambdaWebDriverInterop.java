package io.cpogx.lambdatest.interop;

import com.intuit.karate.Json;
import com.intuit.karate.driver.WebDriver;
import com.intuit.karate.http.Response;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.SessionId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class LambdaWebDriverInterop {

    private static final String DEFAULT_LT_API_BASE_URL = "https://api.lambdatest.com/automation/api/v1";
    private static final int SESSION_VIDEO_MAX_ATTEMPTS = 120;
    private static final long SESSION_VIDEO_RETRY_DELAY_MS = 1000L;

    private LambdaWebDriverInterop() {
    }

    public static Object execute(Object driverRef, String command) {
        return execute(driverRef, command, Collections.emptyList());
    }

    public static Object execute(Object driverRef, String command, Object arg) {
        return execute(driverRef, command, Collections.singletonList(arg));
    }

    public static Object execute(Object driverRef, String command, List<?> args) {
        if (driverRef == null) {
            throw new IllegalArgumentException("driver reference is required");
        }
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command is required");
        }
        List<?> safeArgs = args == null ? Collections.emptyList() : args;

        if (driverRef instanceof WebDriver webDriver) {
            Json payload = Json.object().set("script", command).set("args", safeArgs);
            Response response = webDriver.getHttp().path("execute", "sync").post(payload);
            if (response.getStatus() != 200) {
                throw new RuntimeException("webdriver execute failed: status=" + response.getStatus()
                        + ", body=" + response.getBodyAsString());
            }
            if (response.json() == null) {
                return null;
            }
            if (response.json().pathExists("value")) {
                return response.json().get("value");
            }
            return response.json().value();
        }

        if (driverRef instanceof JavascriptExecutor jsExecutor) {
            return jsExecutor.executeScript(command, safeArgs.toArray());
        }

        throw new IllegalArgumentException("unsupported driver reference type: "
                + driverRef.getClass().getName()
                + " (expected com.intuit.karate.driver.WebDriver or org.openqa.selenium.JavascriptExecutor)");
    }

    public static Object cdpExecute(Object driverRef, String cmd, Map<String, Object> params) {
        if (cmd == null || cmd.isBlank()) {
            throw new IllegalArgumentException("cdp cmd is required");
        }
        WebDriver webDriver = requireWebDriver(driverRef);
        Map<String, Object> safeParams = params == null ? Collections.emptyMap() : params;
        Json payload = Json.object().set("cmd", cmd).set("params", safeParams);
        Response response = webDriver.getHttp().path("goog", "cdp", "execute").post(payload);
        if (response.getStatus() != 200) {
            throw new RuntimeException("/goog/cdp/execute failed: status=" + response.getStatus()
                    + ", body=" + response.getBodyAsString());
        }
        return response.json() == null ? null : response.json().get("value");
    }

    public static Object intercept(Object driverRef, Map<String, Object> rule) {
        if (rule == null || rule.isEmpty()) {
            throw new IllegalArgumentException("intercept rule is required");
        }

        String url = readNonBlank(rule.get("url"), "intercept rule url is required");
        String method = trimToNull(rule.get("method"));

        String redirectUrl = trimToNull(rule.get("redirectUrl"));
        if (redirectUrl != null) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("url", url);
            args.put("redirectUrl", redirectUrl);
            if (method != null) {
                args.put("method", method);
            }
            return execute(driverRef, "lt:intercept:redirect", args);
        }

        Object errorObj = rule.get("error");
        String errorCode = trimToNull(rule.get("errorCode"));
        if (errorObj != null || errorCode != null) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("url", url);
            String normalizedError = errorCode != null ? errorCode : String.valueOf(errorObj);
            args.put("error", normalizedError);
            return execute(driverRef, "lt:intercept:error", args);
        }

        Map<String, Object> response = toMap(rule.get("response"));
        if (response == null || response.isEmpty()) {
            throw new IllegalArgumentException("intercept rule requires response / redirectUrl / error");
        }

        Map<String, Object> normalizedResponse = new LinkedHashMap<>();
        Object status = response.get("status");
        if (status == null) {
            status = response.get("responseCode");
        }
        normalizedResponse.put("status", status == null ? 200 : status);

        Map<String, Object> headers = toMap(response.get("headers"));
        if (headers == null) {
            headers = toMap(response.get("responseHeaders"));
        }
        if (headers != null && !headers.isEmpty()) {
            normalizedResponse.put("headers", headers);
        }

        Object body = response.get("body");
        if (body == null) {
            body = response.get("responseBody");
        }
        if (body != null) {
            normalizedResponse.put("body", toJsonString(body));
        }

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("url", url);
        if (method != null) {
            args.put("method", method);
        }
        args.put("response", normalizedResponse);
        return execute(driverRef, "lt:intercept:response", args);
    }

    public static Object lambdaStatus(Object driverRef, String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("lambda status is required");
        }
        return execute(driverRef, "lambda-status", status.trim().toLowerCase(Locale.ROOT));
    }

    public static String lambdaStatusForError(Object errorMessage) {
        return trimToNull(errorMessage) == null ? "passed" : "failed";
    }

    public static String sessionId(Object driverRef) {
        return resolveSessionId(driverRef);
    }

    public static byte[] downloadSessionVideo(Object driverRef, String username, String accessKey) {
        return downloadSessionVideo(resolveSessionId(driverRef), username, accessKey);
    }

    public static byte[] downloadSessionVideo(String sessionId, String username, String accessKey) {
        String sid = trimToNull(sessionId);
        String user = trimToNull(username);
        String key = trimToNull(accessKey);
        if (sid == null || user == null || key == null) {
            return null;
        }

        return downloadSessionVideoInternal(sid, user, key);
    }

    private static byte[] downloadSessionVideoInternal(String sessionId, String username, String accessKey) {
        URI sessionVideoApiUri = sessionVideoApiUri(sessionId);
        URI sessionApiUri = sessionApiUri(sessionId);
        if (sessionVideoApiUri == null || sessionApiUri == null) {
            return null;
        }
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        for (int i = 0; i < SESSION_VIDEO_MAX_ATTEMPTS; i++) {
            Map<String, Object> sessionVideo = readJsonMap(client, sessionVideoApiUri, username, accessKey);
            String videoUrl = extractVideoDownloadUrl(sessionVideo);
            if (videoUrl == null) {
                Map<String, Object> sessionDetails = readJsonMap(client, sessionApiUri, username, accessKey);
                videoUrl = extractVideoDownloadUrl(sessionDetails);
            }
            if (videoUrl != null) {
                URI videoUri = resolveUri(sessionApiUri, videoUrl);
                if (videoUri != null) {
                    byte[] bytes = readVideoBytes(client, videoUri, username, accessKey);
                    if (bytes != null && bytes.length > 0) {
                        return bytes;
                    }
                }
            }
            if (!sleepQuietly(SESSION_VIDEO_RETRY_DELAY_MS)) {
                return null;
            }
        }
        return null;
    }

    public static String uploadFile(Object driverRef, String localFilePath) {
        WebDriver webDriver = requireWebDriver(driverRef);
        Path path = Path.of(localFilePath).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("file does not exist or is not a regular file: " + path);
        }
        String encodedZip = encodeFileAsBase64Zip(path);
        Response response = webDriver.getHttp().path("se", "file").post(Json.object().set("file", encodedZip));
        if (response.getStatus() != 200) {
            throw new RuntimeException("remote file upload failed: status=" + response.getStatus()
                    + ", body=" + response.getBodyAsString());
        }
        String remotePath = response.json() == null ? null : response.json().get("value");
        if (remotePath == null || remotePath.isBlank()) {
            throw new RuntimeException("remote file upload returned empty path");
        }
        return remotePath;
    }

    public static void inputFile(Object driverRef, String locator, String localFilePath) {
        if (locator == null || locator.isBlank()) {
            throw new IllegalArgumentException("locator is required");
        }
        String remotePath = uploadFile(driverRef, localFilePath);
        setElementValue(requireWebDriver(driverRef), locator, remotePath);
    }

    public static void inputLambdaUploadedFile(Object driverRef, String locator, String uploadedFileName) {
        if (locator == null || locator.isBlank()) {
            throw new IllegalArgumentException("locator is required");
        }
        if (uploadedFileName == null || uploadedFileName.isBlank()) {
            throw new IllegalArgumentException("uploadedFileName is required");
        }
        String platformName = resolvePlatformName(driverRef);
        String remotePath = lambdaRemotePath(uploadedFileName, platformName);
        setElementValue(requireWebDriver(driverRef), locator, remotePath);
    }

    public static String lambdaRemotePath(String uploadedFileName, String platformName) {
        String fileName = readNonBlank(uploadedFileName, "uploadedFileName is required");
        String platform = platformName == null ? "" : platformName.toLowerCase(Locale.ROOT);
        if (platform.contains("win")) {
            return "C:\\Users\\ltuser\\Downloads\\" + fileName;
        }
        if (platform.contains("mac") || platform.contains("os x")) {
            return "/Users/ltuser/Downloads/" + fileName;
        }
        return "/home/ltuser/Downloads/" + fileName;
    }

    public static Map<String, Object> withLambdaUserFiles(Map<String, Object> capabilities, List<String> uploadedFileNames) {
        Map<String, Object> merged = copyMap(capabilities);
        if (uploadedFileNames == null || uploadedFileNames.isEmpty()) {
            return merged;
        }

        Map<String, Object> ltOptions = toMap(merged.get("LT:Options"));
        if (ltOptions == null) {
            ltOptions = new LinkedHashMap<>();
        } else {
            ltOptions = new LinkedHashMap<>(ltOptions);
        }

        List<String> files = new ArrayList<>();
        for (String file : uploadedFileNames) {
            if (file != null && !file.isBlank()) {
                files.add(file.trim());
            }
        }

        if (!files.isEmpty()) {
            ltOptions.put("lambda:userFiles", files);
            merged.put("LT:Options", ltOptions);
        }
        return merged;
    }

    private static WebDriver requireWebDriver(Object driverRef) {
        if (driverRef instanceof WebDriver webDriver) {
            return webDriver;
        }
        throw new IllegalArgumentException("expected com.intuit.karate.driver.WebDriver but got: "
                + (driverRef == null ? "null" : driverRef.getClass().getName()));
    }

    private static String resolvePlatformName(Object driverRef) {
        if (driverRef instanceof RemoteWebDriver seleniumDriver) {
            Object platform = seleniumDriver.getCapabilities().getCapability("platformName");
            if (platform != null && !platform.toString().isBlank()) {
                return platform.toString();
            }
        }
        if (driverRef instanceof WebDriver karateDriver) {
            Map<String, Object> options = castStringObjectMap(karateDriver.getOptions().options);
            if (options != null) {
                String platform = trimToNull(options.get("platformName"));
                if (platform != null) {
                    return platform;
                }
                Map<String, Object> capabilities = toMap(options.get("capabilities"));
                if (capabilities != null) {
                    String capPlatform = trimToNull(capabilities.get("platformName"));
                    if (capPlatform != null) {
                        return capPlatform;
                    }
                }
            }
        }
        String system = trimToNull(System.getProperty("karate.platform.name"));
        return system == null ? "" : system;
    }

    private static void setElementValue(WebDriver webDriver, String locator, String value) {
        String elementId = webDriver.elementId(locator);
        Response response = webDriver.getHttp().path("element", elementId, "value")
                .post(Json.object().set("text", value));
        if (response.getStatus() != 200) {
            response = webDriver.getHttp().path("element", elementId, "value")
                    .post(Json.object().set("value", List.of(value)));
        }
        if (response.getStatus() != 200) {
            throw new RuntimeException("remote file input failed: status=" + response.getStatus()
                    + ", body=" + response.getBodyAsString());
        }
    }

    private static String toJsonString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        return Json.of(value).toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castStringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return out;
    }

    private static Map<String, Object> toMap(Object value) {
        return castStringObjectMap(value);
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(source);
    }

    private static String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String readNonBlank(Object value, String messageIfBlank) {
        String text = trimToNull(value);
        if (text == null) {
            throw new IllegalArgumentException(messageIfBlank);
        }
        return text;
    }

    private static URI sessionApiUri(String sessionId) {
        String apiBase = trimToNull(System.getProperty("lt.api.base.url"));
        if (apiBase == null) {
            apiBase = trimToNull(System.getenv("LT_API_BASE_URL"));
        }
        if (apiBase == null) {
            apiBase = DEFAULT_LT_API_BASE_URL;
        }
        String encoded = URLEncoder.encode(sessionId, StandardCharsets.UTF_8);
        return resolveUri(null, apiBase + "/sessions/" + encoded);
    }

    private static URI sessionVideoApiUri(String sessionId) {
        String apiBase = trimToNull(System.getProperty("lt.api.base.url"));
        if (apiBase == null) {
            apiBase = trimToNull(System.getenv("LT_API_BASE_URL"));
        }
        if (apiBase == null) {
            apiBase = DEFAULT_LT_API_BASE_URL;
        }
        String encoded = URLEncoder.encode(sessionId, StandardCharsets.UTF_8);
        return resolveUri(null, apiBase + "/sessions/" + encoded + "/video");
    }

    static String extractVideoUrl(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        return extractVideoUrl(payload, null);
    }

    static String extractVideoDownloadUrl(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        String direct = directVideoUrl(payload);
        if (direct != null) {
            return direct;
        }
        Map<String, Object> data = toMap(payload.get("data"));
        if (data != null) {
            return directVideoUrl(data);
        }
        return null;
    }

    private static String extractVideoUrl(Object value, String keyHint) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey());
                Object child = entry.getValue();
                if (key.toLowerCase(Locale.ROOT).contains("video")) {
                    String candidate = trimToNull(child);
                    if (candidate != null) {
                        URI uri = resolveUri(null, candidate);
                        if (uri != null && uri.isAbsolute()) {
                            return candidate;
                        }
                    }
                }
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? keyHint : String.valueOf(entry.getKey());
                String nested = extractVideoUrl(entry.getValue(), key);
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String nested = extractVideoUrl(item, keyHint);
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        URI uri = resolveUri(null, text);
        if (uri == null || !uri.isAbsolute()) {
            return null;
        }
        if (text.toLowerCase(Locale.ROOT).contains(".mp4")) {
            return text;
        }
        if (keyHint != null && keyHint.toLowerCase(Locale.ROOT).contains("video")) {
            return text;
        }
        return null;
    }

    private static Map<String, Object> readJsonMap(HttpClient client, URI uri, String username, String accessKey) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", basicAuthValue(username, accessKey))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Collections.emptyMap();
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return Collections.emptyMap();
        }
        String body = trimToNull(response.body());
        if (body == null) {
            return Collections.emptyMap();
        }
        Object parsed;
        try {
            parsed = Json.of(body).value();
        } catch (Exception e) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = toMap(parsed);
        return map == null ? Collections.emptyMap() : map;
    }

    private static byte[] readBytes(HttpClient client, URI uri, String username, String accessKey) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", basicAuthValue(username, accessKey))
                .GET()
                .build();
        HttpResponse<byte[]> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception e) {
            return null;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }
        return response.body();
    }

    private static byte[] readVideoBytes(HttpClient client, URI uri, String username, String accessKey) {
        URI requestUri = withoutUserInfo(uri);
        String authorization = videoAuthValue(uri, username, accessKey);
        HttpRequest request = HttpRequest.newBuilder(requestUri)
                .header("Authorization", authorization)
                .header("Accept", "video/mp4,application/octet-stream,*/*")
                .GET()
                .build();
        HttpResponse<byte[]> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception e) {
            return null;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }
        byte[] body = response.body();
        if (body == null || body.length == 0) {
            return null;
        }
        if (looksLikeMp4(body)) {
            return body;
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("video/mp4")) {
            return body;
        }
        return null;
    }

    private static String resolveSessionId(Object driverRef) {
        if (driverRef == null) {
            return null;
        }
        if (driverRef instanceof RemoteWebDriver seleniumDriver) {
            SessionId sessionId = seleniumDriver.getSessionId();
            if (sessionId != null && !sessionId.toString().isBlank()) {
                return sessionId.toString();
            }
        }
        String reflectedMethod = invokeNoArgMethod(driverRef, "getSessionId");
        if (reflectedMethod != null) {
            return reflectedMethod;
        }
        if (driverRef instanceof WebDriver webDriver) {
            String fromOptions = findSessionIdInOptions(webDriver);
            if (fromOptions != null) {
                return fromOptions;
            }
            Object http = webDriver.getHttp();
            String fromHttpField = findSessionIdField(http);
            if (fromHttpField != null) {
                return fromHttpField;
            }
        }
        return findSessionIdField(driverRef);
    }

    private static String findSessionIdInOptions(WebDriver webDriver) {
        Map<String, Object> options = castStringObjectMap(webDriver.getOptions().options);
        if (options == null || options.isEmpty()) {
            return null;
        }
        String exact = trimToNull(options.get("sessionId"));
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, Object> entry : options.entrySet()) {
            String key = entry.getKey();
            if (key != null && key.toLowerCase(Locale.ROOT).contains("session")) {
                String value = trimToNull(entry.getValue());
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String invokeNoArgMethod(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return trimToNull(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String findSessionIdField(Object target) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName();
                if (name == null || !name.toLowerCase(Locale.ROOT).contains("session")) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    String value = trimToNull(field.get(target));
                    if (value != null) {
                        return value;
                    }
                } catch (Exception ignored) {
                    // continue scanning fields
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static URI resolveUri(URI base, String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            URI uri = URI.create(text);
            if (uri.isAbsolute()) {
                return uri;
            }
            if (base != null) {
                return base.resolve(uri);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String basicAuthValue(String username, String accessKey) {
        String userPass = username + ":" + accessKey;
        return "Basic " + Base64.getEncoder().encodeToString(userPass.getBytes(StandardCharsets.UTF_8));
    }

    private static URI withoutUserInfo(URI uri) {
        if (uri == null || uri.getUserInfo() == null) {
            return uri;
        }
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                    uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (Exception e) {
            return uri;
        }
    }

    private static String videoAuthValue(URI uri, String username, String accessKey) {
        String userInfo = uri == null ? null : trimToNull(uri.getUserInfo());
        if (userInfo != null) {
            int delimiter = userInfo.indexOf(':');
            if (delimiter > 0 && delimiter < userInfo.length() - 1) {
                String user = URLDecoder.decode(userInfo.substring(0, delimiter), StandardCharsets.UTF_8);
                String key = URLDecoder.decode(userInfo.substring(delimiter + 1), StandardCharsets.UTF_8);
                if (!user.isBlank() && !key.isBlank()) {
                    return basicAuthValue(user, key);
                }
            }
        }
        return basicAuthValue(username, accessKey);
    }

    private static String directVideoUrl(Map<String, Object> payload) {
        String[] keys = {"url", "video_url", "download_url", "videoUrl"};
        for (String key : keys) {
            String candidate = trimToNull(payload.get(key));
            if (candidate == null) {
                continue;
            }
            URI uri = resolveUri(null, candidate);
            if (uri == null || !uri.isAbsolute()) {
                continue;
            }
            if (candidate.toLowerCase(Locale.ROOT).contains(".mp4")) {
                return candidate;
            }
        }
        return null;
    }

    static boolean looksLikeMp4(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return false;
        }
        int max = Math.min(bytes.length - 3, 32);
        for (int i = 0; i < max; i++) {
            if (bytes[i] == 'f' && bytes[i + 1] == 't' && bytes[i + 2] == 'y' && bytes[i + 3] == 'p') {
                return true;
            }
        }
        return false;
    }

    private static String encodeFileAsBase64Zip(Path path) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(baos);
             InputStream in = Files.newInputStream(path)) {
            zip.putNextEntry(new ZipEntry(path.getFileName().toString()));
            in.transferTo(zip);
            zip.closeEntry();
            zip.finish();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("failed to prepare zip payload for file: " + path, e);
        }
    }
}
