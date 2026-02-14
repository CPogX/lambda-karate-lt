package io.cpogx.lambdatest.spring;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LambdaTargetProperties.class)
public class LambdaTargetConfiguration {

    @Bean
    public LambdaDriverTarget lambdaDriverTarget(LambdaTargetProperties properties) {
        return properties.toDriverTarget();
    }
}
