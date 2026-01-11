package com.admin;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BCryptTest {
    @Test
    public void testBCrypt() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = "test123";
        String hash = encoder.encode(password);
        System.out.println("Password: " + password);
        System.out.println("New Hash: " + hash);
        System.out.println("New Hash Matches: " + encoder.matches(password, hash));
        
        // Test with the hash in database
        String dbHash = "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";
        System.out.println("DB Hash: " + dbHash);
        System.out.println("DB Hash matches test123: " + encoder.matches(password, dbHash));
        System.out.println("DB Hash matches password: " + encoder.matches("password", dbHash));
    }
    
    @Test
    public void testAdmin123Password() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = "admin123";
        String storedHash = "$2a$10$EIXvYkRAhq0xaOye6lEnoOQowMIJQx1QpO1XLbHrZhtLc/4sHlUHq";
        
        System.out.println("Testing admin123 password:");
        System.out.println("Password: " + password);
        System.out.println("Stored Hash: " + storedHash);
        System.out.println("Hash Length: " + storedHash.length());
        
        boolean matches = encoder.matches(password, storedHash);
        System.out.println("Matches: " + matches);
        
        // Generate new hash for comparison
        String newHash = encoder.encode(password);
        System.out.println("New Hash: " + newHash);
        System.out.println("New Hash Matches: " + encoder.matches(password, newHash));
        
        assertTrue(matches, "admin123 should match the stored hash");
    }
}
