package com.admin.component;

import com.admin.enums.RoleType;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.AdminConflictException;
import com.admin.exception.RoleNotFoundException;
import com.admin.repository.BusinessUnitRoleRepository;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserBusinessUnitRepository;
import com.admin.repository.UserBusinessUnitRoleRepository;
import com.admin.util.EntityTypeConverter;
import com.platform.common.audit.Audited;
import com.platform.security.entity.Role;
import com.platform.security.entity.UserBusinessUnitRole;
import com.platform.security.ubr.UbrMembershipType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 用户业务单元角色（UBR）分配：准入、成员、BU_BOUNDED 校验与审计。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserBusinessUnitRoleManagerComponent {

    private final UserBusinessUnitRoleRepository userBusinessUnitRoleRepository;
    private final UserBusinessUnitRepository userBusinessUnitRepository;
    private final RoleRepository roleRepository;
    private final BusinessUnitRoleRepository businessUnitRoleRepository;

    @Transactional
    @Audited(action = "UBR_ASSIGN", resourceType = "USER_BUSINESS_UNIT_ROLE", resourceId = "#userId")
    public void assign(String userId, String businessUnitId, String roleId, String operatedBy) {
        assign(userId, businessUnitId, roleId, operatedBy, UbrMembershipType.MEMBER);
    }

    @Transactional
    @Audited(action = "UBR_ASSIGN", resourceType = "USER_BUSINESS_UNIT_ROLE", resourceId = "#userId")
    public void assign(String userId, String businessUnitId, String roleId, String operatedBy, String membershipType) {
        String tier = UbrMembershipType.normalize(membershipType);
        var existing = userBusinessUnitRoleRepository
                .findByUserIdAndBusinessUnitIdAndRoleId(userId, businessUnitId, roleId);
        if (existing.isPresent()) {
            updateMembershipType(existing.get(), tier, userId, businessUnitId, roleId, operatedBy);
            return;
        }
        assertCanCreateUbr(userId, businessUnitId, roleId);
        String by = (operatedBy == null || operatedBy.isBlank()) ? "system" : operatedBy;
        UserBusinessUnitRole assignment = UserBusinessUnitRole.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .businessUnitId(businessUnitId)
                .roleId(roleId)
                .membershipType(tier)
                .createdBy(by)
                .build();
        userBusinessUnitRoleRepository.save(assignment);
        log.info("UBR assigned: userId={}, businessUnitId={}, roleId={}, membershipType={}, by={}",
                userId, businessUnitId, roleId, tier, by);
    }

    private void updateMembershipType(UserBusinessUnitRole row, String tier, String userId,
                                      String businessUnitId, String roleId, String operatedBy) {
        String current = UbrMembershipType.normalize(row.getMembershipType());
        if (current.equals(tier)) {
            throw new AdminConflictException("USER_BU_ROLE_ALREADY_EXISTS",
                    "User already has this role in the target business unit");
        }
        row.setMembershipType(tier);
        userBusinessUnitRoleRepository.save(row);
        log.info("UBR membership updated: userId={}, businessUnitId={}, roleId={}, {} -> {}, by={}",
                userId, businessUnitId, roleId, current, tier, operatedBy);
    }

    private void assertCanCreateUbr(String userId, String businessUnitId, String roleId) {
        boolean inBu = userBusinessUnitRepository.existsByUserIdAndBusinessUnitId(userId, businessUnitId);
        if (!inBu) {
            throw new AdminBusinessException("USER_NOT_IN_BUSINESS_UNIT",
                    "User is not a member of this business unit and cannot be assigned BU-bounded role");
        }
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        RoleType roleType = EntityTypeConverter.toRoleType(role.getType());
        if (roleType != RoleType.BU_BOUNDED) {
            throw new AdminBusinessException("ROLE_NOT_BU_BOUNDED",
                    "Only BU-bounded (BU_BOUNDED) roles can be assigned to business units");
        }
        boolean eligible = businessUnitRoleRepository.existsByBusinessUnitIdAndRoleId(businessUnitId, roleId);
        if (!eligible) {
            throw new AdminBusinessException("ROLE_NOT_ELIGIBLE_FOR_BUSINESS_UNIT",
                    "This role is not in the eligible roles list for this business unit");
        }
    }

    @Transactional
    @Audited(action = "UBR_REMOVE", resourceType = "USER_BUSINESS_UNIT_ROLE", resourceId = "#userId")
    public void remove(String userId, String businessUnitId, String roleId, String operatedBy) {
        var existing = userBusinessUnitRoleRepository
                .findByUserIdAndBusinessUnitIdAndRoleId(userId, businessUnitId, roleId);
        if (existing.isEmpty()) {
            log.info("UBR remove skipped, assignment not found: userId={}, businessUnitId={}, roleId={}, by={}",
                    userId, businessUnitId, roleId, operatedBy);
            return;
        }
        userBusinessUnitRoleRepository.delete(existing.get());
        userBusinessUnitRoleRepository.flush();
        log.info("UBR removed: userId={}, businessUnitId={}, roleId={}, by={}",
                userId, businessUnitId, roleId, operatedBy);
        leaveBusinessUnitIfNoRolesRemain(userId, businessUnitId, operatedBy);
    }

    private void leaveBusinessUnitIfNoRolesRemain(String userId, String businessUnitId, String operatedBy) {
        if (userBusinessUnitRoleRepository.existsByUserIdAndBusinessUnitId(userId, businessUnitId)) {
            return;
        }
        userBusinessUnitRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId).ifPresent(membership -> {
            userBusinessUnitRepository.delete(membership);
            log.info("Left business unit after last UBR removed: userId={}, businessUnitId={}, by={}",
                    userId, businessUnitId, operatedBy);
        });
    }
}
