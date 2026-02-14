package io.cpogx.lambdatest.spring;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LambdaDriverTarget {

    private final String driverType;
    private final String gridUrl;
    private final String browserName;
    private final String browserVersion;
    private final String platformName;
    private final String username;
    private final String accessKey;
    private final String project;
    private final String build;
    private final String tags;
    private final String tunnelName;
    private final String userFiles;

    public LambdaDriverTarget(String driverType,
                              String gridUrl,
                              String browserName,
                              String browserVersion,
                              String platformName,
                              String username,
                              String accessKey,
                              String project,
                              String build,
                              String tags,
                              String tunnelName,
                              String userFiles) {
        this.driverType = driverType;
        this.gridUrl = gridUrl;
        this.browserName = browserName;
        this.browserVersion = browserVersion;
        this.platformName = platformName;
        this.username = username;
        this.accessKey = accessKey;
        this.project = project;
        this.build = build;
        this.tags = tags;
        this.tunnelName = tunnelName;
        this.userFiles = userFiles;
    }

    public String driverType() {
        return driverType;
    }

    public String gridUrl() {
        return gridUrl;
    }

    public String browserName() {
        return browserName;
    }

    public String browserVersion() {
        return browserVersion;
    }

    public String platformName() {
        return platformName;
    }

    public String username() {
        return username;
    }

    public String accessKey() {
        return accessKey;
    }

    public String project() {
        return project;
    }

    public String build() {
        return build;
    }

    public String tags() {
        return tags;
    }

    public String tunnelName() {
        return tunnelName;
    }

    public String userFiles() {
        return userFiles;
    }

    public boolean hasCredentials() {
        return !isBlank(username) && !isBlank(accessKey);
    }

    public Map<String, String> toKarateProperties() {
        Map<String, String> props = new LinkedHashMap<>();
        putIfNotBlank(props, "karate.driver.type", driverType);
        putIfNotBlank(props, "karate.grid.url", gridUrl);
        putIfNotBlank(props, "karate.browser.name", browserName);
        putIfNotBlank(props, "karate.browser.version", browserVersion);
        putIfNotBlank(props, "karate.platform.name", platformName);
        putIfNotBlank(props, "lt.username", username);
        putIfNotBlank(props, "lt.accessKey", accessKey);
        putIfNotBlank(props, "lt.project", project);
        putIfNotBlank(props, "lt.build", build);
        putIfNotBlank(props, "lt.tags", tags);
        putIfNotBlank(props, "lt.tunnel.name", tunnelName);
        putIfNotBlank(props, "lt.user.files", userFiles);
        return props;
    }

    private static void putIfNotBlank(Map<String, String> map, String key, String value) {
        if (!isBlank(value)) {
            map.put(key, value.trim());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
