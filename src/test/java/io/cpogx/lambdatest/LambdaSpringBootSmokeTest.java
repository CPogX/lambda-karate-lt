package io.cpogx.lambdatest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.util.LinkedHashMap;
import java.util.Map;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LambdaSpringBootSmokeTest {

    @Autowired
    private Environment environment;

    @Test
    void runSmokeUsingSpringProperties() {
        Map<String, String> props = new LinkedHashMap<>(LambdaSmokeTest.defaultKarateProperties());
        putIfPresent(props, "karate.grid.url", environment.getProperty("cpogx.lambdatest.grid-url"));
        putIfPresent(props, "lt.username", environment.getProperty("cpogx.lambdatest.username"));
        putIfPresent(props, "lt.accessKey", environment.getProperty("cpogx.lambdatest.access-key"));
        putIfPresent(props, "lt.project", environment.getProperty("cpogx.lambdatest.project"));
        putIfPresent(props, "lt.build", environment.getProperty("cpogx.lambdatest.build"));
        putIfPresent(props, "lt.tags", environment.getProperty("cpogx.lambdatest.tags"));
        putIfPresent(props, "lt.tunnel.name", environment.getProperty("cpogx.lambdatest.tunnel.name"));
        putIfPresent(props, "karate.browser.name", environment.getProperty("cpogx.browser.name"));
        putIfPresent(props, "karate.browser.version", environment.getProperty("cpogx.browser.version"));
        putIfPresent(props, "karate.platform.name", environment.getProperty("cpogx.browser.platform-name"));
        Assumptions.assumeTrue(!props.getOrDefault("lt.username", "").isBlank()
                        && !props.getOrDefault("lt.accessKey", "").isBlank(),
                "Skipping Lambda Spring smoke test because credentials are not configured.");

        String tags = environment.getProperty("karate.tags", LambdaSmokeTest.readTagExpression());
        if (tags != null) {
            System.setProperty("karate.tags", tags);
        }
        props.forEach(System::setProperty);

        new LambdaSmokeTest().runSmoke();
    }

    private static void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }
}
