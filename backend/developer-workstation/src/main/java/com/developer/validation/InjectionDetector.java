package com.developer.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Specialized component for detecting various types of injection attacks.
 * This detector implements comprehensive pattern matching for SQL injection,
 * XSS attacks, command injection, and other security threats.
 */
@Component
@Slf4j
public class InjectionDetector {
    
    private final List<Pattern> sqlInjectionPatterns;
    private final List<Pattern> xssPatterns;
    private final List<Pattern> commandInjectionPatterns;
    private final List<Pattern> pathTraversalPatterns;
    private final List<Pattern> ldapInjectionPatterns;
    private final List<Pattern> xmlInjectionPatterns;
    
    public InjectionDetector() {
        this.sqlInjectionPatterns = initializeSqlInjectionPatterns();
        this.xssPatterns = initializeXssPatterns();
        this.commandInjectionPatterns = initializeCommandInjectionPatterns();
        this.pathTraversalPatterns = initializePathTraversalPatterns();
        this.ldapInjectionPatterns = initializeLdapInjectionPatterns();
        this.xmlInjectionPatterns = initializeXmlInjectionPatterns();
    }
    
    /**
     * Detects any type of injection attempt in the input
     * 
     * @param input The input to analyze
     * @return InjectionDetectionResult containing detection details
     */
    public InjectionDetectionResult detectInjection(String input) {
        if (!StringUtils.hasText(input)) {
            return InjectionDetectionResult.safe();
        }
        
        InjectionDetectionResult result = new InjectionDetectionResult();
        
        // Check for SQL injection
        if (detectSqlInjection(input)) {
            result.addThreat(InjectionType.SQL_INJECTION, "SQL injection patterns detected");
        }
        
        // Check for XSS
        if (detectXss(input)) {
            result.addThreat(InjectionType.XSS, "Cross-site scripting patterns detected");
        }
        
        // Check for command injection
        if (detectCommandInjection(input)) {
            result.addThreat(InjectionType.COMMAND_INJECTION, "Command injection patterns detected");
        }
        
        // Check for path traversal
        if (detectPathTraversal(input)) {
            result.addThreat(InjectionType.PATH_TRAVERSAL, "Path traversal patterns detected");
        }
        
        // Check for LDAP injection
        if (detectLdapInjection(input)) {
            result.addThreat(InjectionType.LDAP_INJECTION, "LDAP injection patterns detected");
        }
        
        // Check for XML injection
        if (detectXmlInjection(input)) {
            result.addThreat(InjectionType.XML_INJECTION, "XML injection patterns detected");
        }
        
        return result;
    }
    
    /**
     * Detects SQL injection patterns
     */
    public boolean detectSqlInjection(String input) {
        return matchesAnyPattern(input, sqlInjectionPatterns);
    }
    
    /**
     * Detects XSS patterns
     */
    public boolean detectXss(String input) {
        return matchesAnyPattern(input, xssPatterns);
    }
    
    /**
     * Detects command injection patterns
     */
    public boolean detectCommandInjection(String input) {
        return matchesAnyPattern(input, commandInjectionPatterns);
    }
    
    /**
     * Detects path traversal patterns
     */
    public boolean detectPathTraversal(String input) {
        return matchesAnyPattern(input, pathTraversalPatterns);
    }
    
    /**
     * Detects LDAP injection patterns
     */
    public boolean detectLdapInjection(String input) {
        return matchesAnyPattern(input, ldapInjectionPatterns);
    }
    
    /**
     * Detects XML injection patterns
     */
    public boolean detectXmlInjection(String input) {
        return matchesAnyPattern(input, xmlInjectionPatterns);
    }
    
