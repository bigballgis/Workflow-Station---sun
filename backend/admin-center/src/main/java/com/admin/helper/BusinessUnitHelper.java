package com.admin.helper;

import com.admin.enums.BusinessUnitStatus;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.UserBusinessUnitRepository;
import com.admin.repository.UserRepository;
import com.admin.util.EntityTypeConverter;
import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.User;
import com.platform.security.entity.UserBusinessUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Helper service for business unit operations.
 * 
 * <p>This service provides utility methods for working with BusinessUnit entities from platform-security,
 * including member management, status operations, and hierarchy navigation. It handles the complexity
 * of fetching related entities explicitly since platform-security uses ID-based relationships.</p>
 * 
 * <p><strong>Key Responsibilities:</strong></p>
 * <ul>
 *   <li>Member count and member retrieval operations</li>
 *   <li>Status conversion between String and BusinessUnitStatus enum</li>
 *   <li>Active status checking</li>
 *   <li>Hierarchy navigation (parent, children)</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * @Autowired
 * private BusinessUnitHelper businessUnitHelper;
 * 
 * // Get member count
 * long memberCount = businessUnitHelper.getMemberCount(businessUnitId);
 * 
 * // Get all members
 * List<User> members = businessUnitHelper.getMembers(businessUnitId);
 * 
 * // Check if active
 * if (businessUnitHelper.isActive(businessUnit)) {
 *     // Process active business unit
 * }
 * 
 * // Get status as enum
 * BusinessUnitStatus status = businessUnitHelper.getStatus(businessUnit);
 * 
 * // Navigate hierarchy
 * List<BusinessUnit> children = businessUnitHelper.getChildren(businessUnitId);
 * BusinessUnit parent = businessUnitHelper.getParent(businessUnitId);
 * }</pre>
 * 
 * @author Entity Architecture Alignment
 * @version 1.0
 * @see BusinessUnit
 * @see BusinessUnitStatus
 * @see EntityTypeConverter
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessUnitHelper {
    
    private final BusinessUnitRepository businessUnitRepository;
    private final UserBusinessUnitRepository userBusinessUnitRepository;
    private final UserRepository userRepository;
    
    /**
     * Gets the member count for a business unit.
     * 
     * <p>This method queries the UserBusinessUnit repository to count how many users
     * are members of the specified business unit. The count includes all members regardless
     * of their user status.</p>
     * 
     * @param businessUnitId the ID of the business unit
     * @return the number of members in the business unit, 0 if the unit has no members or doesn't exist
     */
    public long getMemberCount(String businessUnitId) {
        if (businessUnitId == null) {
            log.warn("Attempted to get member count for null businessUnitId");
            return 0;
        }
        
        log.debug("Getting member count for business unit: {}", businessUnitId);
        return userBusinessUnitRepository.countByBusinessUnitId(businessUnitId);
    }
    
    /**
     * Gets all members of a business unit.
     * 
     * <p>This method retrieves all User entities that are members of the specified business unit.
     * It uses ID-based queries to fetch the members and then explicitly fetches the User entities.</p>
     * 
     * <p><strong>Note:</strong> This method performs two database queries:</p>
     * <ol>
     *   <li>Fetch all UserBusinessUnit records for the business unit</li>
     *   <li>Batch fetch all User entities by their IDs</li>
     * </ol>
     * 
     * @param businessUnitId the ID of the business unit
     * @return a list of User entities that are members of the business unit, empty list if no members or unit doesn't exist
     */
    public List<User> getMembers(String businessUnitId) {
        if (businessUnitId == null) {
            log.warn("Attempted to get members for null businessUnitId");
            return List.of();
        }
        
        log.debug("Getting members for business unit: {}", businessUnitId);
        
        // Fetch all member relationships
        List<UserBusinessUnit> members = userBusinessUnitRepository.findByBusinessUnitId(businessUnitId);
        
        if (members.isEmpty()) {
            return List.of();
        }
        
        // Extract user IDs
        List<String> userIds = members.stream()
                .map(UserBusinessUnit::getUserId)
                .distinct()
                .collect(Collectors.toList());
        
        // Batch fetch users
        return userRepository.findAllById(userIds);
    }
    
    /**
     * Gets the BusinessUnitStatus enum for a BusinessUnit entity.
     * 
     * <p>This method converts the String status field from the platform-security BusinessUnit entity
     * to the type-safe BusinessUnitStatus enum used in admin-center business logic.</p>
     * 
     * <p>Supported statuses:</p>
     * <ul>
     *   <li>ACTIVE - Business unit is active and operational</li>
     *   <li>DISABLED - Business unit is disabled</li>
     * </ul>
     * 
     * @param unit the BusinessUnit entity
     * @return the BusinessUnitStatus enum, or null if the unit is null
     * @throws IllegalArgumentException if the unit's status field contains an invalid value
     */
    public BusinessUnitStatus getStatus(BusinessUnit unit) {
        if (unit == null) {
            return null;
        }
        
        return EntityTypeConverter.toBusinessUnitStatus(unit.getStatus());
    }
    
    /**
     * Checks if a business unit is active.
     * 
     * <p>A business unit is active if its status field equals "ACTIVE".
     * Inactive business units may have status values like "DISABLED", "DELETED", etc.</p>
     * 
     * @param unit the BusinessUnit entity to check
     * @return true if the unit's status is "ACTIVE", false otherwise
     */
    public boolean isActive(BusinessUnit unit) {
        if (unit == null) {
            return false;
        }
        
        return "ACTIVE".equals(unit.getStatus());
    }
    
    /**
     * Gets all child business units of a parent business unit.
     * 
     * <p>This method retrieves all direct children of the specified business unit.
     * It does not recursively fetch descendants; only immediate children are returned.</p>
     * 
     * <p>The results are ordered by the sortOrder field to maintain the organizational hierarchy.</p>
     * 
     * @param businessUnitId the ID of the parent business unit
     * @return a list of child BusinessUnit entities, empty list if no children or parent doesn't exist
     */
    public List<BusinessUnit> getChildren(String businessUnitId) {
        if (businessUnitId == null) {
            log.warn("Attempted to get children for null businessUnitId");
            return List.of();
        }
        
        log.debug("Getting children for business unit: {}", businessUnitId);
        return businessUnitRepository.findByParentIdOrderBySortOrder(businessUnitId);
    }
    
    /**
     * Gets the parent business unit of a child business unit.
     * 
     * <p>This method retrieves the parent BusinessUnit entity by using the parentId field
     * from the child business unit. If the child has no parent (i.e., it's a root business unit),
     * this method returns null.</p>
     * 
     * @param businessUnitId the ID of the child business unit
     * @return the parent BusinessUnit entity, or null if the child has no parent, the child doesn't exist, or the parent doesn't exist
     */
    public BusinessUnit getParent(String businessUnitId) {
        if (businessUnitId == null) {
            log.warn("Attempted to get parent for null businessUnitId");
            return null;
        }
        
        log.debug("Getting parent for business unit: {}", businessUnitId);
        
        // Fetch the child business unit
        Optional<BusinessUnit> childOpt = businessUnitRepository.findById(businessUnitId);
        if (childOpt.isEmpty()) {
            log.warn("Business unit not found: {}", businessUnitId);
            return null;
        }
        
        BusinessUnit child = childOpt.get();
        String parentId = child.getParentId();
        
        if (parentId == null || parentId.isEmpty()) {
            log.debug("Business unit {} has no parent (root unit)", businessUnitId);
            return null;
        }
        
        // Fetch the parent business unit
        return businessUnitRepository.findById(parentId).orElse(null);
    }
}
