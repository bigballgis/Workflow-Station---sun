package com.admin.service;

import com.admin.entity.Approver;
import com.admin.enums.ApproverTargetType;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.ApproverRepository;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.UserRepository;
import com.admin.repository.VirtualGroupRepository;
import com.platform.common.i18n.I18nService;
import com.platform.security.entity.User;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Approvers admit people to Portal access groups only (platform access model §5.3): a SYSTEM group or a team cannot
 * get an approver, since its members are kept by System Administrators (§5.2, §5.5).
 */
class ApproverRulesTest {

    private final ApproverRepository approvers = mock(ApproverRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final VirtualGroupRepository groups = mock(VirtualGroupRepository.class);
    private ApproverService service;

    @BeforeEach
    void setUp() {
        I18nService i18n = mock(I18nService.class);
        when(i18n.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        service = new ApproverService(approvers, users, groups, mock(BusinessUnitRepository.class), i18n);
        when(users.findById("u1")).thenReturn(Optional.of(
                User.builder().id("u1").username("u1").status(UserStatus.ACTIVE).build()));
        group("vg-finance", "CUSTOM");
        group("vg-sys-admins", "SYSTEM");
        group("vg-team", "DEVELOPER");
    }

    @Test
    void aCustomGroupGetsAnApprover() {
        service.addApprover(ApproverTargetType.VIRTUAL_GROUP, "vg-finance", "u1");

        verify(approvers).save(any(Approver.class));
    }

    @Test
    void systemGroupsAndTeamsGetNoApprover() {
        for (String groupId : new String[] {"vg-sys-admins", "vg-team"}) {
            assertThatThrownBy(() -> service.addApprover(ApproverTargetType.VIRTUAL_GROUP, groupId, "u1"))
                    .isInstanceOf(AdminBusinessException.class)
                    .extracting("errorCode").isEqualTo("APPROVER_CUSTOM_GROUP_ONLY");
        }
        verify(approvers, never()).save(any());
    }

    private void group(String id, String type) {
        when(groups.findById(id)).thenReturn(Optional.of(VirtualGroup.builder().id(id).code(id).name(id).type(type).build()));
    }
}
