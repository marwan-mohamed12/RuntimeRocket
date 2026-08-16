package io.runtimerocket.frameworks.spring.testapp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChangedController {

    @GetMapping("/hello")
    public String hello() {
        return "hello";
    }

    @GetMapping("/changed")
    public String changed() {
        return "changed";
    }
}
