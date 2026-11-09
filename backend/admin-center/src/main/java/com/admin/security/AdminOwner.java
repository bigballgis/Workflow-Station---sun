package com.admin.security;

/**
 * The team resource a {@link AdminAudience#TEAM_READ} / {@link AdminAudience#TEAM_WRITE} endpoint addresses by a path
 * variable; {@link AdminAccessInterceptor} asks {@link AdminTeamScope} which teams own it before the handler runs.
 */
public enum AdminOwner {

    /** No path-addressed team resource: the handler filters its list or checks its request body itself. */
    NONE,

    /** A catalog version row id ({@code sys_function_units.id}); its code's latest imported row names the team. */
    FUNCTION_UNIT_ID,

    /** A function unit code. */
    FUNCTION_UNIT_CODE,

    /** A deployment record ({@code sys_function_unit_deployments.id}); owned like the catalog row it deployed. */
    FUNCTION_UNIT_DEPLOYMENT,

    /** A relation table id; owned by the teams of the function units it is linked to, none for a Common table. */
    RELATION_TABLE_ID,

    /** An Automation flow id; owned by the team of its AP project, or Public. */
    AUTOMATION_FLOW,

    /** An Automation flow run id; owned like its flow. */
    AUTOMATION_RUN,

    /** A BI data view assignment id; owned by the team of its function unit. */
    DATA_VIEW_ASSIGNMENT
}
