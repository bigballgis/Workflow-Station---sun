package com.developer.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Comprehensive input sanitization engine that provides multiple sanitization strategies
 * for preventing XSS, injection attacks, and other security threats.
 */
@Component
@Slf4j
public class SanitizationEngine {
    
    // HTML entity mappings for encoding
    private static final Map<String, String> HTML_ENTITIES = new HashMap<>();
    
    // Patterns for various sanitization operations
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("(?i)<\\s*script[^>]*>.*?</\\s*script\\s*>", Pattern.DOTALL);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern SQL_COMMENT_PATTERN = Pattern.compile("(?i)(--|/\\*.*?\\*/|#.*)");
    private static final Pattern JAVASCRIPT_PROTOCOL_PATTERN = Pattern.compile("(?i)javascript\\s*:");
    private static final Pattern DATA_PROTOCOL_PATTERN = Pattern.compile("(?i)data\\s*:");
    private static final Pattern VBSCRIPT_PROTOCOL_PATTERN = Pattern.compile("(?i)vbscript\\s*:");
    private static final Pattern EVENT_HANDLER_PATTERN = Pattern.compile("(?i)\\bon(\\w+)\\s*=");
    private static final Pattern NULL_BYTE_PATTERN = Pattern.compile("\\x00");
    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
    
    static {
        // Initialize HTML entity mappings
        HTML_ENTITIES.put("&", "&amp;");
        HTML_ENTITIES.put("<", "&lt;");
        HTML_ENTITIES.put(">", "&gt;");
        HTML_ENTITIES.put("\"", "&quot;");
        HTML_ENTITIES.put("'", "&#x27;");
        HTML_ENTITIES.put("/", "&#x2F;");
        HTML_ENTITIES.put("\\", "&#x5C;");
        HTML_ENTITIES.put("`", "&#x60;");
        HTML_ENTITIES.put("=", "&#x3D;");
    }
    
    /**
     * Applies comprehensive sanitization using multiple strategies
     * 
     * @param input The input to sanitize
     * @return Sanitized input safe for use
     */
    public String sanitize(String input) {
        return sanitize(input, SanitizationStrategy.COMPREHENSIVE);
    }
    
    /**
     * Applies sanitization using the specified strategy
     * 
     * @param input The input to sanitize
     * @param strategy The sanitization strategy to use
     * @return Sanitized input
     */
    public String sanitize(String input, SanitizationStrategy strategy) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        String sanitized = input;
        
        try {
            switch (strategy) {
                case HTML_ENCODE:
                    sanitized = htmlEncode(sanitized);
                    break;
                case XSS_PREVENTION:
                    sanitized = preventXss(sanitized);
                    break;
                case SQL_SAFE:
                    sanitized = makeSqlSafe(sanitized);
                    break;
                case COMMAND_SAFE:
                    sanitized = makeCommandSafe(sanitized);
                    break;
                case COMPREHENSIVE:
                    sanitized = applyComprehensiveSanitization(sanitized);
                    break;
                case STRICT:
                    sanitized = applyStrictSanitization(sanitized);
                    break;
            }
            
            log.debug("Sanitized input using strategy {}: {} -> {}", 
                strategy, input.length() > 50 ? input.substring(0, 50) + "..." : input,
                sanitized.length() > 50 ? sanitized.substring(0, 50) + "..." : sanitized);
                
        } catch (Exception e) {
            log.error("Error during sanitization with strategy {}: {}", strategy, e.getMessage());
            // Fallback to basic HTML encoding if sanitization fails
            sanitized = htmlEncode(input);
        }
        
