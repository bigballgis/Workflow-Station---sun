package com.admin.helper;

import com.admin.enums.VirtualGroupType;
import com.admin.repository.UserRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.util.EntityTypeConverter;
import com.platform.security.entity.User;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper service for virtual group operations.
 * 
 * <p>This service provides utility methods for working with VirtualGroup entities from platform-security,
 * including validation, member management, and type operations. It handles the complexity of fetching
 * related entities explicitly since platform-security uses ID-based relationships.</p>
 * 
 * <p><strong>Key Responsibilities:</strong></p>
 * <ul>
 *   <li>Virtual group validation (active status, validity checks)</li>
 *   <li>Member count and member retrieval operations</li>
 *   <li>Type conversion between String and VirtualGroupType enum</li>
 *   <li>Business group identification</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * @Autowired
 * private VirtualGroupHelper virtualGroupHelper;
 * 
 * // Check if a virtual group is valid
 * if (virtualGroupHelper.isValid(virtualGroup)) {
 *     // Process valid group
 * }
 * 
 * // Get member count
 * long memberCount = virtualGroupHelper.getMemberCount(virtualGroupId);
 * 
 * // Get all members
 * List<User> members = virtualGroupHelper.getMembers(virtualGroupId);
 * 
 * // Get group type as enum
 * VirtualGroupType groupType = virtualGroupHelper.getGroupType(virtualGroup);
 * }</pre>
 * 
 * @author Entity Architecture Alignment
 * @version 1.0
 * @see VirtualGroup
 * @see VirtualGroupType
 * @see EntityTypeConverter
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualGroupHelper {
    
    private final VirtualGroupRepository virtualGroupRepository;
    private final VirtualGroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    
    /**
     * Checks if a virtual group is valid.
     * 
     * <p>A virtual group is considered valid if:</p>
     * <ul>
     *   <li>It is not null</li>
     *   <li>Its status is "ACTIVE"</li>
     * </ul>
     * 
     * <p>This is a convenience method that combines null checking and active status checking.</p>
     * 
     * @param group the VirtualGroup entity to check
     * @return true if the group is valid (not null and active), false otherwise
     */
    public boolean isValid(VirtualGroup group) {
        if (group == null) {
            return false;
        }
        
        return isActive(group);
    }
    
    /**
     * Checks if a virtual group is active.
     * 
     * <p>A virtual group is active if its status field equals "ACTIVE".
     * Inactive groups may have status values like "DISABLED", "DELETED", etc.</p>
     * 
     * @param group the VirtualGroup entity to check
     * @return true if the group's status is "ACTIVE", false otherwise
     */
    public boolean isActive(VirtualGroup group) {
        if (group == null) {
            return false;
        }
        
        return "ACTIVE".equals(group.getStatus());
    }
    
    /**
     * Gets the member count for a virtual group.
     * 
     * <p>This method queries the VirtualGroupMember repository to count how many users
     * are members of the specified virtual group. The count includes all members regardless
     * of their user status.</p>
     * 
     * @param virtualGroupId the ID of the virtual group
     * @return the number of members in the group, 0 if the group has no members or doesn't exist
     */
    public long getMemberCount(String virtualGroupId) {
        if (virtualGroupId == null) {
            log.warn("Attempted to get member count for null virtualGroupId");
            return 0;
        }
        
        log.debug("Getting member count for virtual group: {}", virtualGroupId);
        return memberRepository.countByGroupId(virtualGroupId);
    }
    
    /**
     * Gets all members of a virtual group.
     * 
     * <p>This method retrieves all User entities that are members of the specified virtual group.
     * It uses ID-based queries to fetch the members and then explicitly fetches the User entities.</p>
     * 
     * <p><strong>Note:</strong> This method performs two database queries:</p>
     * <ol>
     *   <li>Fetch all VirtualGroupMember records for the group</li>
     *   <li>Batch fetch all User entities by their IDs</li>
     * </ol>
     * 
     * @param virtualGroupId the ID of the virtual group
     * @return a list of User entities that are members of the group, empty list if no members or group doesn't exist
     */
    public List<User> getMembers(String virtualGroupId) {
        if (virtualGroupId == null) {
            log.warn("Attempted to get members for null virtualGroupId");
            return List.of();
        }
        
        log.debug("Getting members for virtual group: {}", virtualGroupId);
        
        // Fetch all member relationships
        List<VirtualGroupMember> members = memberRepository.findByGroupId(virtualGroupId);
        
        if (members.isEmpty()) {
            return List.of();
        }
        
        // Extract user IDs
        List<String> userIds = members.stream()
                .map(VirtualGroupMember::getUserId)
                .distinct()
                .collect(Collectors.toList());
        
        // Batch fetch users
        return userRepository.findAllById(userIds);
    }
    
    /**
     * Gets the VirtualGroupType enum for a VirtualGroup entity.
     * 
     * <p>This method converts the String type field from the platform-security VirtualGroup entity
     * to the type-safe VirtualGroupType enum used in admin-center business logic.</p>
     * 
     * <p>Supported types:</p>
     * <ul>
     *   <li>SYSTEM - System-defined virtual groups</li>
     *   <li>CUSTOM - User-defined virtual groups</li>
     * </ul>
     * 
     * @param group the VirtualGroup entity
     * @return the VirtualGroupType enum, or null if the group is null
     * @throws IllegalArgumentException if the group's type field contains an invalid value
     */
    public VirtualGroupType getGroupType(VirtualGroup group) {
        if (group == null) {
            return null;
        }
        
        return EntityTypeConverter.toVirtualGroupType(group.getType());
    }
    
    /**
     * Checks if a virtual group is a business group.
     * 
     * <p>Currently, this method checks if the group type is CUSTOM, as business groups
     * are typically user-defined groups associated with business operations. System groups
     * are predefined and not considered business groups.</p>
     * 
     * <p><strong>Note:</strong> The definition of "business group" may vary based on
     * business requirements. This implementation treats CUSTOM groups as business groups.</p>
     * 
     * @param group the VirtualGroup entity to check
     * @return true if the group is a business group (CUSTOM type), false otherwise
     */
    public boolean isBusinessGroup(VirtualGroup group) {
        if (group == null) {
            return false;
        }
        
        try {
            VirtualGroupType groupType = getGroupType(group);
            return groupType == VirtualGroupType.CUSTOM;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid group type for virtual group {}: {}", group.getId(), group.getType());
            return false;
        }
    }
}