    /**
     * Checks if input matches any pattern in the given list
     */
    private boolean matchesAnyPattern(String input, List<Pattern> patterns) {
        if (!StringUtils.hasText(input) || patterns == null) {
            return false;
        }
        
        for (Pattern pattern : patterns) {
            if (pattern.matcher(input).find()) {
                log.debug("Input matched injection pattern: {}", pattern.pattern());
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Initialize SQL injection detection patterns
     */
    private List<Pattern> initializeSqlInjectionPatterns() {
        List<Pattern> patterns = new ArrayList<>();
        
        // SQL keywords and operations
        patterns.add(Pattern.compile("(?i)\\b(union|select|insert|update|delete|drop|create|alter|exec|execute|sp_|xp_)\\b"));
        
        // SQL comment patterns
        patterns.add(Pattern.compile("(?i)(--|/\\*|\\*/|#)"));
        
        // Quote manipulation
        patterns.add(Pattern.compile("('|(\\\\x27)|(\\\\x2D\\\\x2D)|(\"|\\\\x22))"));
        
        // SQL functions and operators
        patterns.add(Pattern.compile("(?i)\\b(concat|substring|ascii|char|nchar|cast|convert|declare|waitfor|delay)\\b"));
        
        // Boolean-based blind SQL injection
        patterns.add(Pattern.compile("(?i)\\b(and|or)\\s+(\\d+\\s*=\\s*\\d+|'[^']*'\\s*=\\s*'[^']*')"));
        
        // Time-based blind SQL injection
        patterns.add(Pattern.compile("(?i)\\b(sleep|benchmark|pg_sleep|waitfor\\s+delay)\\s*\\("));
        
        // UNION-based injection
        patterns.add(Pattern.compile("(?i)\\bunion\\s+(all\\s+)?select\\b"));
        
        // Stacked queries
        patterns.add(Pattern.compile(";\\s*\\w+"));
        
        return patterns;
    }
    
    /**
     * Initialize XSS detection patterns
     */
    private List<Pattern> initializeXssPatterns() {
        List<Pattern> patterns = new ArrayList<>();
        
        // Script tags
        patterns.add(Pattern.compile("(?i)<\\s*script[^>]*>"));
        patterns.add(Pattern.compile("(?i)</\\s*script\\s*>"));
        
        // Event handlers
        patterns.add(Pattern.compile("(?i)\\bon(load|error|click|mouseover|focus|blur|change|submit|resize|scroll|keydown|keyup|keypress)\\s*="));
        
        // JavaScript protocols
        patterns.add(Pattern.compile("(?i)javascript\\s*:"));
        patterns.add(Pattern.compile("(?i)vbscript\\s*:"));
        
        // Data protocols
        patterns.add(Pattern.compile("(?i)data\\s*:"));
        
        // HTML tags that can execute JavaScript
        patterns.add(Pattern.compile("(?i)<\\s*(iframe|object|embed|applet|meta|link|style|img|svg|video|audio)\\s+[^>]*>"));
        
        // Expression and eval patterns
        patterns.add(Pattern.compile("(?i)\\b(eval|expression|setTimeout|setInterval)\\s*\\("));
        
        // HTML entities that could be used for obfuscation
        patterns.add(Pattern.compile("&#x?[0-9a-f]+;"));
        
        // CSS expression
        patterns.add(Pattern.compile("(?i)expression\\s*\\("));
        
        return patterns;
    }
    
    /**
     * Initialize command injection detection patterns
     */
    private List<Pattern> initializeCommandInjectionPatterns() {
        List<Pattern> patterns = new ArrayList<>();
        
        // Shell metacharacters
        patterns.add(Pattern.compile("[|&;`$(){}\\[\\]<>]"));
        
        // Command separators
        patterns.add(Pattern.compile("(&&|\\|\\||;|\\||&)"));
        
        // System commands
        patterns.add(Pattern.compile("(?i)\\b(cmd|powershell|bash|sh|zsh|csh|tcsh|fish|exec|system|eval|wget|curl|nc|netcat|telnet|ssh|ftp)\\b"));
        
        // File operations
        patterns.add(Pattern.compile("(?i)\\b(cat|type|more|less|head|tail|grep|find|locate|which|whereis)\\b"));
        
        // Network operations
        patterns.add(Pattern.compile("(?i)\\b(ping|nslookup|dig|host|traceroute|netstat|ifconfig|ipconfig)\\b"));
        
        // Process operations
        patterns.add(Pattern.compile("(?i)\\b(ps|kill|killall|pkill|top|htop|jobs|nohup)\\b"));
        
        // Environment variables
        patterns.add(Pattern.compile("\\$\\w+|%\\w+%"));
        
        // Command substitution
        patterns.add(Pattern.compile("`[^`]*`|\\$\\([^)]*\\)"));
        
        return patterns;
    }
    
    /**
     * Initialize path traversal detection patterns
     */
    private List<Pattern> initializePathTraversalPatterns() {
        List<Pattern> patterns = new ArrayList<>();
        
        // Directory traversal sequences
        patterns.add(Pattern.compile("\\.\\.[\\\\/]"));
        patterns.add(Pattern.compile("[\\\\/]\\.\\.[\\\\/]"));
        patterns.add(Pattern.compile("\\.\\.\\\\"));
        patterns.add(Pattern.compile("\\.\\./"));
        
        // URL encoded traversal
        patterns.add(Pattern.compile("%2e%2e%2f|%2e%2e%5c|%2e%2e/|%2e%2e\\\\"));
        patterns.add(Pattern.compile("\\.\\.%2f|\\.\\.%5c"));
        
        // Double URL encoded
        patterns.add(Pattern.compile("%252e%252e%252f|%252e%252e%255c"));
        
        // Unicode encoded
        patterns.add(Pattern.compile("\\u002e\\u002e\\u002f|\\u002e\\u002e\\u005c"));
        
        return patterns;
    }
    
    /**
     * Initialize LDAP injection detection patterns
     */
    private List<Pattern> initializeLdapInjectionPatterns() {
        List<Pattern> patterns = new ArrayList<>();
        
        // LDAP metacharacters
        patterns.add(Pattern.compile("[()=*!&|]"));
        
        // LDAP filter operators
        patterns.add(Pattern.compile("(?i)\\b(objectclass|cn|uid|mail|memberof)\\s*="));
        
        // LDAP wildcards and operators
        patterns.add(Pattern.compile("\\*|\\(\\|"));
        
        return patterns;
    }
    
    /**
     * Initialize XML injection detection patterns
     */
    private List<Pattern> initializeXmlInjectionPatterns() {
        List<Pattern> patterns = new ArrayList<>();
        
        // XML entities and CDATA
        patterns.add(Pattern.compile("(?i)<!\\[CDATA\\["));
        patterns.add(Pattern.compile("(?i)<!DOCTYPE"));
        patterns.add(Pattern.compile("(?i)<!ENTITY"));
        
        // XML external entities
        patterns.add(Pattern.compile("(?i)SYSTEM\\s+[\"'][^\"']*[\"']"));
        patterns.add(Pattern.compile("(?i)PUBLIC\\s+[\"'][^\"']*[\"']"));
        
        // XML processing instructions
        patterns.add(Pattern.compile("<\\?xml"));
        
        return patterns;
    }
}