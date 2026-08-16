package io.runtimerocket.frameworks.spring.testapp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Same configuration type shape as {@link TestConfig} plus one extra {@code @Bean} method. */
@Configuration(proxyBeanMethods = false)
public class ConfigWithExtraBean {

    @Bean
    public ExistingBean existingBean() {
        return new ExistingBean();
    }

    @Bean
    public AddedBean addedBean() {
        return new AddedBean();
    }
}
