package com.workflow.assignment;

import com.platform.common.assignment.*;
import com.platform.common.dto.ApiResponse;
import com.platform.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AssignmentSourceSnapshotBatchClientTest {
    @Test void actualHttpBatchCarriesTheServiceTokenAndValidatesEveryResponse() {
        var http=new RestTemplate(); var server=MockRestServiceServer.bindTo(http).build();
        var client=new AssignmentSourceSnapshotClient(http); String token=UUID.randomUUID().toString();
        ReflectionTestUtils.setField(client,"baseUrl","http://portal.test"); ReflectionTestUtils.setField(client,"serviceToken",token);
        UUID caseId=UUID.randomUUID();
        var request=new AssignmentSourceSnapshot.Request(caseId,"instance","frozen",List.of("bu"),null);
        var response=new AssignmentSourceSnapshot(1,caseId,"instance",3,2,AssignmentSourceSnapshot.State.READY,
                JsonUtils.getObjectMapper().createObjectNode().put("bu","CURRENT_BU"));
        server.expect(requestTo("http://portal.test/api/portal/internal/assignment/source-snapshots"))
                .andExpect(header("X-Internal-Token",token)).andExpect(jsonPath("$.requests[0].fields[0]").value("bu"))
                .andRespond(withSuccess(JsonUtils.toJson(ApiResponse.success(List.of(response))),MediaType.APPLICATION_JSON));
        assertEquals("CURRENT_BU",client.readMany(List.of(request)).get(0).values().path("bu").asText()); server.verify();
    }
    @Test void extraFieldsAndWrongBindingsCannotBeAcceptedAsTheRequestedSource() {
        for(String malformed:List.of("extra","binding","initial")) {
            var http=new RestTemplate(); var server=MockRestServiceServer.bindTo(http).build(); var client=new AssignmentSourceSnapshotClient(http);
            ReflectionTestUtils.setField(client,"baseUrl","http://portal.test"); ReflectionTestUtils.setField(client,"serviceToken",UUID.randomUUID().toString());
            UUID caseId=UUID.randomUUID(); var request=new AssignmentSourceSnapshot.Request(caseId,"instance","frozen",List.of("bu"),null);
            var fields=JsonUtils.getObjectMapper().createObjectNode().put("bu","BU"); if(malformed.equals("extra")) fields.put("private","hidden");
            var response=new AssignmentSourceSnapshot(1,caseId,malformed.equals("binding")?"other":"instance",3,2,
                    malformed.equals("initial")?AssignmentSourceSnapshot.State.INITIAL:AssignmentSourceSnapshot.State.READY,fields);
            server.expect(anything()).andRespond(withSuccess(JsonUtils.toJson(ApiResponse.success(List.of(response))),MediaType.APPLICATION_JSON));
            assertThrows(AssignmentFailure.class,()->client.readMany(List.of(request))); server.verify();
        }
    }
}
