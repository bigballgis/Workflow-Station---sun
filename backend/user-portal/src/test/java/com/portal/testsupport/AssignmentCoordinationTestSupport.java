package com.portal.testsupport;

import com.platform.common.assignment.AssignmentCoordinationVerifier;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.mock;

/** Only unrelated case/ledger tests use this stub; real domain and HTTP proof have their own PostgreSQL suite. */
public final class AssignmentCoordinationTestSupport {
    private AssignmentCoordinationTestSupport() { }
    public static void stubVerified(Object target) {
        ReflectionTestUtils.setField(target,"coordination",mock(AssignmentCoordinationVerifier.class));
    }
}
