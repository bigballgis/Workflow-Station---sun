package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The coarse SQL candidate search must include every configured person identity the checker accepts. */
class MainTableViewInvolvementSupersetTest {
    static final String BPMN = """
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:custom="urn:custom">
         <process id="fu"><subProcess id="mi"><multiInstanceLoopCharacteristics/>
          <userTask id="task"><extensionElements><custom:properties>
           <custom:property name="assigneeMode" value="user"/>
           <custom:property name="subTableName" value="lines"/>
           <custom:property name="assigneeField" value="handler"/>
          </custom:properties></extensionElements></userTask>
         </subProcess></process></definitions>
        """;
    private MainTableViewInvolvementChecker checker;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void setup() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("content_type='PROCESS'"),eq(String.class),eq("release"))).thenReturn(List.of(BPMN));
        when(jdbc.queryForList(contains("jsonb_array_elements"),eq("release")))
                .thenReturn(List.of(Map.of("table_name","lines","field_name","handler")));
        checker = new MainTableViewInvolvementChecker(jdbc);
    }

    private Map<String,Object> variables(Map<String,Object> row) {
        return Map.of("__subTables__",Map.of("dw:lines",List.of(row)));
    }

    private boolean coarseWouldMatch(String user,Map<String,Object> variables) throws Exception {
        return mapper.writeValueAsString(variables.get("__subTables__")).toLowerCase(Locale.ROOT)
                .contains(user.toLowerCase(Locale.ROOT));
    }

    @Test void configuredScalarLookupAndWhitespaceIdentityAreAllIncludedByTheCoarseFilter() throws Exception {
        String user = "user-44027893";
        for (Object value : List.of(user,"  "+user+"  ",Map.of("id",user,"name","Person"))) {
            var variables = variables(Map.of("handler",value));
            assertThat(checker.isMiParticipant(user,"release",variables)).isTrue();
            assertThat(coarseWouldMatch(user,variables)).isTrue();
        }
    }

    @Test void longerIdentityIsNotInvolvementEvenWhenCoarseFilterMatches() throws Exception {
        var variables = variables(Map.of("handler","user-12345"));
        assertThat(checker.isMiParticipant("user-1","release",variables)).isFalse();
        assertThat(coarseWouldMatch("user-1",variables)).isTrue();
    }

    @Test void hintsNestedNamesAndTextCannotGrantInvolvement() throws Exception {
        for (var row : List.of(Map.<String,Object>of("assignee","reader"),Map.<String,Object>of("participant_id","reader"),
                Map.<String,Object>of("archived_id_idw_reference","reader"),
                Map.<String,Object>of("detail",Map.of("handler","reader")),Map.<String,Object>of("notes","reader"))) {
            var variables = variables(row);
            assertThat(checker.isMiParticipant("reader","release",variables)).isFalse();
            assertThat(coarseWouldMatch("reader",variables)).isTrue();
        }
    }

    @Test void wrongCanonicalTableAndBindingNumberDoNotGrant() {
        for (String key : List.of("dw:other_lines","50533","lines","rt:lines")) {
            assertThat(checker.isMiParticipant("reader","release",Map.of("__subTables__",Map.of(key,List.of(Map.of("handler","reader")))))).isFalse();
        }
    }

    @Test void instancesWithoutSubTablesAreNotInvolvement() {
        assertThat(checker.isMiParticipant("reader","release",Map.of())).isFalse();
        assertThat(checker.isMiParticipant("reader","release",null)).isFalse();
        assertThat(checker.isMiParticipant("","release",variables(Map.of("handler","")))).isFalse();
    }
}
