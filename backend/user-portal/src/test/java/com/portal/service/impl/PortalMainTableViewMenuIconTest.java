package com.portal.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.component.ComputedFieldRecalculator;
import com.portal.component.FunctionUnitAccessComponent;
import com.portal.component.MainTableViewAccessResolver;
import com.portal.component.MainTableViewInvolvementChecker;
import com.portal.component.ProcessComponent;
import com.portal.dto.MainTableViewPortalDtos.FunctionUnitViewMenuItem;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortalMainTableViewMenuIconTest {

    private static final String USER_ID = "user-dev";
    private static final String FU_CODE = "fu-atm";

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private FunctionUnitAccessComponent functionUnitAccessComponent;
    @Mock
    private MainTableViewAccessResolver accessResolver;

    private PortalMainTableViewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PortalMainTableViewServiceImpl(
                jdbcTemplate,
                new ObjectMapper(),
                functionUnitAccessComponent,
                accessResolver,
                org.mockito.Mockito.mock(MainTableViewInvolvementChecker.class),
                org.mockito.Mockito.mock(ProcessInstanceRepository.class),
                org.mockito.Mockito.mock(ProcessComponent.class),
                org.mockito.Mockito.mock(ComputedFieldRecalculator.class));
    }

    @Test
    void shouldNotQueryIcons_whenNoPublishedViews() {
        when(jdbcTemplate.queryForList(contains("v.status = 'PUBLISHED'"))).thenReturn(List.of());

        assertThat(service.listAccessibleFunctionUnits(USER_ID)).isEmpty();
        verify(jdbcTemplate, never()).queryForList(contains("ic.svg_content"), any(Object.class));
    }

    @Test
    void shouldAttachIconSvg_whenIconRowPresent() {
        stubPublishedFuAndAccess();
        when(jdbcTemplate.queryForList(contains("ic.svg_content"), eq(FU_CODE)))
                .thenReturn(List.of(Map.of("fu_code", FU_CODE, "icon_svg", "<svg></svg>")));

        List<FunctionUnitViewMenuItem> items = service.listAccessibleFunctionUnits(USER_ID);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).functionUnitCode()).isEqualTo(FU_CODE);
        assertThat(items.get(0).iconSvg()).isEqualTo("<svg></svg>");
    }

    @Test
    void shouldOmitIconSvg_whenIconQueryFails() {
        stubPublishedFuAndAccess();
        when(jdbcTemplate.queryForList(contains("ic.svg_content"), eq(FU_CODE)))
                .thenThrow(new DataAccessResourceFailureException("icons down"));

        List<FunctionUnitViewMenuItem> items = service.listAccessibleFunctionUnits(USER_ID);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).functionUnitCode()).isEqualTo(FU_CODE);
        assertThat(items.get(0).iconSvg()).isNull();
    }

    private void stubPublishedFuAndAccess() {
        Map<String, Object> row = new HashMap<>();
        row.put("fu_id", 1L);
        row.put("fu_code", FU_CODE);
        row.put("fu_name", "ATM");
        row.put("view_id", 10L);
        when(jdbcTemplate.queryForList(contains("v.status = 'PUBLISHED'"))).thenReturn(List.of(row));
        when(functionUnitAccessComponent.canAccessFunctionUnit(USER_ID, FU_CODE)).thenReturn(true);
        when(functionUnitAccessComponent.isFunctionUnitEnabled(FU_CODE)).thenReturn(true);
        when(accessResolver.canUserSeeView(eq(USER_ID), anyList())).thenReturn(true);
    }
}
