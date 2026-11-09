package com.admin.service;

import com.admin.exception.AdminBusinessException;
import com.admin.exception.FunctionUnitTeamForbiddenException;
import com.admin.repository.FunctionUnitRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.platform.common.i18n.I18nService;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import com.platform.security.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The team a catalog function unit belongs to, and who may import or deploy it there (platform access model §8.1).
 *
 * <p>A team is a DEVELOPER virtual group, matched across environments by its code. The team of a function unit is
 * the team of its latest imported version row; rows imported before teams existed carry none, and only a
 * System Administrator manages those until they are imported again (C-5).</p>
 */
@Service
@RequiredArgsConstructor
public class FunctionUnitTeamService {

    /** How the package arrived: a DW deployment (team from the package) or the Admin import dialog (team chosen). */
    public enum ImportChannel { DEPLOYMENT, MANUAL }

    /** Team named in an export package ({@code manifest.json} → {@code team}). */
    public record PackageTeam(String code, String name) {
    }

    public enum TeamState { ACTIVE, INACTIVE, MISSING }

    /** A team as the import dialog shows it; {@code name} is the package's name when the team is missing here. */
    public record TeamOption(String code, String name, TeamState state) {
    }

    private static final String TEAM_TYPE = "DEVELOPER";
    private static final String ROLE_SYS_ADMIN = "SYS_ADMIN";
    /** Lists that may deploy a team's units: both, as in DW (§6.2). */
    private static final List<String> DEPLOY_LISTS =
            List.of(VirtualGroupMember.LIST_TECH_LEAD, VirtualGroupMember.LIST_DEVELOPER);
    /** The Admin import dialog is a Technical Lead's (§8.1). */
    private static final List<String> LEAD_LIST = List.of(VirtualGroupMember.LIST_TECH_LEAD);

    private final FunctionUnitRepository functionUnitRepository;
    private final VirtualGroupRepository virtualGroupRepository;
    private final VirtualGroupMemberRepository memberRepository;
    private final UserRoleService userRoleService;
    private final I18nService i18nService;

    /** Team code of a function unit code; empty when its latest imported row has none. */
    public Optional<String> teamOf(String functionUnitCode) {
        return functionUnitRepository.findTeamCodeOfLatestImport(functionUnitCode);
    }

    /**
     * The team an import lands in, once the operator is allowed to put it there.
     *
     * <ul>
     *   <li>A team named in the package must exist and be active here, whatever the channel and the choice (D-23).</li>
     *   <li>A DW deployment lands in the package's team; the import dialog lands in the chosen team.</li>
     *   <li>The operator deploys in that team (dialog: is its Technical Lead). A new version of a unit that belongs to
     *       another team also needs the same right there (§8.1, D-34).</li>
     * </ul>
     *
     * @param existingTeamCode team of the unit already in this catalog under the same code; null for a new unit or one
     *                         imported before teams
     */
    public String resolveImportTeam(ImportChannel channel, PackageTeam packageTeam, String chosenTeamCode,
                                    String existingTeamCode, String operatorId) {
        VirtualGroup packaged = packageTeam == null ? null : requirePackageTeam(packageTeam);
        VirtualGroup target = switch (channel) {
            case DEPLOYMENT -> {
                if (packaged == null) {
                    throw new AdminBusinessException("FU_IMPORT_TEAM_REQUIRED",
                            i18nService.getMessage("admin.fu.import_team_required_deploy"));
                }
                yield packaged;
            }
            case MANUAL -> requireChosenTeam(chosenTeamCode);
        };
        if (isSysAdmin(operatorId)) {
            return target.getCode();
        }
        List<String> lists = channel == ImportChannel.MANUAL ? LEAD_LIST : DEPLOY_LISTS;
        if (!onList(target, operatorId, lists)) {
            throw new FunctionUnitTeamForbiddenException(i18nService.getMessage(
                    channel == ImportChannel.MANUAL ? "admin.fu.import_team_lead_only" : "admin.fu.team_deploy_forbidden",
                    target.getName(), target.getCode()));
        }
        if (existingTeamCode != null && !existingTeamCode.equals(target.getCode())) {
            VirtualGroup owner = usableTeam(existingTeamCode);
            if (!onList(owner, operatorId, lists)) {
                throw new FunctionUnitTeamForbiddenException(i18nService.getMessage("admin.fu.import_owner_forbidden",
                        owner.getName(), owner.getCode()));
            }
        }
        return target.getCode();
    }

