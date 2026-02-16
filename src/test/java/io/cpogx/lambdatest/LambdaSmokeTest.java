package io.cpogx.lambdatest;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import io.cpogx.lambdatest.spring.LambdaDriverTarget;
import io.cpogx.lambdatest.spring.LambdaDriverTargetRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

class LambdaSmokeTest {

    @Test
    void runSmoke() {
        Map<String, String> props = defaultKarateProperties();
        LambdaDriverTarget target = LambdaDriverTarget.fromKarateProperties(props);
        Assumptions.assumeTrue(target.hasCredentials(),
                "Skipping Lambda smoke test because lt.username / lt.accessKey are not set.");
        runSmokeWithTarget(target, props, readTagExpression());
    }

    void runSmokeWithTarget(LambdaDriverTarget target, Map<String, String> props, String tagExpression) {
        Runner.Builder builder = Runner.path("classpath:features/lambdatest-smoke.feature")
                .reportDir("build/karate-reports/lambdatest-smoke")
                .outputCucumberJson(true);
        if (tagExpression != null && !tagExpression.isBlank()) {
            builder.tags(tagExpression);
        }
        props.forEach(builder::systemProperty);
        LambdaDriverTargetRegistry.set(target);
        try {
            Results results = builder.parallel(1);
            Assertions.assertEquals(0, results.getFailCount(), results.getErrorMessages());
        } finally {
            LambdaDriverTargetRegistry.clear();
        }
    }

    static Map<String, String> defaultKarateProperties() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("karate.driver.type", read("karate.driver.type", "chromedriver"));
        props.put("karate.grid.url", read("karate.grid.url", "https://hub.lambdatest.com/wd/hub"));
        props.put("karate.browser.name", read("karate.browser.name", "Chrome"));
        props.put("karate.browser.version", read("karate.browser.version", "latest"));
        props.put("karate.platform.name", read("karate.platform.name", "win11"));
        props.put("lt.username", read("lt.username", ""));
        props.put("lt.accessKey", read("lt.accessKey", ""));
        props.put("lt.project", read("lt.project", "lambda-karate-lt"));
        props.put("lt.build", read("lt.build", "lambda-karate-lt"));
        props.put("lt.tags", read("lt.tags", "smoke,lambda"));

        String tunnelName = read("lt.tunnel.name", "");
        if (!tunnelName.isBlank()) {
            props.put("lt.tunnel.name", tunnelName);
        }
        String userFiles = read("lt.user.files", "");
        if (!userFiles.isBlank()) {
            props.put("lt.user.files", userFiles);
        }
        return props;
    }

    static String readTagExpression() {
        return read("karate.tags", "");
    }

    static String read(String key, String fallback) {
        String fromSystem = trimToNull(System.getProperty(key));
        if (fromSystem != null) {
            return fromSystem;
        }
        String envKey = key.toUpperCase().replace('.', '_');
        String fromEnv = trimToNull(System.getenv(envKey));
        if (fromEnv != null) {
            return fromEnv;
        }
        if ("lt.username".equals(key)) {
            String legacy = trimToNull(System.getenv("LT_USERNAME"));
            if (legacy != null) {
                return legacy;
            }
        }
        if ("lt.accessKey".equals(key)) {
            String legacy = trimToNull(System.getenv("LT_ACCESS_KEY"));
            if (legacy != null) {
                return legacy;
            }
        }
        return fallback;
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
