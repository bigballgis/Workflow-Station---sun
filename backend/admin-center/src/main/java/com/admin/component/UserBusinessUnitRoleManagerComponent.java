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
        if (userBusinessUnitRoleRepository.existsByUserIdAndBusinessUnitIdAndRoleId(userId, businessUnitId, roleId)) {
            throw new AdminConflictException("USER_BU_ROLE_ALREADY_EXISTS", "该用户在目标业务单元下已拥有该角色");
        }
        boolean inBu = userBusinessUnitRepository.existsByUserIdAndBusinessUnitId(userId, businessUnitId);
        if (!inBu) {
            throw new AdminBusinessException("USER_NOT_IN_BUSINESS_UNIT", "用户未加入该业务单元，无法分配 BU 绑定型角色");
        }
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        RoleType roleType = EntityTypeConverter.toRoleType(role.getType());
        if (roleType != RoleType.BU_BOUNDED) {
            throw new AdminBusinessException("ROLE_NOT_BU_BOUNDED", "仅可为业务单元分配 BU 绑定型（BU_BOUNDED）角色");
        }
        boolean eligible = businessUnitRoleRepository.existsByBusinessUnitIdAndRoleId(businessUnitId, roleId);
        if (!eligible) {
            throw new AdminBusinessException("ROLE_NOT_ELIGIBLE_FOR_BUSINESS_UNIT", "该角色不在业务单元的准入角色列表中");
        }

        String by = (operatedBy == null || operatedBy.isBlank()) ? "system" : operatedBy;
        UserBusinessUnitRole assignment = UserBusinessUnitRole.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .businessUnitId(businessUnitId)
                .roleId(roleId)
                .createdBy(by)
                .build();
        userBusinessUnitRoleRepository.save(assignment);
        log.info("UBR assigned: userId={}, businessUnitId={}, roleId={}, by={}", userId, businessUnitId, roleId, by);
    }

    @Transactional
    @Audited(action = "UBR_REMOVE", resourceType = "USER_BUSINESS_UNIT_ROLE", resourceId = "#userId")
    public void remove(String userId, String businessUnitId, String roleId, String operatedBy) {
        userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitIdAndRoleId(userId, businessUnitId, roleId)
                .ifPresent(userBusinessUnitRoleRepository::delete);
        log.info("UBR removed if present: userId={}, businessUnitId={}, roleId={}, by={}",
                userId, businessUnitId, roleId, operatedBy);
    }
}
