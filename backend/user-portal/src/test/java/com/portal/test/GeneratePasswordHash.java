package com.portal.test;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GeneratePasswordHash {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = "admin123";
        
        // Generate a hash
        String hash = encoder.encode(password);
        
        // Verify it matches
        boolean matches = encoder.matches(password, hash);
        
        System.out.println("Password: " + password);
        System.out.println("Generated Hash: " + hash);
        System.out.println("Hash matches password: " + matches);
        
        if (matches) {
            System.out.println("\nUse this SQL to update passwords:");
            System.out.println("UPDATE sys_users SET password_hash = '" + hash + "' WHERE username IN ('purchase.requester', 'dept.reviewer', 'countersign.approver1', 'countersign.approver2', 'finance.reviewer', 'parent.reviewer', 'core.lead', 'tech.director');");
        }
    }
}
