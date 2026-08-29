package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleLeaderGroup {

    private String roleId;
    private String roleName;
    private String roleCode;
    @Builder.Default
    private List<RoleLeaderUser> leaders = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleLeaderUser {
        private String userId;
        private String userName;
        private String userFullName;
    }
}
