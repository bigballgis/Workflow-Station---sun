package com.workflow.service;

import com.platform.common.functionunit.FunctionUnitVersionException;
import com.platform.common.i18n.I18nService;
import com.workflow.client.AdminCenterClient;
import com.workflow.client.DeveloperWorkstationEmailTemplateClient;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ConfiguredEmailVersionContextTest {
    @Test void missingReleaseCannotResolveTheCurrentlyActiveReleaseByCode() {
        var admin = mock(AdminCenterClient.class);
        var sender = new ConfiguredEmailSender(admin, mock(DeveloperWorkstationEmailTemplateClient.class),
                mock(EmailAttachmentResolver.class), mock(EmailSenderService.class),
                mock(TextEnvironmentInterpolator.class), mock(I18nService.class));
        assertThatThrownBy(() -> sender.send("historical request", Map.of(
                "connectionId", "mail", "emailTo", "fixture@example.test", "emailTemplateId", "501"),
                Map.of("functionUnitCode", "purchase")))
                .isInstanceOf(FunctionUnitVersionException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((FunctionUnitVersionException) error).getDetails()).containsEntry("reason", "VERSION_RECOVERY_REQUIRED"));
        verifyNoInteractions(admin);
    }
}
