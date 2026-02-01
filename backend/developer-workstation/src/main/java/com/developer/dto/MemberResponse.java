package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Member response DTO for returning member information.
 * 
 * Requirements: 2.2, 2.5
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberResponse {
    
    private String id;
    private String username;
    private String fullName;
    private String email;
    private String employeeId;
    private String businessUnitId;
    private String businessUnitName;
    private String role;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
}