    /**
     * Validate / deploy of an imported unit: the operator deploys in the unit's team (§8.1). A unit without a team, or
     * whose team is gone or disabled, is a System Administrator's.
     */
    public void assertMayDeploy(String functionUnitCode, String operatorId) {
        if (isSysAdmin(operatorId)) {
            return;
        }
        String teamCode = teamOf(functionUnitCode).orElseThrow(() -> new FunctionUnitTeamForbiddenException(
                i18nService.getMessage("admin.fu.deploy_without_team")));
        VirtualGroup team = usableTeam(teamCode);
        if (!onList(team, operatorId, DEPLOY_LISTS)) {
            throw new FunctionUnitTeamForbiddenException(
                    i18nService.getMessage("admin.fu.team_deploy_forbidden", team.getName(), team.getCode()));
        }
    }

    /** A team as this environment has it; the package's name stands in when the team does not exist here. */
    public TeamOption describe(String code, String fallbackName) {
        Optional<VirtualGroup> team = virtualGroupRepository.findByCode(code)
                .filter(group -> TEAM_TYPE.equals(group.getType()));
        if (team.isEmpty()) {
            return new TeamOption(code, fallbackName, TeamState.MISSING);
        }
        return new TeamOption(code, team.get().getName(), team.get().isActive() ? TeamState.ACTIVE : TeamState.INACTIVE);
    }

    /** Teams the operator may choose in the import dialog: every active team for a System Administrator, else the
     *  active teams they lead. */
    public List<TeamOption> selectableTeams(String operatorId) {
        List<VirtualGroup> active = virtualGroupRepository.findByTypeAndStatus(TEAM_TYPE, "ACTIVE");
        if (!isSysAdmin(operatorId)) {
            active = active.stream().filter(group -> onList(group, operatorId, LEAD_LIST)).toList();
        }
        return active.stream()
                .sorted(Comparator.comparing(VirtualGroup::getName, String.CASE_INSENSITIVE_ORDER))
                .map(group -> new TeamOption(group.getCode(), group.getName(), TeamState.ACTIVE))
                .toList();
    }

    private VirtualGroup requirePackageTeam(PackageTeam packageTeam) {
        TeamOption team = describe(packageTeam.code(), packageTeam.name());
        if (team.state() == TeamState.MISSING) {
            throw new AdminBusinessException("FU_IMPORT_TEAM_NOT_FOUND",
                    i18nService.getMessage("admin.fu.import_team_not_found", team.name(), team.code()));
        }
        if (team.state() == TeamState.INACTIVE) {
            throw new AdminBusinessException("FU_IMPORT_TEAM_INACTIVE",
                    i18nService.getMessage("admin.fu.import_team_inactive", team.name(), team.code()));
        }
        return activeTeam(packageTeam.code()).orElseThrow();
    }

    private VirtualGroup requireChosenTeam(String chosenTeamCode) {
        if (chosenTeamCode == null || chosenTeamCode.isBlank()) {
            throw new AdminBusinessException("FU_IMPORT_TEAM_REQUIRED",
                    i18nService.getMessage("admin.fu.import_team_required"));
        }
        return activeTeam(chosenTeamCode).orElseThrow(() -> new AdminBusinessException("FU_IMPORT_TEAM_INVALID",
                i18nService.getMessage("admin.fu.import_team_invalid", chosenTeamCode)));
    }

    /**
     * The unit's current team, for someone other than a System Administrator: a disabled or deleted team leaves the
     * unit to System Administrators (D-09, D-33), and the refusal says which case it is.
     */
    private VirtualGroup usableTeam(String code) {
        Optional<VirtualGroup> team = virtualGroupRepository.findByCode(code)
                .filter(group -> TEAM_TYPE.equals(group.getType()));
        if (team.isEmpty()) {
            throw new FunctionUnitTeamForbiddenException(i18nService.getMessage("admin.fu.team_deleted_admin_only", code));
        }
        if (!team.get().isActive()) {
            throw new FunctionUnitTeamForbiddenException(i18nService.getMessage("admin.fu.team_inactive_admin_only",
                    team.get().getName(), code));
        }
        return team.get();
    }

    private Optional<VirtualGroup> activeTeam(String code) {
        return virtualGroupRepository.findByCode(code)
                .filter(group -> TEAM_TYPE.equals(group.getType()) && group.isActive());
    }

    private boolean onList(VirtualGroup team, String userId, List<String> lists) {
        return memberRepository.isActiveUserOnLists(team.getId(), userId, lists);
    }

    private boolean isSysAdmin(String userId) {
        return userRoleService.hasRole(userId, ROLE_SYS_ADMIN);
    }
}
