package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.*;
import com.portal.repository.AssignmentSourceSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentSourceSnapshotBatchTest {
    @Test void sameBoundInstanceIsReadAndDecodedOnceWithoutLeakingOtherFields() throws Exception {
        var repository=spy(new AssignmentSourceSnapshotRepository(mock(JdbcTemplate.class)));
        var json=spy(new ObjectMapper());
        var component=new AssignmentSourceSnapshotComponent(repository,json);
        String payload="{\"__subTables__\":{\"dw:items\":[{\"id\":1,\"bu\":\"A\",\"private\":9},{\"id\":2,\"bu\":\"B\",\"private\":8}]},\"secret\":7}";
        doReturn(new AssignmentSourceSnapshotRepository.Stored(3,2,true,payload,true)).when(repository).read(any());
        UUID caseId=UUID.randomUUID();
        var first=new AssignmentSourceSnapshot.Request(caseId,"instance","frozen",List.of("bu"),
                new AssignmentSourceSnapshot.Row("dw:items",json.readTree("{\"id\":1}")));
        var second=new AssignmentSourceSnapshot.Request(caseId,"instance","frozen",List.of("bu"),
                new AssignmentSourceSnapshot.Row("dw:items",json.readTree("{\"id\":2}")));
        var results=component.readMany(List.of(first,second));
        assertEquals(List.of("{\"bu\":\"A\"}","{\"bu\":\"B\"}"),results.stream().map(value->value.values().toString()).toList());
        verify(repository,times(1)).read(any()); verify(json,times(1)).readTree(payload);
    }
    @Test void archivedMirrorNeverBecomesInitialAndUnboundedFieldsNeverReachStorage() {
        var repository=mock(AssignmentSourceSnapshotRepository.class);
        var component=new AssignmentSourceSnapshotComponent(repository,new ObjectMapper());
        var request=new AssignmentSourceSnapshot.Request(UUID.randomUUID(),"instance","frozen",List.of("reviewer"),null);
        when(repository.readMany(List.of(request))).thenReturn(List.of(new AssignmentSourceSnapshotRepository.Stored(3,2,false,null,true)));
        assertThrows(AssignmentFailure.class,()->component.readMany(List.of(request)));
        clearInvocations(repository);
        var forbidden=new AssignmentSourceSnapshot.Request(request.caseId(),"instance","frozen",List.of("__subTables__"),null);
        assertThrows(AssignmentFailure.class,()->component.readMany(List.of(forbidden)));
        assertThrows(AssignmentFailure.class,()->component.readMany(Collections.nCopies(1001,request)));
        verifyNoInteractions(repository);
    }
    @Test void aDifferentDefinitionCannotReuseTheFirstBindingRead() {
        var repository=spy(new AssignmentSourceSnapshotRepository(mock(JdbcTemplate.class)));
        doReturn(new AssignmentSourceSnapshotRepository.Stored(3,2,false,null,false)).when(repository).read(any());
        var component=new AssignmentSourceSnapshotComponent(repository,new ObjectMapper());
        UUID caseId=UUID.randomUUID();
        component.readMany(List.of(new AssignmentSourceSnapshot.Request(caseId,"instance","first",List.of("bu"),null),
                new AssignmentSourceSnapshot.Request(caseId,"instance","second",List.of("bu"),null)));
        verify(repository,times(2)).read(any());
    }
}
