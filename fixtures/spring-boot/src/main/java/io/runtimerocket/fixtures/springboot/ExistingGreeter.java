package io.runtimerocket.fixtures.springboot;

import org.springframework.stereotype.Service;

@Service
public class ExistingGreeter {

    public String greet() {
        return "hello";
    }
}
