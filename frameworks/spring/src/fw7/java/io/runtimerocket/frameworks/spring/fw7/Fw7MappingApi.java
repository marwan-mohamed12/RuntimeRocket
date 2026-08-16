package io.runtimerocket.frameworks.spring.fw7;

import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

/**
 * Compile-time probe of Framework 7 {@code RequestMappingHandlerMapping} names. Loaded only by
 * {@code compileFw7Java}; the adapter talks to mappings by reflection.
 */
public final class Fw7MappingApi {

    public static final String SERVLET_MAPPING =
            "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping";
    public static final String WEBFLUX_MAPPING =
            "org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping";

    private Fw7MappingApi() {}

    public static String detectHandlerMethodsName() {
        return "detectHandlerMethods";
    }

    static final class Probe extends RequestMappingHandlerMapping {
        @Override
        protected void detectHandlerMethods(Object handler) {
            super.detectHandlerMethods(handler);
        }

        void touchPublicApi() {
            getHandlerMethods();
            unregisterMapping(null);
        }

        void touchRegister(RequestMappingInfo mapping, Object handler, Method method) {
            registerMapping(mapping, handler, method);
        }
    }
}
