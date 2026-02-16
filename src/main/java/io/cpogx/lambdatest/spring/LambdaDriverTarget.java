package io.cpogx.lambdatest.spring;

import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.driver.Target;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LambdaDriverTarget implements Target {

    private final String driverType;
    private final int timeoutMs;
    private final String gridUrl;
    private final String browserName;
    private final String browserVersion;
    private final String platformName;
    private final String namePrefix;
    private final String username;
    private final String accessKey;
    private final String project;
    private final String build;
    private final String tags;
    private final String tunnelName;
    private final String userFiles;
    private final boolean acceptInsecureCerts;
    private final boolean network;
    private final boolean console;
    private final boolean visual;
    private final boolean webSocketUrl;

    public LambdaDriverTarget(String driverType,
                              int timeoutMs,
                              String gridUrl,
                              String browserName,
                              String browserVersion,
                              String platformName,
                              String namePrefix,
                              String username,
                              String accessKey,
                              String project,
                              String build,
                              String tags,
                              String tunnelName,
                              String userFiles,
                              boolean acceptInsecureCerts,
                              boolean network,
                              boolean console,
                              boolean visual,
                              boolean webSocketUrl) {
        this.driverType = driverType;
        this.timeoutMs = timeoutMs;
        this.gridUrl = gridUrl;
        this.browserName = browserName;
        this.browserVersion = browserVersion;
        this.platformName = platformName;
        this.namePrefix = namePrefix;
        this.username = username;
        this.accessKey = accessKey;
        this.project = project;
        this.build = build;
        this.tags = tags;
        this.tunnelName = tunnelName;
        this.userFiles = userFiles;
        this.acceptInsecureCerts = acceptInsecureCerts;
        this.network = network;
        this.console = console;
        this.visual = visual;
        this.webSocketUrl = webSocketUrl;
    }

    @Override
    public Map<String, Object> start(ScenarioRuntime runtime) {
        Map<String, Object> ltOptions = new LinkedHashMap<>();
        putIfNotBlankObject(ltOptions, "user", username);
        putIfNotBlankObject(ltOptions, "accessKey", accessKey);
        putIfNotBlankObject(ltOptions, "project", project);
        putIfNotBlankObject(ltOptions, "build", build);
        putIfNotBlankObject(ltOptions, "name", scenarioName(runtime));
        List<String> mergedTags = mergedTags(runtime);
        if (!mergedTags.isEmpty()) {
            ltOptions.put("tags", mergedTags);
        }
        if (!isBlank(tunnelName)) {
            ltOptions.put("tunnel", true);
            ltOptions.put("tunnelName", tunnelName.trim());
        }
        if (!csv(userFiles).isEmpty()) {
            ltOptions.put("lambda:userFiles", csv(userFiles));
        }
        ltOptions.put("network", network);
        ltOptions.put("console", console);
        ltOptions.put("visual", visual);

        Map<String, Object> alwaysMatch = new LinkedHashMap<>();
        putIfNotBlankObject(alwaysMatch, "browserName", browserName);
        putIfNotBlankObject(alwaysMatch, "browserVersion", browserVersion);
        putIfNotBlankObject(alwaysMatch, "platformName", platformName);
        if (acceptInsecureCerts) {
            alwaysMatch.put("acceptInsecureCerts", true);
        }
        alwaysMatch.put("webSocketUrl", webSocketUrl);
        alwaysMatch.put("LT:Options", ltOptions);

        Map<String, Object> webDriverSession = new LinkedHashMap<>();
        webDriverSession.put("capabilities", Map.of("alwaysMatch", alwaysMatch));

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("type", driverType);
        config.put("start", false);
        config.put("webDriverUrl", gridUrl);
        config.put("webDriverSession", webDriverSession);
        config.put("timeout", timeoutMs);
        return config;
    }

    @Override
    public Map<String, Object> stop(ScenarioRuntime runtime) {
        return Collections.emptyMap();
    }

    public static LambdaDriverTarget fromKarateProperties(Map<String, ?> properties) {
        Map<String, ?> props = properties == null ? Map.of() : properties;
        String driverType = pick(props, "karate.driver.type", "chromedriver");
        int timeoutMs = parseInt(pick(props, "cpogx.execution.timeout-ms", "30000"), 30000);
        String gridUrl = pick(props, "karate.grid.url", "https://hub.lambdatest.com/wd/hub");
        String browserName = pick(props, "karate.browser.name", "Chrome");
        String browserVersion = pick(props, "karate.browser.version", "latest");
        String platformName = pick(props, "karate.platform.name", "win11");
        String namePrefix = pick(props, "lt.name.prefix", "");
        String username = pick(props, "lt.username", "");
        String accessKey = pick(props, "lt.accessKey", "");
        String project = pick(props, "lt.project", "lambda-karate-lt");
        String build = pick(props, "lt.build", "lambda-karate-lt");
        String tags = pick(props, "lt.tags", "smoke,lambda");
        String tunnelName = pick(props, "lt.tunnel.name", "");
        String userFiles = pick(props, "lt.user.files", "");
        boolean acceptInsecureCerts = parseBoolean(pick(props, "lt.acceptInsecureCerts", "false"), false);
        boolean network = parseBoolean(pick(props, "lt.network", "true"), true);
        boolean console = parseBoolean(pick(props, "lt.console", "true"), true);
        boolean visual = parseBoolean(pick(props, "lt.visual", "true"), true);
        boolean webSocketUrl = parseBoolean(pick(props, "lt.webSocketUrl", "true"), true);
        return new LambdaDriverTarget(
                driverType,
                timeoutMs,
                gridUrl,
                browserName,
                browserVersion,
                platformName,
                namePrefix,
                username,
                accessKey,
                project,
                build,
                tags,
                tunnelName,
                userFiles,
                acceptInsecureCerts,
                network,
                console,
                visual,
                webSocketUrl
        );
    }

    public boolean hasCredentials() {
        return !isBlank(username) && !isBlank(accessKey);
    }

    public Map<String, String> toKarateProperties() {
        Map<String, String> props = new LinkedHashMap<>();
        putIfNotBlank(props, "karate.driver.type", driverType);
        props.put("cpogx.execution.timeout-ms", String.valueOf(timeoutMs));
        putIfNotBlank(props, "karate.grid.url", gridUrl);
        putIfNotBlank(props, "karate.browser.name", browserName);
        putIfNotBlank(props, "karate.browser.version", browserVersion);
        putIfNotBlank(props, "karate.platform.name", platformName);
        putIfNotBlank(props, "lt.name.prefix", namePrefix);
        putIfNotBlank(props, "lt.username", username);
        putIfNotBlank(props, "lt.accessKey", accessKey);
        putIfNotBlank(props, "lt.project", project);
        putIfNotBlank(props, "lt.build", build);
        putIfNotBlank(props, "lt.tags", tags);
        putIfNotBlank(props, "lt.tunnel.name", tunnelName);
        putIfNotBlank(props, "lt.user.files", userFiles);
        props.put("lt.acceptInsecureCerts", String.valueOf(acceptInsecureCerts));
        props.put("lt.network", String.valueOf(network));
        props.put("lt.console", String.valueOf(console));
        props.put("lt.visual", String.valueOf(visual));
        props.put("lt.webSocketUrl", String.valueOf(webSocketUrl));
        return props;
    }

    private String scenarioName(ScenarioRuntime runtime) {
        String scenario = runtime != null && runtime.scenario != null ? trimToNull(runtime.scenario.getName()) : null;
        String base = scenario == null ? "lambda-karate-lt-scenario" : scenario;
        if (isBlank(namePrefix)) {
            return base;
        }
        return namePrefix.trim() + " - " + base;
    }

    private List<String> mergedTags(ScenarioRuntime runtime) {
        Set<String> all = new LinkedHashSet<>(csv(tags));
        if (runtime != null && runtime.tags != null) {
            for (String tag : runtime.tags.getTags()) {
                String normalized = trimToNull(tag);
                if (normalized != null) {
                    if (normalized.startsWith("@")) {
                        normalized = normalized.substring(1);
                    }
                    if (!normalized.isBlank()) {
                        all.add(normalized);
                    }
                }
            }
        }
        return new ArrayList<>(all);
    }

    private static List<String> csv(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return List.of();
        }
        String[] parts = text.split(",");
        List<String> out = new ArrayList<>();
        for (String raw : parts) {
            String item = trimToNull(raw);
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        if (isBlank(raw)) {
            return fallback;
        }
        String normalized = raw.trim().toLowerCase();
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        return fallback;
    }

    private static String pick(Map<String, ?> map, String key, String fallback) {
        Object value = map.get(key);
        String text = trimToNull(value == null ? null : String.valueOf(value));
        return text == null ? fallback : text;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static void putIfNotBlank(Map<String, String> map, String key, String value) {
        if (!isBlank(value)) {
            map.put(key, value.trim());
        }
    }

    private static void putIfNotBlankObject(Map<String, Object> map, String key, String value) {
        if (!isBlank(value)) {
            map.put(key, value.trim());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
