package com.workflow.assignment;

import com.platform.common.assignment.*;
import com.platform.common.dto.ApiResponse;
import com.platform.common.util.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AssignmentPostActionSnapshotClientTest {
    final RestTemplate http=new RestTemplate();
    final AssignmentPostActionSnapshotClient client=new AssignmentPostActionSnapshotClient(http);
    final MockRestServiceServer server=MockRestServiceServer.bindTo(http).build();
    final AssignmentPostAction.SnapshotRequest request=new AssignmentPostAction.SnapshotRequest(UUID.randomUUID(),0,"event","instance","actor");
    @BeforeEach void configure() {
        ReflectionTestUtils.setField(client,"baseUrl","http://portal.test"); ReflectionTestUtils.setField(client,"serviceToken",UUID.randomUUID().toString());
    }
    @Test void exactOperationBindingAndServiceTokenAreRequiredForFrozenVariables() {
        server.expect(requestTo("http://portal.test/api/portal/internal/assignment/post-action-snapshot"))
                .andExpect(header("X-Internal-Token",(String)ReflectionTestUtils.getField(client,"serviceToken")))
                .andRespond(withSuccess(JsonUtils.toJson(ApiResponse.success(new AssignmentPostAction.Snapshot(request,Map.of("recipient","original")))),MediaType.APPLICATION_JSON));
        assertEquals(Map.of("recipient","original"),client.read(request)); server.verify();
    }
    @Test void aSnapshotForAnotherOperationCannotBecomeTheCurrentMailsVariables() {
        var wrong=new AssignmentPostAction.SnapshotRequest(UUID.randomUUID(),0,"event","instance","actor");
        server.expect(anything()).andRespond(withSuccess(JsonUtils.toJson(ApiResponse.success(new AssignmentPostAction.Snapshot(wrong,Map.of()))),MediaType.APPLICATION_JSON));
        assertThrows(AssignmentFailure.class,()->client.read(request)); server.verify();
    }
}
