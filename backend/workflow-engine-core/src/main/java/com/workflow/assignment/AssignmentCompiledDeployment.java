package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import org.flowable.common.engine.api.repository.EngineDeployment;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Supplier;

/** Only the platform compiler's exact single XML resource may publish server-owned assignment metadata. */
public final class AssignmentCompiledDeployment {
    private static final ThreadLocal<Resource> CURRENT=new ThreadLocal<>();
    private record Resource(String name,byte[] bytes) { }
    private AssignmentCompiledDeployment() { }
    public static <T> T deploy(String name,String xml,Supplier<T> deployment) {
        if (CURRENT.get()!=null || name==null || name.isBlank() || xml==null || xml.isBlank()) throw denied();
        CURRENT.set(new Resource(name,xml.getBytes(StandardCharsets.UTF_8)));
        try { return deployment.get(); }
        finally { CURRENT.remove(); }
    }
    public static void require(EngineDeployment deployment) {
        var expected=CURRENT.get();
        if (expected==null || deployment==null || !deployment.isNew()) throw denied();
        var resources=deployment.getResources().values().stream().filter(resource->!resource.isGenerated()).toList();
        if (resources.size()!=1 || !expected.name().equals(resources.get(0).getName())
                || !Arrays.equals(expected.bytes(),resources.get(0).getBytes())) throw denied();
    }
    private static AssignmentFailure denied() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_ENGINE_UNSUPPORTED); }
}
