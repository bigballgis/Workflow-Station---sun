package com.admin.component;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.List;

/** Protected assignment sources apply live organization status without changing legacy queries. */
@Component
public class AssignmentActiveCandidates {
    private final JdbcTemplate jdbc;
    public AssignmentActiveCandidates(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public List<String> resolve(Request request) {
        return switch(request.kind()) {
            case "BU_ROLE" -> jdbc.queryForList("""
                    SELECT DISTINCT ur.user_id FROM sys_user_business_unit_roles ur
                    JOIN sys_business_units bu ON bu.id=ur.business_unit_id AND bu.status='ACTIVE'
                    JOIN sys_roles r ON r.id=ur.role_id AND r.status='ACTIVE' AND r.type='BU_BOUNDED'
                    WHERE bu.code=? AND r.code=? ORDER BY ur.user_id
                    """,String.class,required(request.buCode()),required(request.roleCode()));
            case "ROLE" -> jdbc.queryForList("""
                    SELECT DISTINCT m.user_id FROM sys_virtual_group_members m
                    JOIN sys_virtual_groups g ON g.id=m.group_id AND g.status='ACTIVE'
                    JOIN sys_virtual_group_roles vr ON vr.virtual_group_id=g.id
                    JOIN sys_roles r ON r.id=vr.role_id AND r.status='ACTIVE' AND r.type='BU_UNBOUNDED'
                    WHERE r.code=? ORDER BY m.user_id
                    """,String.class,required(request.roleCode()));
            case "VIRTUAL_GROUP" -> jdbc.queryForList("""
                    SELECT DISTINCT m.user_id FROM sys_virtual_group_members m
                    JOIN sys_virtual_groups g ON g.id=m.group_id AND g.status='ACTIVE'
                    WHERE g.code=? ORDER BY m.user_id
                    """,String.class,required(request.groupCode()));
            default -> throw new IllegalArgumentException("Unsupported protected assignment source");
        };
    }
    private String required(String code) {
        if(code==null || code.isBlank()) throw new IllegalArgumentException("Missing protected assignment source code");
        return code;
    }
    public record Request(@jakarta.validation.constraints.NotBlank String kind,String buCode,String roleCode,String groupCode) { }
}
