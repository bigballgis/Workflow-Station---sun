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
        
        // Test with the hash from database
        String dbHash = "$2a$10$XMfQkI8Q4i2ZOLcl.V5RH.SoLTbPpfsxbv0YG21jRr8F7zhNouMle";
        System.out.println("Testing admin123 password:");
        System.out.println("Password: " + password);
        System.out.println("DB Hash: " + dbHash);
        System.out.println("Hash Length: " + dbHash.length());
        
        boolean matches = encoder.matches(password, dbHash);
        System.out.println("Matches: " + matches);
        
        // Generate new hash for comparison
        String newHash = encoder.encode(password);
        System.out.println("New Hash: " + newHash);
        System.out.println("New Hash Matches: " + encoder.matches(password, newHash));
        
        // Print SQL to update database
        System.out.println("\n=== SQL to update database ===");
        System.out.println("UPDATE sys_users SET password_hash = '" + newHash + "';");
        
        assertTrue(matches, "admin123 should match the DB hash");
    }
}
