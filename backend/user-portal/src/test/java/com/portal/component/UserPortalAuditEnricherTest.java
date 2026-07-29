package com.portal.component;

import com.portal.entity.ProcessInstance;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserPortalAuditEnricherTest {

    @Test
    void buildProcessTitle_prefersRequestIdOverTitleAndBusinessKey() {
        ProcessInstance pi = ProcessInstance.builder()
                .id("9c8086c5-8a33-11f1-8b28-4a6972150855")
                .title("Some Title")
                .businessKey("BK-001")
                .processDefinitionName("ATM")
                .build();

        String title = UserPortalAuditEnricher.buildProcessTitle(pi, pi.getId(), "ATM-DC-PW-000020");

        assertThat(title).isEqualTo("ATM-DC-PW-000020");
    }

    @Test
    void buildProcessTitle_fallsBackToTitleWhenRequestIdMissing() {
        ProcessInstance pi = ProcessInstance.builder()
                .id("9c8086c5-8a33-11f1-8b28-4a6972150855")
                .title("Case Title")
                .businessKey("BK-001")
                .processDefinitionName("ATM")
                .build();

        String title = UserPortalAuditEnricher.buildProcessTitle(pi, pi.getId(), null);

        assertThat(title).isEqualTo("Case Title");
    }

    @Test
    void buildProcessTitle_fallsBackToBusinessKeyThenDefNameShortId() {
        ProcessInstance withBk = ProcessInstance.builder()
                .id("abcdefgh-ijkl-mnop-qrst-uvwxyz012345")
                .businessKey("BK-77")
                .processDefinitionName("ATM")
                .build();
        assertThat(UserPortalAuditEnricher.buildProcessTitle(withBk, withBk.getId(), "  "))
                .isEqualTo("BK-77");

        ProcessInstance defOnly = ProcessInstance.builder()
                .id("abcdefgh-ijkl-mnop-qrst-uvwxyz012345")
                .processDefinitionName("Multi-Instance")
                .build();
        assertThat(UserPortalAuditEnricher.buildProcessTitle(defOnly, defOnly.getId(), null))
                .isEqualTo("Multi-Instance · abcdefgh");
    }

    @Test
    void buildProcessTitle_usesShortProcessIdWhenNoPi() {
        assertThat(UserPortalAuditEnricher.buildProcessTitle(null, "1234567890abcdef", null))
                .isEqualTo("12345678");
    }
}
