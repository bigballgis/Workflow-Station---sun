package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dataview.RuntimeDataView;
import com.portal.client.PortalBiClient;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.UserDisplayNameResolver;
import com.portal.service.impl.PortalMainTableViewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import com.portal.dto.MainTableViewQueryRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real list predicates and process-detail policy; only Admin metadata and external identities are mocked. */
@Testcontainers
class ProcessViewDetailScopePostgresTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");
    private final ObjectMapper mapper = new ObjectMapper();
    private PortalBiClient client;
    private MainTableViewAccessResolver admins;
    private FunctionUnitAccessComponent requestAccess;
    private MainTableViewInvolvementScope involvement;
    private ProcessInstanceRepository instances;
    private ProcessApplicationQueryComponent detail;
    private PortalMainTableViewServiceImpl service;
    private JdbcTemplate jdbc;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()));
        jdbc.execute("DROP TABLE IF EXISTS up_process_instance");
        jdbc.execute("CREATE TABLE up_process_instance(id TEXT PRIMARY KEY,function_unit_code TEXT,"
                + "start_user_id TEXT,variables JSONB,status TEXT,start_time TIMESTAMP,start_user_name TEXT,"
                + "function_unit_catalog_id TEXT,function_unit_version_label TEXT,process_definition_id TEXT,current_node TEXT)");
        client = mock(PortalBiClient.class);
        admins = mock(MainTableViewAccessResolver.class);
        requestAccess = mock(FunctionUnitAccessComponent.class);
        involvement = mock(MainTableViewInvolvementScope.class);
        instances = mock(ProcessInstanceRepository.class);
        service = new PortalMainTableViewServiceImpl(client, mapper, involvement,
                new MainTableViewRowQueryComponent(jdbc, mapper), new MainTableViewSubRowQueryComponent(jdbc, mapper), mock(UserDisplayNameResolver.class));
        detail = new ProcessApplicationQueryComponent(instances, mock(WorkflowEngineClient.class),
                mock(EngineSubTableHydrator.class), mock(UserDisplayNameResolver.class), mock(MiOverlayComponent.class),
                mock(SubTableEnrichmentComponent.class), mock(MainTableViewInvolvementChecker.class), admins,
                requestAccess, jdbc, mock(MiOuterStepResolver.class));
        ReflectionTestUtils.setField(detail, "dataViewClient", client);
        ReflectionTestUtils.setField(detail, "viewService", service);
    }

    private ProcessInstanceInfo record(String code, Map<String,Object> values) throws Exception {
        jdbc.update("INSERT INTO up_process_instance(id,function_unit_code,start_user_id,variables) VALUES('target',?,'owner',?::jsonb)", code, mapper.writeValueAsString(values));
        var instance = new ProcessInstance();
        instance.setId("target"); instance.setFunctionUnitCode(code); instance.setStartUserId("owner"); instance.setVariables(values);
        when(instances.findById("target")).thenReturn(Optional.of(instance));
        return ProcessInstanceInfo.builder().id("target").functionUnitCode(code).startUserId("owner").build();
    }

    private RuntimeDataView view(long id, String code, boolean sub, boolean restricted) {
        Map<String,Object> filter = Map.of("logic","and","conditions",List.of(
                Map.of("fieldName","state","operator","eq","value","Open")),"groups",List.of());
        Map<String,Object> config = Map.of("viewName","Open","restrictToInvolvedUsers",restricted,
                "filterConfig",filter,"fields",List.of(Map.of("fieldName","state","visible",true,"columnType","field")));
        return new RuntimeDataView(id,1L,1L,"key-"+id,"current-release",code,"FU","2.0",null,
                sub ? "case_lines" : "cases","Cases",sub ? "SUB" : "MAIN",config,
                List.of(Map.of("tableName",sub ? "case_lines" : "cases","fields",List.of(Map.of("fieldName","state","dataType","VARCHAR")))),
                List.of(),List.of(),false);
    }

    private void visible(RuntimeDataView... views) {
        when(client.getDataViews("reader")).thenReturn(List.of(views));
        for (var view : views) when(client.getDataView("reader",view.id())).thenReturn(view);
    }

    @ParameterizedTest @ValueSource(strings={"native-fu","imported-fu"})
    void aFilteredOutMainRecordCannotGrantDetail(String code) throws Exception {
        var target = record(code,Map.of("state","Closed")); visible(view(1,code,false,false));
        assertThat(detail.canAuditProcessDetail("reader",target)).isFalse();
    }

    @ParameterizedTest @ValueSource(strings={"native-fu","imported-fu"})
    void aMatchingMainRecordStillGrantsDetailWithoutRequestPermission(String code) throws Exception {
        var target = record(code,Map.of("state","Open")); visible(view(1,code,false,false));
        assertThat(detail.canAuditProcessDetail("reader",target)).isTrue();
        verifyNoInteractions(requestAccess);
    }

    @Test void anEmptySubViewCannotGrantWholeRequestDetail() throws Exception {
        var target = record("fu",Map.of("state","Open")); visible(view(1,"fu",true,false));
        assertThat(detail.canAuditProcessDetail("reader",target)).isFalse();
    }

    @Test void aFilteredOutSubRowCannotGrantWholeRequestDetail() throws Exception {
        var target = record("fu",Map.of("__subTables__",Map.of("dw:case_lines",List.of(Map.of("id","line","state","Closed")))));
        visible(view(1,"fu",true,false));
        assertThat(detail.canAuditProcessDetail("reader",target)).isFalse();
    }

    @Test void aMatchingSubRowCanStillOpenItsOwningRequest() throws Exception {
        var target = record("fu",Map.of("__subTables__",Map.of("dw:case_lines",List.of(Map.of("id","line","state","Open")))));
        visible(view(1,"fu",true,false));
        assertThat(detail.canAuditProcessDetail("reader",target)).isTrue();
    }

    @Test void anotherProcessMatchingTheViewCannotAuthorizeTheTarget() throws Exception {
        var target = record("fu",Map.of("state","Closed")); visible(view(1,"fu",false,false));
        jdbc.update("INSERT INTO up_process_instance(id,function_unit_code,start_user_id,variables) VALUES('other','fu','someone','{\"state\":\"Open\"}')");
        assertThat(detail.canAuditProcessDetail("reader",target)).isFalse();
    }

    @Test void involvementAndDesignedFilterMustBothMatch() throws Exception {
        var target = record("fu",Map.of("state","Open")); visible(view(1,"fu",false,true));
        when(involvement.predicateFor("reader","fu")).thenReturn(
                new MainTableViewInvolvementScope.Predicate(" AND pi.start_user_id = ?",List.of("reader")));
        assertThat(detail.canAuditProcessDetail("reader",target)).isFalse();
        jdbc.update("UPDATE up_process_instance SET start_user_id='reader'");
        assertThat(detail.canAuditProcessDetail("reader",target)).isTrue();
    }

    @Test void aSecondMatchingViewMayGrantAccess() throws Exception {
        var target = record("fu",Map.of("state","Open","__subTables__",Map.of("dw:case_lines",List.of())));
        visible(view(1,"fu",true,false),view(2,"fu",false,false));
        assertThat(detail.canAuditProcessDetail("reader",target)).isTrue();
    }

    @Test void otherFunctionUnitsNeverGrantAccess() throws Exception {
        var target = record("fu",Map.of("state","Open")); visible(view(1,"other-fu",false,false));
        assertThat(detail.canAuditProcessDetail("reader",target)).isFalse();
    }

    @Test void independentParticipantAdministratorAndAuditAccessRemain() throws Exception {
        var target = record("fu",Map.of("state","Closed")); visible(view(1,"fu",false,false));
        assertThat(detail.canAuditProcessDetail("owner",target)).isTrue();
        when(admins.isSystemAdministrator("admin")).thenReturn(true);
        assertThat(detail.canAuditProcessDetail("admin",target)).isTrue();
        when(client.getDataViews("auditor")).thenReturn(List.of());
        when(requestAccess.canAuditFunctionUnit("auditor","fu")).thenReturn(true);
        assertThat(detail.canAuditProcessDetail("auditor",target)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"false,process_status,RUNNING", "false,start_time,2026-11-03 12:00:00",
            "false,initiator,Actual Owner", "false,current_step,Review",
            "true,process_status,RUNNING", "true,start_time,2026-11-03 12:00:00",
            "true,initiator,Actual Owner", "true,current_step,Review"})
    void hidingSystemColumnsKeepsListExportAndDetailOnActualInstanceValues(
            boolean sub, String field, String expected) throws Exception {
        String code = sub ? "imported-system-fu" : "native-system-fu";
        var target = record(code, filterValues(sub, field, "spoofed"));
        jdbc.update("UPDATE up_process_instance SET status='RUNNING',start_time='2026-11-03 12:00:00',"
                + "start_user_name='Actual Owner',current_node='Review'");
        jdbc.update("INSERT INTO up_process_instance(id,function_unit_code,start_user_id,status,start_time,"
                        + "start_user_name,current_node,variables) VALUES('spoof',?,'other','COMPLETED',"
                        + "'2025-01-01','Other Owner','Finished',?::jsonb)",
                code, mapper.writeValueAsString(filterValues(sub, field, expected)));
        var request = new MainTableViewQueryRequest(0,20,null,null,null,null,null);
        for (boolean shown : List.of(true, false)) {
            visible(filteredView(code, sub, field, expected, shown, true, "field"));
            assertThat(service.containsProcess("reader",1L,"target")).as("%s visible=%s",field,shown).isTrue();
            assertThat(service.containsProcess("reader",1L,"spoof")).isFalse();
            assertThat(detail.canAuditProcessDetail("reader",target)).isTrue();
            var page = service.queryViewData("reader",1L,request);
            assertThat(page.total()).isEqualTo(1);
            assertThat(page.rows()).extracting(r -> r.processInstanceId()).containsExactly("target");
            assertThat(page.columns().stream().anyMatch(c -> field.equals(c.fieldName()))).isEqualTo(shown);
            assertThat(new String(service.exportViewCsv("reader",1L,20,request),java.nio.charset.StandardCharsets.UTF_8))
                    .contains("target").doesNotContain("spoof");
            jdbc.update("UPDATE up_process_instance SET status='COMPLETED',start_time='2025-01-01',"
                    + "start_user_name='Other Owner',current_node='Finished' WHERE id='target'");
            assertThat(detail.canAuditProcessDetail("reader",target)).isFalse();
            jdbc.update("UPDATE up_process_instance SET status='RUNNING',start_time='2026-11-03 12:00:00',"
                    + "start_user_name='Actual Owner',current_node='Review' WHERE id='target'");
        }
        verify(requestAccess, never()).canAccessFunctionUnit(anyString(),anyString());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void aHiddenBusinessFieldWithASystemColumnNameStillReadsDesignedJson(boolean sub) throws Exception {
        record("fu",filterValues(sub,"process_status","Business Open"));
        jdbc.update("UPDATE up_process_instance SET status='COMPLETED'");
        visible(filteredView("fu",sub,"process_status","Business Open",false,false,"field"));
        assertThat(service.containsProcess("reader",1L,"target")).isTrue();
    }

    @ParameterizedTest @ValueSource(strings={"lookup_display","fk_display"})
    void aHiddenDerivedLabelCannotBeReinterpretedAsAStoredJsonField(String columnType) throws Exception {
        record("fu",Map.of("label","Open"));
        visible(filteredView("fu",false,"label","Open",false,false,columnType));
        assertThatThrownBy(() -> service.containsProcess("reader",1L,"target"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("label");
    }

    private Map<String,Object> filterValues(boolean sub, String field, String value) {
        Map<String,Object> row = Map.of("platformRowUuid","line",field,value);
        return sub ? Map.of("__subTables__",Map.of("dw:case_lines",List.of(row))) : row;
    }

    private RuntimeDataView filteredView(String code, boolean sub, String field, String value,
                                         boolean shown, boolean system, String columnType) {
        Map<String,Object> config = Map.of("viewName","Filtered","fields",List.of(Map.of(
                        "fieldName",field,"visible",shown,"systemField",system,"columnType",columnType)),
                "filterConfig",Map.of("conditions",List.of(Map.of("fieldName",field,"operator","eq","value",value))));
        String table = sub ? "case_lines" : "cases";
        return new RuntimeDataView(1L,1L,1L,"key-1","release",code,"FU","1.0",null,
                table,table,sub ? "SUB" : "MAIN",config,
                List.of(Map.of("tableName",table,"fields",List.of(Map.of("fieldName",field,"dataType","VARCHAR")))),
                List.of(),List.of(),false);
    }
}
