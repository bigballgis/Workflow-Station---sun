package verification;

import com.workflow.assignment.AssignmentRuntimeSupport;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test classpath only. Never packaged into the production jar/image or discovered by the application's component scan. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="assignment.verification.enabled",havingValue="true")
public class AssignmentEngineVerificationConfiguration {
    @Bean @Primary
    public AssignmentRuntimeSupport assignmentVerificationRuntimeSupport(JdbcTemplate jdbc) {
        if (!"assignment_e2e_1179".equals(jdbc.queryForObject("SELECT current_database()",String.class))) {
            throw new IllegalStateException("Assignment HTTP verification requires its owned acceptance database");
        }
        return new AssignmentRuntimeSupport() {
            @Override public Capability capability() { return new Capability(1,true); }
        };
    }
}
