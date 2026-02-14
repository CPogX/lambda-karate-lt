package io.cpogx.lambdatest;

import io.cpogx.lambdatest.spring.LambdaDriverTarget;
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
    private LambdaDriverTarget driverTarget;

    @Autowired
    private Environment environment;

    @Test
    void runSmokeUsingSpringProperties() {
        Map<String, String> props = new LinkedHashMap<>(driverTarget.toKarateProperties());
        Assumptions.assumeTrue(driverTarget.hasCredentials(),
                "Skipping Lambda Spring smoke test because credentials are not configured.");

        String tags = environment.getProperty("karate.tags", LambdaSmokeTest.readTagExpression());
        if (tags != null) {
            System.setProperty("karate.tags", tags);
        }
        props.forEach(System::setProperty);

        new LambdaSmokeTest().runSmoke();
    }
}
