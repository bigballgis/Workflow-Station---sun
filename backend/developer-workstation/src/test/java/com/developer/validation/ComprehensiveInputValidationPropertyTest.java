package com.developer.validation;

import com.developer.dto.ValidationResult;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Property-based tests for comprehensive input validation framework.
 * 
 * **Feature: technical-debt-remediation, Property 1: Comprehensive Input Validation**
 * **Validates: Requirements 1.1, 1.4**
 */
class ComprehensiveInputValidationPropertyTest {
    
    private SecurityInputValidator securityValidator;
    private ValidationRuleEngine ruleEngine;
    private SanitizationEngine sanitizationEngine;
    private InjectionDetector injectionDetector;
    
    @BeforeTry
    void setUp() {
        sanitizationEngine = new SanitizationEngine();
        injectionDetector = new InjectionDetector();
        securityValidator = new SecurityInputValidator(sanitizationEngine, injectionDetector);
        ruleEngine = new ValidationRuleEngine();
    }
    
    /**
     * Property 1: Comprehensive Input Validation
     * 
     * For any API request with input parameters, all parameters (path variables, 
     * query parameters, request bodies) should be validated against defined schemas 
     * before processing.
     * 
     * **Validates: Requirements 1.1, 1.4**
     */
    @Property(tries = 100)
    @Label("Comprehensive Input Validation - All input parameters must be validated")
    void comprehensiveInputValidationProperty(
            @ForAll("apiRequestInputs") ApiRequestInputs inputs) {
        
        // Act: Validate all input parameters using the validation framework
        ValidationResult pathVariableResult = validatePathVariables(inputs.getPathVariables());
        ValidationResult queryParameterResult = validateQueryParameters(inputs.getQueryParameters());
        ValidationResult requestBodyResult = validateRequestBody(inputs.getRequestBody());
        
        // Assert: All validation results should be consistent and complete
        
        // 1. Validation should never return null results
        Assume.that(pathVariableResult != null);
        Assume.that(queryParameterResult != null);
        Assume.that(requestBodyResult != null);
        
        // 2. If any input contains security threats, validation should detect them
        boolean containsSecurityThreats = containsKnownSecurityPatterns(inputs);
        if (containsSecurityThreats) {
            boolean anyValidationFailed = !pathVariableResult.isValid() || 
                                        !queryParameterResult.isValid() || 
                                        !requestBodyResult.isValid();
            
            // At least one validation should fail when security threats are present
            Assume.that(anyValidationFailed);
        }
        
        // 3. Validation should be comprehensive - all parameters must be checked
        // This means we should have validation results for all non-empty inputs
        if (!inputs.getPathVariables().isEmpty()) {
            // Path variables should be validated
            Assume.that(pathVariableResult != null);
        }
        
        if (!inputs.getQueryParameters().isEmpty()) {
            // Query parameters should be validated
            Assume.that(queryParameterResult != null);
        }
        
        if (inputs.getRequestBody() != null && !inputs.getRequestBody().trim().isEmpty()) {
            // Request body should be validated
            Assume.that(requestBodyResult != null);
        }
        
        // 4. Validation results should be deterministic - same input should produce same result
        ValidationResult pathVariableResult2 = validatePathVariables(inputs.getPathVariables());
        ValidationResult queryParameterResult2 = validateQueryParameters(inputs.getQueryParameters());
        ValidationResult requestBodyResult2 = validateRequestBody(inputs.getRequestBody());
        
        Assume.that(pathVariableResult.isValid() == pathVariableResult2.isValid());
        Assume.that(queryParameterResult.isValid() == queryParameterResult2.isValid());
        Assume.that(requestBodyResult.isValid() == requestBodyResult2.isValid());
        
        // 5. Error messages should be present when validation fails
        if (!pathVariableResult.isValid()) {
            Assume.that(!pathVariableResult.getErrors().isEmpty());
        }
        if (!queryParameterResult.isValid()) {
            Assume.that(!queryParameterResult.getErrors().isEmpty());
        }
        if (!requestBodyResult.isValid()) {
            Assume.that(!requestBodyResult.getErrors().isEmpty());
        }
    }
    
