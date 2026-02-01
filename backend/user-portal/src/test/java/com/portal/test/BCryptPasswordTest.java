package com.portal.test;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BCryptPasswordTest {
    
    @Test
    public void testAdmin123PasswordHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = "admin123";
        
        // Test the NEW hash from database (after fix)
        String newDbHash = "$2a$10$U8RY1nXkphRLpUyqzy1fOe3W64/nfRmG3ara8YHK2yrWfYMugCKxK";
        
        System.out.println("Testing password: " + password);
        System.out.println("New database hash: " + newDbHash);
        boolean matches = encoder.matches(password, newDbHash);
        System.out.println("Password matches new hash: " + matches);
        
        if (matches) {
            System.out.println("SUCCESS: The new hash matches 'admin123'");
        } else {
            System.out.println("ERROR: The new hash does not match 'admin123'");
        }
        
        assertTrue(matches, "The new hash should match 'admin123'");
    }
}
