package com.admin.component;

import com.admin.dto.request.VirtualGroupCreateRequest;
import com.admin.dto.request.VirtualGroupMemberRequest;
import com.admin.dto.response.VirtualGroupMemberInfo;
import com.admin.enums.VirtualGroupType;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserBusinessUnitRepository;
import com.admin.repository.UserRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.repository.VirtualGroupRoleRepository;
import com.platform.common.dto.UserPrincipal;
import com.platform.common.i18n.I18nService;
import com.platform.security.entity.User;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import com.platform.security.model.UserStatus;
import com.platform.security.service.UserRoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Admin virtual group rules of the platform access model: team codes come from names and teams keep their
 * name (§5.4), SYSTEM groups and Public are protected (§5.2, §6.4), members are kept per list and source and
 * revoking a manual member keeps the AD one (§5.5), System Administrators keeps an active member (AC-12), and only a
 * System Administrator changes the members of a SYSTEM group or a team — a Portal approver reaches addMember through a
 * service call and may only change a CUSTOM group.
 */
class VirtualGroupRulesTest {

    private final VirtualGroupRepository groups = mock(VirtualGroupRepository.class);
    private final VirtualGroupMemberRepository members = mock(VirtualGroupMemberRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UserRoleService userRoleService = mock(UserRoleService.class);
    private final List<VirtualGroupMember> rows = new ArrayList<>();
    private final List<User> people = new ArrayList<>();
    private VirtualGroupManagerComponent component;

    @BeforeEach
    void setUp() {
        I18nService i18n = mock(I18nService.class);
        lenient().when(i18n.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(i18n.getMessage(anyString(), any(Object[].class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(groups.save(any(VirtualGroup.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(members.findByGroupId(anyString())).thenAnswer(inv ->
                rows.stream().filter(r -> r.getGroupId().equals(inv.getArgument(0))).toList());
        lenient().when(members.findByGroupIdAndSource(anyString(), anyString())).thenAnswer(inv ->
                rows.stream().filter(r -> r.getGroupId().equals(inv.getArgument(0))
                        && r.getSource().equals(inv.getArgument(1))).toList());
        lenient().when(members.save(any(VirtualGroupMember.class))).thenAnswer(inv -> {
            rows.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        lenient().doAnswer(inv -> rows.remove((VirtualGroupMember) inv.getArgument(0)))
                .when(members).delete(any(VirtualGroupMember.class));
        lenient().when(users.findAllById(any())).thenAnswer(inv -> {
            Iterable<String> ids = inv.getArgument(0);
            Set<String> wanted = new java.util.HashSet<>();
            ids.forEach(wanted::add);
            return people.stream().filter(u -> wanted.contains(u.getId())).toList();
        });
        component = new VirtualGroupManagerComponent(groups, members, mock(VirtualGroupRoleRepository.class),
                mock(RoleRepository.class), users, jdbc, mock(UserBusinessUnitRepository.class), i18n, userRoleService);
        lenient().when(userRoleService.hasRole("admin", "SYS_ADMIN")).thenReturn(true);
        actAs("admin");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---------- create / update ----------

    @Test
    void aTeamCodeIsMadeFromItsName() {
        component.createVirtualGroup(request("Payment Ops", VirtualGroupType.DEVELOPER));

        verify(groups).save(org.mockito.ArgumentMatchers.argThat(g -> "PAYMENT_OPS".equals(g.getCode())));
    }

    @Test
    void aTeamCodeCannotBeTyped() {
        VirtualGroupCreateRequest request = request("Payment Ops", VirtualGroupType.DEVELOPER);
        request.setCode("PAY");

        assertError(() -> component.createVirtualGroup(request), "TEAM_CODE_FROM_NAME");
    }

    @Test
    void aNameWithoutLettersOrDigitsIsRefused() {
        assertError(() -> component.createVirtualGroup(request("财务团队", VirtualGroupType.DEVELOPER)),
                "VIRTUAL_GROUP_CODE_EMPTY");
    }

    @Test
    void aTeamNameThatGivesAnExistingCodeIsRefused() {
        when(groups.existsByCode("PAY_OPS")).thenReturn(true);

        assertError(() -> component.createVirtualGroup(request("Pay-Ops", VirtualGroupType.DEVELOPER)), "CODE_EXISTS");
    }

    @Test
    void onlyATeamHasATechnicalLeadAdGroup() {
        VirtualGroupCreateRequest request = request("Finance", VirtualGroupType.CUSTOM);
        request.setTechLeadAdGroup("AD-TL");

        assertError(() -> component.createVirtualGroup(request), "TECH_LEAD_AD_GROUP_TEAM_ONLY");
    }

    @Test
    void aTeamKeepsItsNameAndSetsBothAdGroups() {
        VirtualGroup team = group("vg-team", "PAYMENT_OPS", "Payment Ops", "DEVELOPER");
        VirtualGroupCreateRequest rename = request("Payments", VirtualGroupType.DEVELOPER);
        assertError(() -> component.updateVirtualGroup("vg-team", rename), "TEAM_NAME_IMMUTABLE");

        VirtualGroupCreateRequest mappings = request("Payment Ops", VirtualGroupType.DEVELOPER);
        mappings.setAdGroup("AD-DEV");
        mappings.setTechLeadAdGroup("AD-TL");
        component.updateVirtualGroup("vg-team", mappings);

        assertThat(team.getAdGroup()).isEqualTo("AD-DEV");
        assertThat(team.getTechLeadAdGroup()).isEqualTo("AD-TL");
        assertThat(team.getCode()).isEqualTo("PAYMENT_OPS");
    }

    @Test
    void aSystemGroupChangesOnlyItsAdGroup() {
        VirtualGroup admins = group("vg-sys-admins", VirtualGroup.SYSTEM_ADMINISTRATORS_CODE, "System Administrators", "SYSTEM");
        admins.setDisplayName("Platform administrators");
        VirtualGroupCreateRequest description = request("System Administrators", VirtualGroupType.SYSTEM);
        description.setDescription("Changed");
        assertError(() -> component.updateVirtualGroup("vg-sys-admins", description), "SYSTEM_GROUP_AD_GROUP_ONLY");

        VirtualGroupCreateRequest adGroup = request("System Administrators", VirtualGroupType.SYSTEM);
        adGroup.setDescription("Platform administrators");
        adGroup.setAdGroup("AD-ADMINS");
        component.updateVirtualGroup("vg-sys-admins", adGroup);

        assertThat(admins.getAdGroup()).isEqualTo("AD-ADMINS");
    }

    @Test
    void aCodeLongerThanTheColumnIsRefused() {
        assertError(() -> component.createVirtualGroup(request("A".repeat(51), VirtualGroupType.DEVELOPER)),
                "VIRTUAL_GROUP_CODE_TOO_LONG");
    }

    @Test
    void publicHasNoAdGroup() {
        group(VirtualGroup.PUBLIC_TEAM_ID, "DEV_TEAM_PUBLIC", "Public", "DEVELOPER");
        VirtualGroupCreateRequest request = request("Public", VirtualGroupType.DEVELOPER);
        request.setAdGroup("AD-EVERYONE");

        assertError(() -> component.updateVirtualGroup(VirtualGroup.PUBLIC_TEAM_ID, request), "PUBLIC_TEAM_HAS_NO_LISTS");
    }

    // ---------- disable / delete ----------

    @Test
    void systemGroupsAndPublicCannotBeDisabled() {
        group("vg-sys-admins", VirtualGroup.SYSTEM_ADMINISTRATORS_CODE, "System Administrators", "SYSTEM");
        group(VirtualGroup.PUBLIC_TEAM_ID, "DEV_TEAM_PUBLIC", "Public", "DEVELOPER");

        assertError(() -> component.deactivateGroup("vg-sys-admins"), "GROUP_CANNOT_BE_DEACTIVATED");
        assertError(() -> component.deactivateGroup(VirtualGroup.PUBLIC_TEAM_ID), "GROUP_CANNOT_BE_DEACTIVATED");
    }

    @Test
    void publicAndATeamWithFunctionUnitsAreNotDeleted() {
        group(VirtualGroup.PUBLIC_TEAM_ID, "DEV_TEAM_PUBLIC", "Public", "DEVELOPER");
        VirtualGroup team = group("vg-team", "PAYMENT_OPS", "Payment Ops", "DEVELOPER");
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("PAYMENT_OPS"))).thenReturn(2L);

        assertError(() -> component.deleteVirtualGroup(VirtualGroup.PUBLIC_TEAM_ID), "PUBLIC_TEAM_CANNOT_DELETE");
        assertError(() -> component.deleteVirtualGroup("vg-team"), "TEAM_HAS_FUNCTION_UNITS");
        verify(groups, never()).delete(team);
    }

    @Test
    void aTeamWithoutFunctionUnitsIsDeleted() {
        VirtualGroup team = group("vg-team", "PAYMENT_OPS", "Payment Ops", "DEVELOPER");
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("PAYMENT_OPS"))).thenReturn(0L);

        component.deleteVirtualGroup("vg-team");

        verify(groups).delete(team);
    }

    // ---------- members ----------

    @Test
    void aCustomGroupMemberIsAManualMember() {
        group("vg-finance", "FINANCE", "Finance", "CUSTOM");

        component.addMember("vg-finance", member("u1", null));

        assertThat(rowsOf("vg-finance")).containsExactly("u1/MEMBER/MANUAL");
    }

    @Test
    void aTeamMemberGoesOnAList() {
        group("vg-team", "PAYMENT_OPS", "Payment Ops", "DEVELOPER");

        assertError(() -> component.addMember("vg-team", member("u1", null)), "TEAM_LIST_REQUIRED");
        component.addMember("vg-team", member("u1", "TECH_LEAD"));
        component.addMember("vg-team", member("u1", "DEVELOPER"));

        assertThat(rowsOf("vg-team")).containsExactly("u1/TECH_LEAD/MANUAL", "u1/DEVELOPER/MANUAL");
    }

    @Test
    void publicTakesNoMember() {
        group(VirtualGroup.PUBLIC_TEAM_ID, "DEV_TEAM_PUBLIC", "Public", "DEVELOPER");

        assertError(() -> component.addMember(VirtualGroup.PUBLIC_TEAM_ID, member("u1", "DEVELOPER")),
                "PUBLIC_TEAM_HAS_NO_LISTS");
    }

    @Test
    void anAdMemberCanAlsoBeAddedByHandButOnlyOnce() {
        group("vg-finance", "FINANCE", "Finance", "CUSTOM");
        row("vg-finance", "u1", "MEMBER", "AD");

        component.addMember("vg-finance", member("u1", "MEMBER"));
        assertError(() -> component.addMember("vg-finance", member("u1", "MEMBER")), "MEMBER_EXISTS");

        assertThat(rowsOf("vg-finance")).containsExactly("u1/MEMBER/AD", "u1/MEMBER/MANUAL");
    }

    @Test
    void revokingTheManualMemberKeepsTheAdOne() {
        group("vg-finance", "FINANCE", "Finance", "CUSTOM");
        row("vg-finance", "u1", "MEMBER", "AD");
        row("vg-finance", "u1", "MEMBER", "MANUAL");

        component.removeMember("vg-finance", "u1", null);

        assertThat(rowsOf("vg-finance")).containsExactly("u1/MEMBER/AD");
        assertError(() -> component.removeMember("vg-finance", "u1", null), "MANUAL_MEMBER_NOT_FOUND");
    }

    @Test
    void aPortalApproverOnlyChangesCustomGroups() {
        group("vg-finance", "FINANCE", "Finance", "CUSTOM");
        group("vg-sys-admins", VirtualGroup.SYSTEM_ADMINISTRATORS_CODE, "System Administrators", "SYSTEM");
        group("vg-team", "PAYMENT_OPS", "Payment Ops", "DEVELOPER");
        row("vg-team", "u2", "DEVELOPER", "MANUAL");
        actAs("approver");

        component.addMember("vg-finance", member("u1", null));
        assertError(() -> component.addMember("vg-sys-admins", member("u1", null)), "MEMBERS_SYS_ADMIN_ONLY");
        assertError(() -> component.addMember("vg-team", member("u1", "DEVELOPER")), "MEMBERS_SYS_ADMIN_ONLY");
        assertError(() -> component.removeMember("vg-team", "u2", "DEVELOPER"), "MEMBERS_SYS_ADMIN_ONLY");

        assertThat(rowsOf("vg-finance")).containsExactly("u1/MEMBER/MANUAL");
        assertThat(rowsOf("vg-sys-admins")).isEmpty();
        assertThat(rowsOf("vg-team")).containsExactly("u2/DEVELOPER/MANUAL");
    }

    @Test
    void theLastActiveAdministratorIsKept() {
        group("vg-sys-admins", VirtualGroup.SYSTEM_ADMINISTRATORS_CODE, "System Administrators", "SYSTEM");
        row("vg-sys-admins", "u1", "MEMBER", "MANUAL");
        row("vg-sys-admins", "u2", "MEMBER", "AD");
        person("u1", UserStatus.ACTIVE);
        person("u2", UserStatus.LOCKED);

        assertError(() -> component.removeMember("vg-sys-admins", "u1", null), "LAST_ADMIN_CANNOT_REMOVE");

        person("u3", UserStatus.ACTIVE);
        row("vg-sys-admins", "u3", "MEMBER", "AD");
        component.removeMember("vg-sys-admins", "u1", null);

        assertThat(rowsOf("vg-sys-admins")).containsExactly("u2/MEMBER/AD", "u3/MEMBER/AD");
    }

    @Test
    void theMemberListShowsEachPersonOncePerListWithItsSources() {
        group("vg-team", "PAYMENT_OPS", "Payment Ops", "DEVELOPER");
        row("vg-team", "u1", "DEVELOPER", "AD");
        row("vg-team", "u1", "DEVELOPER", "MANUAL");
        row("vg-team", "u1", "TECH_LEAD", "MANUAL");
        row("vg-team", "u2", "DEVELOPER", "AD");

        List<VirtualGroupMemberInfo> list = component.getGroupMembers("vg-team");

        assertThat(list).extracting(m -> m.getUserId() + "/" + m.getListType() + "/" + m.getSources())
                .containsExactly("u1/DEVELOPER/[AD, MANUAL]", "u1/TECH_LEAD/[MANUAL]", "u2/DEVELOPER/[AD]");
    }

    // ---------- helpers ----------

    private static void actAs(String userId) {
        UserPrincipal principal = UserPrincipal.builder().userId(userId).username(userId).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", java.util.List.of()));
    }

    private VirtualGroup group(String id, String code, String name, String type) {
        VirtualGroup group = VirtualGroup.builder().id(id).code(code).name(name).type(type).status("ACTIVE").build();
        lenient().when(groups.findById(id)).thenReturn(Optional.of(group));
        lenient().when(groups.existsById(id)).thenReturn(true);
        return group;
    }

    private void row(String groupId, String userId, String listType, String source) {
        rows.add(VirtualGroupMember.builder().id(UUID.randomUUID().toString()).groupId(groupId).userId(userId)
                .listType(listType).source(source).build());
    }

    private void person(String id, UserStatus status) {
        people.add(User.builder().id(id).username(id).status(status).deleted(false).build());
    }

    private List<String> rowsOf(String groupId) {
        return rows.stream().filter(r -> r.getGroupId().equals(groupId))
                .map(r -> r.getUserId() + "/" + r.getListType() + "/" + r.getSource()).toList();
    }

    private static VirtualGroupCreateRequest request(String name, VirtualGroupType type) {
        return VirtualGroupCreateRequest.builder().name(name).type(type).build();
    }

    private static VirtualGroupMemberRequest member(String userId, String listType) {
        return VirtualGroupMemberRequest.builder().userId(userId).listType(listType).build();
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOf(AdminBusinessException.class).extracting("errorCode").isEqualTo(code);
    }
}
