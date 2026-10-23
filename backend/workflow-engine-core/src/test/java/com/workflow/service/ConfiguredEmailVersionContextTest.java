package com.workflow.service;

import com.platform.common.functionunit.CatalogSnapshotReader;
import com.platform.common.functionunit.FunctionUnitVersionException;
import com.platform.common.i18n.I18nService;
import com.workflow.client.AdminCenterClient;
import com.workflow.client.DeveloperWorkstationEmailTemplateClient;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ConfiguredEmailVersionContextTest {
    private static final String PIN_QUERY = "SELECT function_unit_catalog_id FROM up_process_instance WHERE id = ?";
    private static final Map<String, String> TEMPLATED_EMAIL = Map.of(
            "connectionId", "mail", "emailTo", "fixture@example.test", "emailTemplateId", "501");

    private final AdminCenterClient admin = mock(AdminCenterClient.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CatalogSnapshotReader snapshots = mock(CatalogSnapshotReader.class);
    private final ConfiguredEmailSender sender = sender();

    private ConfiguredEmailSender sender() {
        var sender = new ConfiguredEmailSender(admin, mock(DeveloperWorkstationEmailTemplateClient.class),
                mock(EmailAttachmentResolver.class), mock(EmailSenderService.class),
                mock(TextEnvironmentInterpolator.class), mock(I18nService.class));
        ReflectionTestUtils.setField(sender, "pinnedReleaseLookup", new com.workflow.component.PinnedReleaseLookup(jdbc));
        ReflectionTestUtils.setField(sender, "catalogSnapshots", snapshots);
        return sender;
    }

    @Test void missingReleaseCannotResolveTheCurrentlyActiveReleaseByCode() {
        when(jdbc.queryForList(PIN_QUERY, String.class, "pi-1")).thenReturn(List.of());
        assertThatThrownBy(() -> sender.send("historical request", TEMPLATED_EMAIL,
                Map.of("functionUnitCode", "purchase"), "pi-1"))
                .isInstanceOf(FunctionUnitVersionException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((FunctionUnitVersionException) error).getDetails()).containsEntry("reason", "VERSION_RECOVERY_REQUIRED"));
        verifyNoInteractions(admin, snapshots);
    }

    /**
     * Instances started before their engine variables carried the release (e.g. called by a caller
     * deployed earlier) still have it on the Portal's record: the email resolves in that release.
     */
    @Test void releaseMissingFromTheEngineIsTakenFromTheRequestsPortalPin() {
        when(jdbc.queryForList(PIN_QUERY, String.class, "pi-1")).thenReturn(List.of("rel-vendor-1"));
        when(snapshots.contents("rel-vendor-1", "EMAIL_TEMPLATE")).thenReturn(List.of());

        assertThatThrownBy(() -> sender.send("historical request", TEMPLATED_EMAIL,
                Map.of("functionUnitCode", "vendor"), "pi-1"))
                .isInstanceOf(EmailDeliveryException.class);
        verify(snapshots).contents("rel-vendor-1", "EMAIL_TEMPLATE");
        verifyNoInteractions(admin);
    }

    /** A release in the engine variables is the request's own; the Portal record is not consulted. */
    @Test void releaseInTheEngineVariablesIsUsedAsIs() {
        when(snapshots.contents("rel-engine", "EMAIL_TEMPLATE")).thenReturn(List.of());

        assertThatThrownBy(() -> sender.send("request", TEMPLATED_EMAIL,
                Map.of("functionUnitId", "rel-engine"), "pi-1"))
                .isInstanceOf(EmailDeliveryException.class);
        verify(snapshots).contents("rel-engine", "EMAIL_TEMPLATE");
        verifyNoInteractions(jdbc);
    }
}
