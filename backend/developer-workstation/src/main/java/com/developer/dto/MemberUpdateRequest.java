package com.developer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Member update request DTO for updating existing members.
 * All fields are optional for partial updates.
 * 
 * Requirements: 2.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberUpdateRequest {
    
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;
    
    @Email(message = "Email must be valid")
    private String email;
    
    @Size(max = 20, message = "Employee ID cannot exceed 20 characters")
    private String employeeId;
    
    @Size(max = 50, message = "Business unit ID cannot exceed 50 characters")
    private String businessUnitId;
    
    @Size(max = 100, message = "Business unit name cannot exceed 100 characters")
    private String businessUnitName;
    
    @Size(max = 50, message = "Role cannot exceed 50 characters")
    private String role;
    
    private Boolean active;
}