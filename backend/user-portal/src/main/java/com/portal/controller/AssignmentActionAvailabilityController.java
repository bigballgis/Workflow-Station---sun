package com.portal.controller;

import com.platform.common.assignment.*;
import com.platform.common.dto.ApiResponse;
import com.portal.client.AssignmentEngineTransport;
import com.portal.security.CurrentUserId;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
public class AssignmentActionAvailabilityController {
    private static final ParameterizedTypeReference<ApiResponse<AssignmentActionAvailability>> TYPE=new ParameterizedTypeReference<>() { };
    private final AssignmentEngineTransport engine;
    private final com.portal.service.UserDisplayNameResolver names;
    public AssignmentActionAvailabilityController(AssignmentEngineTransport engine,com.portal.service.UserDisplayNameResolver names) { this.engine=engine; this.names=names; }
    @GetMapping("/tasks/{task}/assignment/handoff-availability")
    public ApiResponse<View> handoff(@PathVariable String task,@CurrentUserId String actor) { return read(task,actor,"handoff"); }
    @GetMapping("/processes/{source}/assignment/reopen-availability")
    public ApiResponse<View> reopen(@PathVariable String source,@CurrentUserId String actor) { return read(source,actor,"reopen"); }
    private ApiResponse<View> read(String resource,String actor,String action) {
        com.portal.security.AssignmentPortalActor.require(actor);
        var result=engine.post(List.of("action-availability",resource,action),Map.of("actor",actor),TYPE,false);
        if (result==null || result.contractVersion()!=1 || !resource.equals(result.resourceId()) || !actor.equals(result.actor())) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        }
        var labels=names.resolveBatch(result.targets());
        return ApiResponse.success(new View(1,resource,result.allowed(),result.targets().stream().map(id->new Target(id,labels.getOrDefault(id,id))).toList()));
    }
    public record View(int contractVersion,String resourceId,boolean allowed,List<Target> targets) { }
    public record Target(String id,String label) { }
}
