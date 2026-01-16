package com.platform.security.property;

import com.platform.security.dto.ResolvedUser;
import com.platform.security.enums.AssignmentTargetType;
import com.platform.security.resolver.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Target Resolvers
 * Feature: role-assignment-targets
 * 
 * Note: Department-related resolvers have been removed as part of the
 * migration from Department to BusinessUnit architecture.
 * Only USER and VIRTUAL_GROUP target types are now supported.
 */
class TargetResolverProperties {

    /**
     * Property 1: USER Assignment Grants Role to Single User
     * For any role assignment with target type USER, the effective users for that assignment
     * SHALL contain exactly one user - the user specified by the target ID.
     * Validates: Requirements 1.2
     */
    @Property(tries = 100)
    void userAssignmentResolvesToSingleUser(
            @ForAll @NotBlank @Size(max = 64) String userId,
            @ForAll @NotBlank @Size(max = 100) String username,
            @ForAll @NotBlank @Size(max = 100) String displayName
    ) {
        // Arrange
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UserTargetResolver resolver = new UserTargetResolver(jdbcTemplate);
        
        // Mock user exists
        when(jdbcTemplate.queryForObject(
                contains("COUNT(*)"),
                eq(Integer.class),
                eq(userId)
        )).thenReturn(1);
        
        // Mock user query
        ResolvedUser expectedUser = ResolvedUser.builder()
                .userId(userId)
                .username(username)
                .displayName(displayName)
                .build();
        
        when(jdbcTemplate.query(
                contains("FROM sys_users"),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq(userId)
        )).thenReturn(List.of(expectedUser));
        
        // Act
        List<ResolvedUser> users = resolver.resolveUsers(userId);
        
        // Assert
        assertThat(resolver.getTargetType()).isEqualTo(AssignmentTargetType.USER);
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getUserId()).isEqualTo(userId);
    }

    /**
     * Property 2: VIRTUAL_GROUP Assignment Grants Role to Group Members
     * For any role assignment with target type VIRTUAL_GROUP, the effective users
     * SHALL equal the set of active members of the virtual group specified by the target ID.
     * Validates: Requirements 1.5, 3.4
     */
    @Property(tries = 100)
    void virtualGroupAssignmentResolvesToGroupMembers(
            @ForAll @NotBlank @Size(max = 64) String groupId,
            @ForAll @Size(min = 0, max = 10) List<@NotBlank @Size(max = 64) String> memberIds
    ) {
        // Arrange
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        VirtualGroupTargetResolver resolver = new VirtualGroupTargetResolver(jdbcTemplate);
        
        // Mock group exists
        when(jdbcTemplate.queryForObject(
                contains("COUNT(*)"),
                eq(Integer.class),
                eq(groupId)
        )).thenReturn(1);
        
        // Create expected members
        List<ResolvedUser> expectedMembers = memberIds.stream()
                .map(id -> ResolvedUser.builder()
                        .userId(id)
                        .build())
                .collect(Collectors.toList());
        
        when(jdbcTemplate.query(
                contains("group_id = ?"),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq(groupId)
        )).thenReturn(expectedMembers);
        
        // Act
        List<ResolvedUser> users = resolver.resolveUsers(groupId);
        
        // Assert
        assertThat(resolver.getTargetType()).isEqualTo(AssignmentTargetType.VIRTUAL_GROUP);
        assertThat(users).hasSize(memberIds.size());
        
        // All resolved users should be from the member list
        Set<String> resolvedIds = users.stream()
                .map(ResolvedUser::getUserId)
                .collect(Collectors.toSet());
        assertThat(resolvedIds).containsExactlyInAnyOrderElementsOf(memberIds);
    }

    /**
     * Property: Target resolver factory returns correct resolver for supported types
     * Only USER and VIRTUAL_GROUP are supported after Department removal
     */
    @Property(tries = 50)
    void factoryReturnsCorrectResolverForSupportedTypes(
            @ForAll("supportedTargetTypes") AssignmentTargetType targetType
    ) {
        // Arrange
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        List<TargetResolver> resolvers = List.of(
                new UserTargetResolver(jdbcTemplate),
                new VirtualGroupTargetResolver(jdbcTemplate)
        );
        TargetResolverFactory factory = new TargetResolverFactory(resolvers);
        
        // Act
        TargetResolver resolver = factory.getResolver(targetType);
        
        // Assert
        assertThat(resolver.getTargetType()).isEqualTo(targetType);
    }
    
    @Provide
    Arbitrary<AssignmentTargetType> supportedTargetTypes() {
        return Arbitraries.of(AssignmentTargetType.USER, AssignmentTargetType.VIRTUAL_GROUP);
    }
}