        return sanitized;
    }
    
    /**
     * HTML encodes dangerous characters
     */
    public String htmlEncode(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        String encoded = input;
        for (Map.Entry<String, String> entity : HTML_ENTITIES.entrySet()) {
            encoded = encoded.replace(entity.getKey(), entity.getValue());
        }
        
        return encoded;
    }
    
    /**
     * Decodes HTML entities back to original characters
     */
    public String htmlDecode(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        String decoded = input;
        for (Map.Entry<String, String> entity : HTML_ENTITIES.entrySet()) {
            decoded = decoded.replace(entity.getValue(), entity.getKey());
        }
        
        return decoded;
    }
    
    /**
     * URL encodes input for safe use in URLs
     */
    public String urlEncode(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        try {
            return URLEncoder.encode(input, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Error URL encoding input: {}", e.getMessage());
            return input;
        }
    }
    
    /**
     * URL decodes input
     */
    public String urlDecode(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        try {
            return URLDecoder.decode(input, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Error URL decoding input: {}", e.getMessage());
            return input;
        }
    }
    
    /**
     * Prevents XSS attacks by removing/encoding dangerous elements
     */
    public String preventXss(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        String sanitized = input;
        
        // Remove script tags completely
        sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("");
        
        // Remove dangerous protocols
        sanitized = JAVASCRIPT_PROTOCOL_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = DATA_PROTOCOL_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = VBSCRIPT_PROTOCOL_PATTERN.matcher(sanitized).replaceAll("");
        
        // Remove event handlers
        sanitized = EVENT_HANDLER_PATTERN.matcher(sanitized).replaceAll("");
        
        // HTML encode remaining dangerous characters
        sanitized = htmlEncode(sanitized);
        
        return sanitized;
    }
    
    /**
     * Makes input safe for SQL queries by removing/escaping dangerous patterns
     */
    public String makeSqlSafe(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        String sanitized = input;
        
        // Remove SQL comments
        sanitized = SQL_COMMENT_PATTERN.matcher(sanitized).replaceAll("");
        
        // Escape single quotes by doubling them
        sanitized = sanitized.replace("'", "''");
        
        // Remove null bytes
        sanitized = NULL_BYTE_PATTERN.matcher(sanitized).replaceAll("");
        
        return sanitized;
    }
    
    /**
     * Makes input safe for command execution by removing shell metacharacters
     */
    public String makeCommandSafe(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        String sanitized = input;
        
        // Remove dangerous shell metacharacters
        sanitized = sanitized.replaceAll("[|&;`$(){}\\[\\]<>]", "");
        
        // Remove command separators
        sanitized = sanitized.replaceAll("(&&|\\|\\||;)", "");
        
        // Remove null bytes and control characters
        sanitized = CONTROL_CHAR_PATTERN.matcher(sanitized).replaceAll("");
        
        return sanitized;
    }
    
    /**
     * Applies comprehensive sanitization combining multiple strategies
     */
    private String applyComprehensiveSanitization(String input) {
        String sanitized = input;
        
        // First, remove null bytes and control characters
        sanitized = CONTROL_CHAR_PATTERN.matcher(sanitized).replaceAll("");
        
        // Apply XSS prevention (this includes HTML encoding)
        sanitized = preventXss(sanitized);
        
        // Apply SQL safety measures (but don't double-encode)
        sanitized = makeSqlSafe(sanitized);
        
        // Don't apply command safety for comprehensive as it removes too many characters
        // Command safety should be used only when specifically needed
        
        return sanitized;
    }
    
    /**
     * Applies strict sanitization that removes most special characters
     */
    private String applyStrictSanitization(String input) {
        String sanitized = input;
        
        // Remove all HTML tags
        sanitized = HTML_TAG_PATTERN.matcher(sanitized).replaceAll("");
        
        // Apply comprehensive sanitization
        sanitized = applyComprehensiveSanitization(sanitized);
        
        // Remove additional potentially dangerous characters
        sanitized = sanitized.replaceAll("[<>\"'`]", "");
        
        return sanitized;
    }
    
    /**
     * Removes all HTML tags from input
     */
    public String stripHtmlTags(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        return HTML_TAG_PATTERN.matcher(input).replaceAll("");
    }
    
    /**
     * Validates that sanitized input is safe
     * 
     * @param original The original input
     * @param sanitized The sanitized input
     * @return true if sanitization was successful and safe
     */
    public boolean validateSanitization(String original, String sanitized) {
        if (original == null && sanitized == null) {
            return true;
        }
        
        if (original == null || sanitized == null) {
            return false;
        }
        
        // Check that dangerous patterns were removed
        return !SCRIPT_PATTERN.matcher(sanitized).find() &&
               !JAVASCRIPT_PROTOCOL_PATTERN.matcher(sanitized).find() &&
               !DATA_PROTOCOL_PATTERN.matcher(sanitized).find() &&
               !EVENT_HANDLER_PATTERN.matcher(sanitized).find() &&
               !NULL_BYTE_PATTERN.matcher(sanitized).find();
    }
}