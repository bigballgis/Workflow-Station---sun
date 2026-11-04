package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dataview.RuntimeDataView;
import com.portal.client.PortalBiClient;
import com.portal.dto.MainTableViewQueryRequest;
import com.portal.entity.ProcessInstance;
import com.portal.service.UserDisplayNameResolver;
import com.portal.service.impl.PortalMainTableViewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual metadata lookup, participant policy, SQL candidate filter, list/export and detail scope. */
@Testcontainers
class MainTableViewInvolvementPostgresTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");
    private final ObjectMapper mapper = new ObjectMapper();
    private JdbcTemplate jdbc;
    private MainTableViewInvolvementChecker checker;

    @BeforeEach void setup() throws Exception {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()));
        jdbc.execute("DROP TABLE IF EXISTS up_process_instance,sys_function_unit_contents,ACT_HI_TASKINST");
        jdbc.execute("CREATE TABLE up_process_instance(id TEXT PRIMARY KEY,function_unit_code TEXT,start_user_id TEXT,"
                + "variables JSONB,status TEXT,start_time TIMESTAMP,start_user_name TEXT,function_unit_catalog_id TEXT,"
                + "function_unit_version_label TEXT,process_definition_id TEXT,current_node TEXT)");
        jdbc.execute("CREATE TABLE sys_function_unit_contents(function_unit_id TEXT,content_type TEXT,content_data TEXT)");
        jdbc.execute("CREATE TABLE ACT_HI_TASKINST(PROC_INST_ID_ TEXT,ASSIGNEE_ TEXT)");
        checker = new MainTableViewInvolvementChecker(jdbc);
        release("old","handler_old","user");
        release("new","handler_new","user");
    }

    private void release(String catalog,String field,String mode) throws Exception {
        String xml = """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn" xmlns:custom="http://workflow.platform/schema/bpmn">
             <process id="fu"><subProcess id="mi"><multiInstanceLoopCharacteristics flowable:collection="rows"/>
              <userTask id="review"><extensionElements><custom:properties>
               <custom:property name="subTableName" value="lines"/><custom:property name="assigneeMode" value="%s"/>
               <custom:property name="assigneeField" value="%s"/>
               <custom:property name="roleField" value="assigned_role"/>
              </custom:properties></extensionElements></userTask>
             </subProcess></process></definitions>
            """.formatted(mode,field);
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES(?,'PROCESS',?)",catalog,xml);
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES(?,'DATA_TABLE',?)",catalog,mapper.writeValueAsString(
                Map.of("tableName","lines","tableType","SUB","fields",List.of(
                        Map.of("fieldName",field,"dataType","VARCHAR"),Map.of("fieldName","assigned_role","dataType","VARCHAR")))));
    }

    private ProcessInstance instance(String id,String catalog,Map<String,Object> rows) throws Exception {
        var variables = Map.<String,Object>of("__subTables__",rows);
        jdbc.update("INSERT INTO up_process_instance(id,function_unit_code,start_user_id,function_unit_catalog_id,variables) VALUES(?,'fu','owner',?,?::jsonb)",id,catalog,mapper.writeValueAsString(variables));
        return ProcessInstance.builder().id(id).functionUnitCode("fu").functionUnitCatalogId(catalog)
                .startUserId("owner").variables(variables).build();
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void configuredRenamedUsersMatchButUnrelatedHintsDoNotInListExportOrDetailScope(boolean sub) throws Exception {
        instance("valid-old","old",Map.of("dw:lines",List.of(Map.of("platformRowUuid","one","handler_old",Map.of("id","reader","name","Person")))));
        instance("valid-new","new",Map.of("dw:lines",List.of(Map.of("platformRowUuid","two","handler_new"," reader "))));
        instance("spoof","old",Map.of("dw:lines",List.of(Map.of("platformRowUuid","three","participant_notes","reader","archived_id_idw_reference","reader","handler_new","reader"))));
        instance("wrong-table","old",Map.of("dw:other_lines",List.of(Map.of("platformRowUuid","four","handler_old","reader","assignee","reader"))));
        var client = mock(PortalBiClient.class);
        Map<String,Object> config = Map.of("viewName","Restricted","restrictToInvolvedUsers",true,"fields",List.of(),"filterConfig",Map.of());
        var view = new RuntimeDataView(1L,1L,1L,"key","new","fu","FU","2",null,
                sub?"lines":"cases","Table",sub?"SUB":"MAIN",config,
                List.of(Map.of("tableName",sub?"lines":"cases","fields",List.of())),List.of(),List.of(),false);
        when(client.getDataView("reader",1L)).thenReturn(view);
        var service = new PortalMainTableViewServiceImpl(client,mapper,new MainTableViewInvolvementScope(jdbc,mapper,checker),
                new MainTableViewRowQueryComponent(jdbc,mapper),new MainTableViewSubRowQueryComponent(jdbc,mapper),mock(UserDisplayNameResolver.class));
        assertThat(service.containsProcess("reader",1L,"spoof")).isFalse();
        assertThat(service.containsProcess("reader",1L,"wrong-table")).isFalse();
        assertThat(service.containsProcess("reader",1L,"valid-old")).isTrue();
        assertThat(service.containsProcess("reader",1L,"valid-new")).isTrue();
        var request = new MainTableViewQueryRequest(0,20,null,null,null,null,null);
        var page = service.queryViewData("reader",1L,request);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.rows()).extracting(r -> r.processInstanceId()).containsExactlyInAnyOrder("valid-old","valid-new");
        assertThat(new String(service.exportViewCsv("reader",1L,20,request),StandardCharsets.UTF_8))
                .contains("valid-old","valid-new").doesNotContain("spoof","wrong-table");
    }

    @Test void roleOnlyAssignmentDoesNotGrantFromAUserField() throws Exception {
        release("roles","assignee","role");
        var pi = instance("role-record","roles",Map.of("dw:lines",List.of(Map.of("assignee","reader","assigned_role","reader"))));
        assertThat(checker.isUserInvolved("reader",pi)).isFalse();
    }

    @Test void initiatorAndHistoricAssigneeRemainIndependent() throws Exception {
        var pi = instance("owned","old",Map.of());
        assertThat(checker.isUserInvolved("owner",pi)).isTrue();
        jdbc.update("INSERT INTO ACT_HI_TASKINST VALUES('owned','handler')");
        assertThat(checker.isUserInvolved("handler",pi)).isTrue();
    }

    @Test void staleOrMissingReleaseCannotFallBackToCurrentDesign() throws Exception {
        var pi = instance("broken","missing",Map.of("dw:lines",List.of(Map.of("assignee","reader"))));
        assertThatThrownBy(() -> checker.isUserInvolved("reader",pi)).isInstanceOf(RuntimeException.class);
    }

    @Test void pendingPersonRemovalMustRevokeTheNextQuery() throws Exception {
        release("mutable","assignee","user");
        instance("revoked","mutable",Map.of("dw:lines",List.of(Map.of("assignee","reader"))));
        var scope = new MainTableViewInvolvementScope(jdbc,mapper,checker);
        assertThat(scope.miVisibleInstanceIds("reader","fu")).containsExactly("revoked");
        jdbc.update("UPDATE up_process_instance SET variables=jsonb_set(variables,'{__subTables__,dw:lines,0,assignee}','\"other\"') WHERE id='revoked'");
        assertThat(scope.miVisibleInstanceIds("reader","fu")).isEmpty();
    }

    @Test void independentOwnerAndHistoryGrantsDoNotRequireMiMetadata() throws Exception {
        instance("owned-legacy","missing",Map.of("dw:lines",List.of(Map.of("assignee","owner"))));
        instance("handled-legacy","missing",Map.of("dw:lines",List.of(Map.of("assignee","handler"))));
        jdbc.update("INSERT INTO ACT_HI_TASKINST VALUES('handled-legacy','handler')");
        var scope = new MainTableViewInvolvementScope(jdbc,mapper,checker);
        assertThat(scope.miVisibleInstanceIds("owner","fu")).isEmpty();
        assertThat(scope.miVisibleInstanceIds("handler","fu")).isEmpty();
    }
}
