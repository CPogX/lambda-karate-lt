package io.cpogx.lambdatest.interop;

import com.intuit.karate.Json;
import com.intuit.karate.driver.WebDriver;
import com.intuit.karate.http.Response;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
