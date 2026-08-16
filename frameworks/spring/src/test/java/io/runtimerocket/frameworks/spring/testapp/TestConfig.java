package io.runtimerocket.frameworks.spring.testapp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TestConfig {

    @Bean
    public ExistingBean existingBean() {
        return new ExistingBean();
    }
}
