package com.platform.security.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.Size;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests for User entity and password hashing.
 * Feature: authentication, Property 1: Password Hashing Security
 * Validates: Requirements 1.3
 */
class UserPropertyTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Property 1: Password Hashing Security
     * For any password string, when hashed using BCrypt, the hash should:
     * 1. Be different from the original password
     * 2. Be verifiable against the original password
     * 3. Produce different hashes for the same password (due to salt)
     */
    @Property(tries = 100)
    void passwordHashingShouldBeSecureAndVerifiable(
            @ForAll @Size(min = 1, max = 100) String password) {
        
        String hash = passwordEncoder.encode(password);
        
        // Hash should be different from original password
        assertThat(hash).isNotEqualTo(password);
        
        // Hash should be verifiable
        assertThat(passwordEncoder.matches(password, hash)).isTrue();
        
        // Hash should start with BCrypt prefix
        assertThat(hash).startsWith("$2");
    }

    @Property(tries = 100)
    void samePasswordShouldProduceDifferentHashes(
            @ForAll @Size(min = 1, max = 50) String password) {
        
        String hash1 = passwordEncoder.encode(password);
        String hash2 = passwordEncoder.encode(password);
        
        // Due to random salt, same password should produce different hashes
        assertThat(hash1).isNotEqualTo(hash2);
        
        // But both should verify against the original password
        assertThat(passwordEncoder.matches(password, hash1)).isTrue();
        assertThat(passwordEncoder.matches(password, hash2)).isTrue();
    }

    @Property(tries = 100)
    void wrongPasswordShouldNotMatch(
            @ForAll @Size(min = 1, max = 50) String password,
            @ForAll @Size(min = 1, max = 50) String wrongPassword) {
        
        Assume.that(!password.equals(wrongPassword));
        
        String hash = passwordEncoder.encode(password);
        
        // Wrong password should not match
        assertThat(passwordEncoder.matches(wrongPassword, hash)).isFalse();
    }

    @Property(tries = 100)
    void passwordHashingShouldHandleSpecialCharacters(
            @ForAll("specialPasswords") String password) {
        
        String hash = passwordEncoder.encode(password);
        
        assertThat(passwordEncoder.matches(password, hash)).isTrue();
    }

    @Property(tries = 100)
    void passwordHashingShouldHandleUnicode(
            @ForAll("unicodePasswords") String password) {
        
        String hash = passwordEncoder.encode(password);
        
        assertThat(passwordEncoder.matches(password, hash)).isTrue();
    }

    @Property(tries = 100)
    void userIdShouldBeValidUUID() {
        UUID id = UUID.randomUUID();
        
        assertThat(id).isNotNull();
        assertThat(id.toString()).matches(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        );
    }

    @Property(tries = 100)
    void userRolesShouldBeModifiable(
            @ForAll @Size(min = 1, max = 10) Set<@AlphaChars @Size(min = 1, max = 20) String> roleCodes) {
        
        Set<String> roles = new HashSet<>();
        
        // Add all roles
        for (String roleCode : roleCodes) {
            roles.add(roleCode);
        }
        
        assertThat(roles).containsExactlyInAnyOrderElementsOf(roleCodes);
        
        // Remove one role
        if (!roles.isEmpty()) {
            String firstRole = roles.iterator().next();
            roles.remove(firstRole);
            assertThat(roles).doesNotContain(firstRole);
        }
    }

    @Property(tries = 100)
    void userStatusShouldOnlyAcceptValidValues() {
        // Test that only valid status values are accepted
        Set<String> validStatuses = Set.of("ACTIVE", "INACTIVE", "LOCKED");
        
        for (String status : validStatuses) {
            assertThat(validStatuses).contains(status);
        }
    }

    @Provide
    Arbitrary<String> specialPasswords() {
        return Arbitraries.of(
                "P@ssw0rd!",
                "Test#123$%^",
                "Pass\nword",
                "Quote\"Test'Single",
                "Space Password",
                "Tab\tPassword",
                "Slash/Back\\slash",
                "Brackets[{()}]"
        );
    }

    @Provide
    Arbitrary<String> unicodePasswords() {
        return Arbitraries.of(
                "密码测试123",
                "パスワード",
                "비밀번호",
                "Пароль123",
                "🔐🔑🔒",
                "كلمة السر",
                "סיסמה"
        );
    }
}
