package com.developer.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VirtualGroupMembershipDaoTest {
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void selectableTeamsIncludeOnlyDeveloperVirtualGroups() {
        VirtualGroupMembershipDao dao = new VirtualGroupMembershipDao(jdbcTemplate);

        dao.findSelectableTeamsByUserId("user-1", "public-group");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq("user-1"), eq("public-group"));
        assertTrue(sql.getValue().contains("g.type = 'DEVELOPER'"));
        assertFalse(sql.getValue().contains("CUSTOM"));
    }

    @Test
    void allSelectableTeamsIncludeOnlyDeveloperVirtualGroups() {
        VirtualGroupMembershipDao dao = new VirtualGroupMembershipDao(jdbcTemplate);

        dao.findAllSelectableTeams("public-group");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq("public-group"));
        assertTrue(sql.getValue().contains("g.type = 'DEVELOPER'"));
        assertFalse(sql.getValue().contains("CUSTOM"));
    }
}
