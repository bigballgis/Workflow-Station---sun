package com.admin.servicetask;

import com.admin.servicetask.config.ServiceTaskProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理面列表的「project → workspace」反查片段。
 *
 * <p>钉住两点：前缀与 Public externalId <b>走绑定参数</b>（不是拼进 SQL 的常量，否则改配置就漂移），
 * 且 substr 的起点与前缀长度一致——差一位会把组 id 截断成查不到的值，列表会静悄悄回落成
 * project 显示名而不是报错。</p>
 */
class ApWorkspaceSqlTest {

    @Test
    void joinBindsPublicExternalIdAndTeamPrefixAsParameters() {
        ApWorkspaceSql sql = new ApWorkspaceSql(new ServiceTaskProperties());

        assertEquals(4, sql.joinClause().chars().filter(c -> c == '?').count());
        assertEquals(List.of("hermes-main", "vg-dev-public", "hermes-dg-%", 11), sql.joinParams());
        assertTrue(sql.joinClause().contains("sys_virtual_groups vg"));
    }

    /** 前缀是配置项：改了它，substr 起点必须跟着走。 */
    @Test
    void substrOffsetFollowsTheConfiguredPrefixLength() {
        ServiceTaskProperties properties = new ServiceTaskProperties();
        properties.getManaged().setTeamProjectExternalIdPrefix("ws-");
        properties.getManaged().setProjectExternalId("shared-main");
        properties.getManaged().setPublicGroupId("vg-shared");

        assertEquals(List.of("shared-main", "vg-shared", "ws-%", 4),
                new ApWorkspaceSql(properties).joinParams());
    }
}
