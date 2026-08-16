package io.runtimerocket.fixtures.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/** Idle Boot fixture (no web server) used by Spring adapter tests. */
@SpringBootApplication
public class FixtureApp {

    public static ConfigurableApplicationContext run(String... args) {
        SpringApplication application = new SpringApplication(FixtureApp.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        return application.run(args);
    }

    public static void main(String[] args) {
        run(args);
    }
}
