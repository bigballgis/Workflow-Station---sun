package com.admin.dto.response;

import com.admin.service.FunctionUnitTeamService.TeamOption;

import java.util.List;

/**
 * What the Admin import dialog needs before the team is chosen (platform access model §8.1).
 *
 * @param packageTeam     team named in the package, with its state here; null for a package from before teams
 * @param currentTeam     team the unit already belongs to here (same code); null for a new unit or one without a team
 * @param selectableTeams teams the caller may import into
 */
public record FunctionUnitImportPreview(String code, String name, String version, TeamOption packageTeam,
                                        TeamOption currentTeam, List<TeamOption> selectableTeams) {
}
