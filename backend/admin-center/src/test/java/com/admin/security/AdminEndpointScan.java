package com.admin.security;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Every handler of every Admin controller, keyed "METHOD /path/{}" (path variables blanked), with its policy. */
final class AdminEndpointScan {

    /** One endpoint: its policy and its path with the variable names kept. */
    record Endpoint(AdminEndpointPolicy policy, String rawPath) {
    }

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^}]+}");
    private static Map<String, Endpoint> endpoints;

    private AdminEndpointScan() {
    }

    static synchronized Map<String, Endpoint> endpoints() {
        if (endpoints == null) {
            endpoints = Collections.unmodifiableMap(scan());
        }
        return endpoints;
    }

    static AdminEndpointPolicy policy(String call) {
        Endpoint endpoint = endpoints().get(call);
        if (endpoint == null) {
            throw new AssertionError("no Admin endpoint for " + call);
        }
        return endpoint.policy();
    }

    private static Map<String, Endpoint> scan() {
        Map<String, Endpoint> found = new LinkedHashMap<>();
        // Controllers behind a feature switch (LDAP sync) are guarded as well: scan with every switch on.
        MockEnvironment allFeatures = new MockEnvironment()
                .withProperty("ldap.enabled", "true")
                .withProperty("ldap.sync-enabled", "true");
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false, allFeatures);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        for (BeanDefinition definition : scanner.findCandidateComponents("com.admin")) {
            Class<?> type;
            try {
                type = Class.forName(definition.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            String[] basePaths = base == null || base.path().length == 0 ? new String[] {""} : base.path();
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String[] paths = mapping.path().length == 0 ? new String[] {""} : mapping.path();
                RequestMethod[] verbs = mapping.method().length == 0 ? RequestMethod.values() : mapping.method();
                for (String basePath : basePaths) {
                    for (String path : paths) {
                        for (RequestMethod verb : verbs) {
                            String raw = basePath + path;
                            String key = verb.name() + " " + PATH_VARIABLE.matcher(raw).replaceAll("{}");
                            Endpoint previous = found.put(key,
                                    new Endpoint(AdminEndpointPolicy.resolve(method, type, verb.name()), raw));
                            // Two handlers on one key would let one policy hide the other's.
                            if (previous != null) {
                                throw new AssertionError("two Admin handlers map " + key + ": "
                                        + previous.rawPath() + " and " + raw + " (" + type.getSimpleName() + ")");
                            }
                        }
                    }
                }
            }
        }
        return found;
    }
}
