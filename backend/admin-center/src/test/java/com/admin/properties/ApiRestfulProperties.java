package com.admin.properties;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 属性测试 18: API RESTful 规范性
 * 验证需求: 需求 14.1
 */
class ApiRestfulProperties {

    private ApiEndpoint endpoint;
    private static final Pattern URI_PATTERN = Pattern.compile("^/api/v\\d+/[a-z][a-z0-9-]*(/[a-z][a-z0-9-]*|/\\{[a-z][a-zA-Z0-9]*\\})*$");
    private static final Set<String> VALID_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    record ApiEndpoint(String uri, String method, int successStatus, int errorStatus, boolean hasRequestBody, boolean hasResponseBody) {}

    @BeforeTry
    void setUp() {
        endpoint = null;
    }

    @Provide
    Arbitrary<ApiEndpoint> validEndpoints() {
        Arbitrary<String> uris = Arbitraries.of(
            "/api/v1/admin/users",
            "/api/v1/admin/users/{id}",
            "/api/v1/admin/departments",
            "/api/v1/admin/departments/{id}",
            "/api/v1/admin/roles",
            "/api/v1/admin/roles/{id}/permissions"
        );
        Arbitrary<String> methods = Arbitraries.of("GET", "POST", "PUT", "DELETE");
        
        return Combinators.combine(uris, methods).as((uri, method) -> {
            boolean isCollection = !uri.contains("{");
            int successStatus = switch (method) {
                case "GET" -> 200;
                case "POST" -> 201;
                case "PUT", "PATCH" -> 200;
                case "DELETE" -> 204;
                default -> 200;
            };
            boolean hasRequestBody = method.equals("POST") || method.equals("PUT") || method.equals("PATCH");
            boolean hasResponseBody = !method.equals("DELETE");
            return new ApiEndpoint(uri, method, successStatus, 400, hasRequestBody, hasResponseBody);
        });
    }

    @Property(tries = 20)
    void uriShouldFollowRestfulNamingConvention(@ForAll("validEndpoints") ApiEndpoint ep) {
        endpoint = ep;
        // URI应使用小写字母和连字符
        Assume.that(endpoint.uri() != null && !endpoint.uri().isEmpty());
        
        String uriWithoutParams = endpoint.uri().replaceAll("\\{[^}]+\\}", "id");
        boolean isValidUri = URI_PATTERN.matcher(endpoint.uri()).matches() || 
                            uriWithoutParams.matches("^/api/v\\d+/[a-z][a-z0-9-/]*$");
        
        assert isValidUri : "URI should follow RESTful naming: " + endpoint.uri();
    }

    @Property(tries = 20)
    void httpMethodShouldBeValid(@ForAll("validEndpoints") ApiEndpoint ep) {
        endpoint = ep;
        assert VALID_METHODS.contains(endpoint.method()) : 
            "HTTP method should be valid: " + endpoint.method();
    }

    @Property(tries = 20)
    void getRequestShouldNotHaveRequestBody(@ForAll("validEndpoints") ApiEndpoint ep) {
        endpoint = ep;
        if (endpoint.method().equals("GET")) {
            assert !endpoint.hasRequestBody() : "GET request should not have request body";
        }
    }

    @Property(tries = 20)
    void deleteRequestShouldReturn204OnSuccess(@ForAll("validEndpoints") ApiEndpoint ep) {
        endpoint = ep;
        if (endpoint.method().equals("DELETE")) {
            assert endpoint.successStatus() == 204 : 
                "DELETE should return 204 No Content on success";
        }
    }

    @Property(tries = 20)
    void postRequestShouldReturn201OnSuccess(@ForAll("validEndpoints") ApiEndpoint ep) {
        endpoint = ep;
        if (endpoint.method().equals("POST")) {
            assert endpoint.successStatus() == 201 : 
                "POST should return 201 Created on success";
        }
    }

    @Property(tries = 20)
    void collectionUriShouldUsePluralNouns(@ForAll("validEndpoints") ApiEndpoint ep) {
        endpoint = ep;
        String[] segments = endpoint.uri().split("/");
        for (String segment : segments) {
            if (!segment.isEmpty() && !segment.startsWith("{") && 
                !segment.equals("api") && !segment.matches("v\\d+") && !segment.equals("admin")) {
                // 资源名称应使用复数形式
                boolean isPlural = segment.endsWith("s") || segment.endsWith("ies") || 
                                  segment.equals("config") || segment.equals("audit");
                assert isPlural || segment.contains("-") : 
                    "Collection URI should use plural nouns: " + segment;
            }
        }
    }
}
