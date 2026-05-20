package com.admin;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Development-only test — disable before deploying to production")
public class BCryptTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Test
    public void testBCrypt() {
        String password = System.getProperty("test.password", "test-password-not-set");
        String hash = ENCODER.encode(password);
        System.out.println("New Hash: " + hash);
        System.out.println("New Hash Matches: " + ENCODER.matches(password, hash));

        // Test with the hash in database
        String dbHash = "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";
        System.out.println("DB Hash: " + dbHash);
        System.out.println("DB Hash matches test password: " + ENCODER.matches(password, dbHash));
        System.out.println("DB Hash matches password: " + ENCODER.matches("password", dbHash));
    }

    @Test
    public void testAdmin123Password() {
        String password = System.getProperty("test.admin.password", "admin-password-not-set");

        // Test with the hash from database
        String dbHash = "$2a$10$XMfQkI8Q4i2ZOLcl.V5RH.SoLTbPpfsxbv0YG21jRr8F7zhNouMle";
        System.out.println("Testing admin password:");
        System.out.println("DB Hash: " + dbHash);
        System.out.println("Hash Length: " + dbHash.length());

        boolean matches = ENCODER.matches(password, dbHash);
        System.out.println("Matches: " + matches);

        // Generate new hash for comparison
        String newHash = ENCODER.encode(password);
        System.out.println("New Hash: " + newHash);
        System.out.println("New Hash Matches: " + ENCODER.matches(password, newHash));

        // Print SQL to update database
        System.out.println("\n=== SQL to update database ===");
        System.out.println("UPDATE sys_users SET password_hash = '" + newHash + "';");

        assertTrue(matches, "admin password should match the DB hash");
    }
}