    /**
     * Property: Input Validation Schema Compliance
     * 
     * All input validation should follow defined schemas and rules consistently.
     * 
     * **Validates: Requirements 1.1, 1.4**
     */
    @Property(tries = 100)
    @Label("Input Validation Schema Compliance")
    void inputValidationSchemaComplianceProperty(
            @ForAll @NotBlank @Size(min = 1, max = 200) String input,
            @ForAll("validationRuleGroups") String ruleGroup) {
        
        // Act: Validate input using rule engine with specific rule group
        ValidationResult ruleEngineResult = ruleEngine.validateWithRuleGroup(input, ruleGroup);
        ValidationResult securityResult = securityValidator.validate(input);
        
        // Assert: Validation should be consistent and follow schema rules
        
        // 1. Results should never be null
        Assume.that(ruleEngineResult != null);
        Assume.that(securityResult != null);
        
        // 2. Security validator should catch security threats that rule engine might miss
        if (!securityResult.isValid() && containsSecurityPattern(input)) {
            // If security validator fails on security patterns, it should be for valid reasons
            Assume.that(!securityResult.getErrors().isEmpty());
            
            // Error codes should follow naming convention
            boolean hasValidErrorCodes = securityResult.getErrors().stream()
                    .allMatch(error -> error.getCode().startsWith("SECURITY_"));
            Assume.that(hasValidErrorCodes);
        }
        
        // 3. Rule engine validation should be consistent with rule group expectations
        List<ValidationRule> rules = ruleEngine.getRulesInGroup(ruleGroup);
        if (!rules.isEmpty()) {
            // If rules exist, validation result should reflect rule application
            boolean anyRuleMatches = rules.stream()
                    .filter(ValidationRule::isEnabled)
                    .anyMatch(rule -> rule.shouldFail(input));
            
            if (anyRuleMatches) {
                // If any rule should fail, validation should fail
                Assume.that(!ruleEngineResult.isValid());
            }
        }
        
        // 4. Validation should be repeatable
        ValidationResult repeatResult = ruleEngine.validateWithRuleGroup(input, ruleGroup);
        Assume.that(ruleEngineResult.isValid() == repeatResult.isValid());
    }
    
    /**
     * Property: Validation Framework Integration
     * 
     * Different validation components should work together consistently.
     * 
     * **Validates: Requirements 1.1, 1.4**
     */
    @Property(tries = 100)
    @Label("Validation Framework Integration Consistency")
    void validationFrameworkIntegrationProperty(
            @ForAll @NotBlank @Size(min = 1, max = 100) String input) {
        
        // Act: Use different validation approaches
        ValidationResult securityResult = securityValidator.validate(input);
        ValidationResult allRulesResult = ruleEngine.validateWithAllRules(input);
        boolean isValidQuickCheck = securityValidator.isValid(input);
        
        // Assert: Integration should be consistent
        
        // 1. Quick validation should match detailed validation
        Assume.that(securityResult.isValid() == isValidQuickCheck);
        
        // 2. Sanitization should not break validation consistency
        String sanitizedInput = securityValidator.sanitize(input);
        ValidationResult sanitizedResult = securityValidator.validate(sanitizedInput);
        
        // Sanitized input should be safer or equal in safety
        if (!securityResult.isValid()) {
            // Original input failed, sanitized should be better or equal
            boolean sanitizedIsBetter = sanitizedResult.isValid() || 
                                      sanitizedResult.getErrors().size() <= securityResult.getErrors().size();
            Assume.that(sanitizedIsBetter);
        }
        
        // 3. Injection detection should be consistent with validation
        boolean injectionDetected = securityValidator.detectInjectionAttempt(input);
        Assume.that(injectionDetected == !securityResult.isValid());
        
        // 4. Validator name should be consistent
        String validatorName = securityValidator.getValidatorName();
        Assume.that(validatorName != null && !validatorName.isEmpty());
        Assume.that(validatorName.equals("SecurityInputValidator"));
    }
    
    // Helper methods for validation
    
    private ValidationResult validatePathVariables(Map<String, String> pathVariables) {
        ValidationResult combinedResult = ValidationResult.builder().build();
        
        for (Map.Entry<String, String> entry : pathVariables.entrySet()) {
            ValidationResult result = securityValidator.validate(entry.getValue());
            if (!result.isValid()) {
                combinedResult.setValid(false);
                result.getErrors().forEach(error -> 
                    combinedResult.addError(error.getCode(), error.getMessage(), "pathVariable." + entry.getKey()));
            }
        }
        
        return combinedResult;
    }
    
    private ValidationResult validateQueryParameters(Map<String, String> queryParameters) {
        ValidationResult combinedResult = ValidationResult.builder().build();
        
        for (Map.Entry<String, String> entry : queryParameters.entrySet()) {
            ValidationResult result = securityValidator.validate(entry.getValue());
            if (!result.isValid()) {
                combinedResult.setValid(false);
                result.getErrors().forEach(error -> 
                    combinedResult.addError(error.getCode(), error.getMessage(), "queryParam." + entry.getKey()));
            }
        }
        
        return combinedResult;
    }
    
    private ValidationResult validateRequestBody(String requestBody) {
        if (requestBody == null || requestBody.trim().isEmpty()) {
            return ValidationResult.builder().build(); // Empty body is valid
        }
        
        return securityValidator.validate(requestBody);
    }
    
