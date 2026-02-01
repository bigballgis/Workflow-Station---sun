package com.developer.repository;

import com.developer.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Member entity operations.
 * 
 * Requirements: 2.2, 5.3
 */
@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    
    /**
     * Find member by username
     */
    Optional<Member> findByUsername(String username);
    
    /**
     * Find member by email
     */
    Optional<Member> findByEmail(String email);
    
    /**
     * Find member by employee ID
     */
    Optional<Member> findByEmployeeId(String employeeId);
    
    /**
     * Check if username exists
     */
    boolean existsByUsername(String username);
    
    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);
    
    /**
     * Find all active members
     */
    List<Member> findByActiveTrue();
    
    /**
     * Find members by business unit ID
     */
    List<Member> findByBusinessUnitId(String businessUnitId);
    
    /**
     * Find members by role
     */
    List<Member> findByRole(String role);
    
    /**
     * Find members by business unit and role
     */
    List<Member> findByBusinessUnitIdAndRole(String businessUnitId, String role);
    
    /**
     * Search members by name or username (case-insensitive)
     */
    @Query("SELECT m FROM Member m WHERE " +
           "LOWER(m.fullName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(m.username) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Member> searchByNameOrUsername(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    /**
     * Find members with pagination
     */
    Page<Member> findByActiveTrue(Pageable pageable);
}