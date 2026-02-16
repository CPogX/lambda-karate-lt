package io.cpogx.lambdatest.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cpogx")
public class LambdaTargetProperties {

    private final Execution execution = new Execution();
    private final Browser browser = new Browser();
    private final LambdaTest lambdatest = new LambdaTest();

    public Execution getExecution() {
        return execution;
    }

    public Browser getBrowser() {
        return browser;
    }

    public LambdaTest getLambdatest() {
        return lambdatest;
    }

    public LambdaDriverTarget toDriverTarget() {
        String tunnelName = lambdatest.tunnel.enabled && !isBlank(lambdatest.tunnel.name)
                ? lambdatest.tunnel.name
                : (!isBlank(lambdatest.tunnel.name) ? lambdatest.tunnel.name : "");
        return new LambdaDriverTarget(
                execution.driverType,
                execution.timeoutMs,
                lambdatest.gridUrl,
                browser.name,
                browser.version,
                browser.platformName,
                lambdatest.namePrefix,
                lambdatest.username,
                lambdatest.accessKey,
                lambdatest.project,
                lambdatest.build,
                lambdatest.tags,
                tunnelName,
                lambdatest.userFiles,
                lambdatest.acceptInsecureCerts,
                lambdatest.network,
                lambdatest.console,
                lambdatest.visual,
                lambdatest.webSocketUrl
        );
    }

    public static class Execution {
        private String driverType = "chromedriver";
        private int timeoutMs = 30000;

        public String getDriverType() {
            return driverType;
        }

        public void setDriverType(String driverType) {
            this.driverType = driverType;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class Browser {
        private String name = "Chrome";
        private String version = "latest";
        private String platformName = "win11";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getPlatformName() {
            return platformName;
        }

        public void setPlatformName(String platformName) {
            this.platformName = platformName;
        }
    }

    public static class LambdaTest {
        private String gridUrl = "https://hub.lambdatest.com/wd/hub";
        private String username = "";
        private String accessKey = "";
        private String project = "lambda-karate-lt";
        private String build = "lambda-karate-lt";
        private String tags = "smoke,lambda";
        private String namePrefix = "";
        private String userFiles = "";
        private boolean acceptInsecureCerts = false;
        private boolean network = true;
        private boolean console = true;
        private boolean visual = true;
        private boolean webSocketUrl = true;
        private final Tunnel tunnel = new Tunnel();

        public String getGridUrl() {
            return gridUrl;
        }

        public void setGridUrl(String gridUrl) {
            this.gridUrl = gridUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getProject() {
            return project;
        }

        public void setProject(String project) {
            this.project = project;
        }

        public String getBuild() {
            return build;
        }

        public void setBuild(String build) {
            this.build = build;
        }

        public String getTags() {
            return tags;
        }

        public void setTags(String tags) {
            this.tags = tags;
        }

        public String getNamePrefix() {
            return namePrefix;
        }

        public void setNamePrefix(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        public String getUserFiles() {
            return userFiles;
        }

        public void setUserFiles(String userFiles) {
            this.userFiles = userFiles;
        }

        public boolean isAcceptInsecureCerts() {
            return acceptInsecureCerts;
        }

        public void setAcceptInsecureCerts(boolean acceptInsecureCerts) {
            this.acceptInsecureCerts = acceptInsecureCerts;
        }

        public boolean isNetwork() {
            return network;
        }

        public void setNetwork(boolean network) {
            this.network = network;
        }

        public boolean isConsole() {
            return console;
        }

        public void setConsole(boolean console) {
            this.console = console;
        }

        public boolean isVisual() {
            return visual;
        }

        public void setVisual(boolean visual) {
            this.visual = visual;
        }

        public boolean isWebSocketUrl() {
            return webSocketUrl;
        }

        public void setWebSocketUrl(boolean webSocketUrl) {
            this.webSocketUrl = webSocketUrl;
        }

        public Tunnel getTunnel() {
            return tunnel;
        }
    }

    public static class Tunnel {
        private boolean enabled = false;
        private String name = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