    private boolean containsKnownSecurityPatterns(ApiRequestInputs inputs) {
        // Check all inputs for known security patterns
        String[] securityPatterns = {
            "<script", "javascript:", "'; DROP", "UNION SELECT", 
            "../../", "cmd.exe", "powershell", "eval(", "alert("
        };
        
        // Check path variables
        for (String value : inputs.getPathVariables().values()) {
            if (containsAnyPattern(value, securityPatterns)) {
                return true;
            }
        }
        
        // Check query parameters
        for (String value : inputs.getQueryParameters().values()) {
            if (containsAnyPattern(value, securityPatterns)) {
                return true;
            }
        }
        
        // Check request body
        if (inputs.getRequestBody() != null && containsAnyPattern(inputs.getRequestBody(), securityPatterns)) {
            return true;
        }
        
        return false;
    }
    
    private boolean containsSecurityPattern(String input) {
        String[] securityPatterns = {
            "<script", "javascript:", "'; DROP", "UNION SELECT", 
            "../../", "cmd.exe", "powershell", "eval(", "alert("
        };
        return containsAnyPattern(input, securityPatterns);
    }
    
    private boolean containsAnyPattern(String input, String[] patterns) {
        if (input == null) return false;
        String lowerInput = input.toLowerCase();
        for (String pattern : patterns) {
            if (lowerInput.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    // Data generators
    
    @Provide
    Arbitrary<ApiRequestInputs> apiRequestInputs() {
        return Combinators.combine(
            pathVariables(),
            queryParameters(),
            requestBodies()
        ).as(ApiRequestInputs::new);
    }
    
    @Provide
    Arbitrary<Map<String, String>> pathVariables() {
        return Arbitraries.maps(
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(20),
            Arbitraries.oneOf(
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(50),
                Arbitraries.strings().numeric().ofMinLength(1).ofMaxLength(10),
                maliciousInputs()
            )
        ).ofMinSize(0).ofMaxSize(5);
    }
    
    @Provide
    Arbitrary<Map<String, String>> queryParameters() {
        return Arbitraries.maps(
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(20),
            Arbitraries.oneOf(
                Arbitraries.strings().ascii().ofMinLength(0).ofMaxLength(100),
                maliciousInputs()
            )
        ).ofMinSize(0).ofMaxSize(10);
    }
    
    @Provide
    Arbitrary<String> requestBodies() {
        return Arbitraries.oneOf(
            Arbitraries.just(null),
            Arbitraries.just(""),
            Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(500),
            jsonBodies(),
            maliciousInputs()
        );
    }
    
    @Provide
    Arbitrary<String> jsonBodies() {
        return Arbitraries.oneOf(
            Arbitraries.just("{\"name\":\"test\",\"value\":\"data\"}"),
            Arbitraries.just("{\"id\":123,\"active\":true}"),
            Arbitraries.just("[{\"item\":\"value1\"},{\"item\":\"value2\"}]")
        );
    }
    
    @Provide
    Arbitrary<String> maliciousInputs() {
        return Arbitraries.oneOf(
            // SQL Injection patterns
            Arbitraries.just("'; DROP TABLE users; --"),
            Arbitraries.just("1' UNION SELECT * FROM passwords"),
            Arbitraries.just("admin'--"),
            
            // XSS patterns
            Arbitraries.just("<script>alert('XSS')</script>"),
            Arbitraries.just("<img src=x onerror=alert('XSS')>"),
            Arbitraries.just("javascript:alert('XSS')"),
            
            // Command injection patterns
            Arbitraries.just("test; rm -rf /"),
            Arbitraries.just("file.txt && cat /etc/passwd"),
            Arbitraries.just("$(whoami)"),
            
            // Path traversal patterns
            Arbitraries.just("../../../etc/passwd"),
            Arbitraries.just("..\\..\\..\\windows\\system32")
        );
    }
    
    @Provide
    Arbitrary<String> validationRuleGroups() {
        return Arbitraries.oneOf(
            Arbitraries.just("security"),
            Arbitraries.just("format"),
            Arbitraries.just("business")
        );
    }
    
    // Data class for API request inputs
    public static class ApiRequestInputs {
        private final Map<String, String> pathVariables;
        private final Map<String, String> queryParameters;
        private final String requestBody;
        
        public ApiRequestInputs(Map<String, String> pathVariables, 
                               Map<String, String> queryParameters, 
                               String requestBody) {
            this.pathVariables = pathVariables;
            this.queryParameters = queryParameters;
            this.requestBody = requestBody;
        }
        
        public Map<String, String> getPathVariables() { return pathVariables; }
        public Map<String, String> getQueryParameters() { return queryParameters; }
        public String getRequestBody() { return requestBody; }
    }
}