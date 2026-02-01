package com.developer.service;

import com.developer.dto.MemberRequest;
import com.developer.dto.MemberResponse;
import com.developer.dto.MemberUpdateRequest;
import com.developer.entity.Member;
import com.developer.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Service class for member management operations.
 * 
 * Requirements: 2.2, 3.1, 3.2
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {
    
    private final MemberRepository memberRepository;
    
    /**
     * Create a new member
     */
    @Transactional
    public MemberResponse createMember(MemberRequest request, String currentUserId) {
        log.info("Creating new member with username: {}", request.getUsername());
        
        try {
            // Validate unique constraints
            validateUniqueConstraints(request.getUsername(), request.getEmail(), null);
            
            // Create member entity
            Member member = Member.builder()
                    .username(request.getUsername())
                    .fullName(request.getFullName())
                    .email(request.getEmail())
                    .employeeId(request.getEmployeeId())
                    .businessUnitId(request.getBusinessUnitId())
                    .businessUnitName(request.getBusinessUnitName())
                    .role(request.getRole())
                    .active(true)
                    .createdBy(currentUserId)
                    .updatedBy(currentUserId)
                    .build();
            
            Member savedMember = memberRepository.save(member);
            log.info("Member created successfully with ID: {}", savedMember.getId());
            
            return mapToResponse(savedMember);
            
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create member with username: {}", request.getUsername(), e);
            throw new RuntimeException("Failed to create member: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get member by ID
     */
    public MemberResponse getMember(Long id) {
        log.info("Retrieving member with ID: {}", id);
        
        try {
            Member member = memberRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Member not found with ID: " + id));
            
            return mapToResponse(member);
            
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve member with ID: {}", id, e);
            throw new RuntimeException("Failed to retrieve member: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get member by username
     */
    public Optional<MemberResponse> getMemberByUsername(String username) {
        log.info("Retrieving member with username: {}", username);
        
        try {
            return memberRepository.findByUsername(username)
                    .map(this::mapToResponse);
                    
        } catch (Exception e) {
            log.error("Failed to retrieve member with username: {}", username, e);
            throw new RuntimeException("Failed to retrieve member by username: " + e.getMessage(), e);
        }
    }
    
    /**
     * Update member
     */
    @Transactional
    public MemberResponse updateMember(Long id, MemberUpdateRequest request, String currentUserId) {
        log.info("Updating member with ID: {}", id);
        
        try {
            Member existingMember = memberRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Member not found with ID: " + id));
            
            // Validate unique constraints if email is being updated
            if (StringUtils.hasText(request.getEmail()) && 
                !request.getEmail().equals(existingMember.getEmail())) {
                validateEmailUnique(request.getEmail(), id);
            }
            
            // Update fields if provided
            updateMemberFields(existingMember, request, currentUserId);
            
            Member updatedMember = memberRepository.save(existingMember);
            log.info("Member updated successfully with ID: {}", updatedMember.getId());
            
            return mapToResponse(updatedMember);
            
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update member with ID: {}", id, e);
            throw new RuntimeException("Failed to update member: " + e.getMessage(), e);
        }
    }
    
    /**
     * Delete member (soft delete by setting active to false)
     */
    @Transactional
    public void deleteMember(Long id, String currentUserId) {
        log.info("Deleting member with ID: {}", id);
        
        try {
            Member member = memberRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Member not found with ID: " + id));
            
            member.setActive(false);
            member.setUpdatedBy(currentUserId);
            memberRepository.save(member);
            
            log.info("Member deleted successfully with ID: {}", id);
            
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete member with ID: {}", id, e);
            throw new RuntimeException("Failed to delete member: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get all active members with pagination
     */
    public Page<MemberResponse> getAllActiveMembers(Pageable pageable) {
        log.info("Retrieving all active members with pagination");
        
        try {
            Page<Member> members = memberRepository.findByActiveTrue(pageable);
            return members.map(this::mapToResponse);
            
        } catch (Exception e) {
            log.error("Failed to retrieve active members", e);
            throw new RuntimeException("Failed to retrieve active members: " + e.getMessage(), e);
        }
    }
    
    /**
     * Search members by name or username
     */
    public Page<MemberResponse> searchMembers(String searchTerm, Pageable pageable) {
        log.info("Searching members with term: {}", searchTerm);
        
        try {
            Page<Member> members = memberRepository.searchByNameOrUsername(searchTerm, pageable);
            return members.map(this::mapToResponse);
            
        } catch (Exception e) {
            log.error("Failed to search members with term: {}", searchTerm, e);
            throw new RuntimeException("Failed to search members: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get members by business unit
     */
    public List<MemberResponse> getMembersByBusinessUnit(String businessUnitId) {
        log.info("Retrieving members for business unit: {}", businessUnitId);
        
        try {
            List<Member> members = memberRepository.findByBusinessUnitId(businessUnitId);
            return members.stream()
                    .map(this::mapToResponse)
                    .toList();
                    
        } catch (Exception e) {
            log.error("Failed to retrieve members for business unit: {}", businessUnitId, e);
            throw new RuntimeException("Failed to retrieve members by business unit: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validate unique constraints for username and email
     */
    private void validateUniqueConstraints(String username, String email, Long excludeId) {
        if (memberRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already exists: " + username);
        }
        
        validateEmailUnique(email, excludeId);
    }
    
    /**
     * Validate email uniqueness
     */
    private void validateEmailUnique(String email, Long excludeId) {
        Optional<Member> existingMember = memberRepository.findByEmail(email);
        if (existingMember.isPresent() && 
            (excludeId == null || !existingMember.get().getId().equals(excludeId))) {
            throw new RuntimeException("Email already exists: " + email);
        }
    }
    
    /**
     * Update member fields from request
     */
    private void updateMemberFields(Member member, MemberUpdateRequest request, String currentUserId) {
        if (StringUtils.hasText(request.getFullName())) {
            member.setFullName(request.getFullName());
        }
        if (StringUtils.hasText(request.getEmail())) {
            member.setEmail(request.getEmail());
        }
        if (StringUtils.hasText(request.getEmployeeId())) {
            member.setEmployeeId(request.getEmployeeId());
        }
        if (StringUtils.hasText(request.getBusinessUnitId())) {
            member.setBusinessUnitId(request.getBusinessUnitId());
        }
        if (StringUtils.hasText(request.getBusinessUnitName())) {
            member.setBusinessUnitName(request.getBusinessUnitName());
        }
        if (StringUtils.hasText(request.getRole())) {
            member.setRole(request.getRole());
        }
        if (request.getActive() != null) {
            member.setActive(request.getActive());
        }
        member.setUpdatedBy(currentUserId);
    }
    
    /**
     * Map Member entity to MemberResponse DTO
     */
    private MemberResponse mapToResponse(Member member) {
        return MemberResponse.builder()
                .id(member.getId().toString())
                .username(member.getUsername())
                .fullName(member.getFullName())
                .email(member.getEmail())
                .employeeId(member.getEmployeeId())
                .businessUnitId(member.getBusinessUnitId())
                .businessUnitName(member.getBusinessUnitName())
                .role(member.getRole())
                .active(member.getActive())
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .createdBy(member.getCreatedBy())
                .updatedBy(member.getUpdatedBy())
                .build();
    }
}