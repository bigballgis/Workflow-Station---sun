package com.admin.component;

import com.admin.entity.FunctionUnit;
import com.admin.repository.EmailConnectionRepository;
import com.admin.repository.FunctionUnitRepository;
import com.platform.security.encryption.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailConnectionSyncResolveCodeTest {

    private FunctionUnitRepository functionUnitRepository;
    private EmailConnectionSyncComponentImpl component;

    @BeforeEach
    void setUp() {
        functionUnitRepository = mock(FunctionUnitRepository.class);
        component = new EmailConnectionSyncComponentImpl(
                mock(EmailConnectionRepository.class),
                functionUnitRepository,
                mock(EncryptionService.class),
                mock(SystemSmtpConfigResolver.class));
    }

    @Test
    void resolveFunctionUnitCodeById_returnsCode() {
        FunctionUnit unit = FunctionUnit.builder().id("fu-uuid").code("FU-MCY").build();
        when(functionUnitRepository.findById("fu-uuid")).thenReturn(Optional.of(unit));

        assertThat(component.resolveFunctionUnitCodeById("fu-uuid")).contains("FU-MCY");
    }

    @Test
    void resolveFunctionUnitCodeById_emptyWhenMissing() {
        when(functionUnitRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(component.resolveFunctionUnitCodeById("missing")).isEmpty();
        assertThat(component.resolveFunctionUnitCodeById(" ")).isEmpty();
    }
}
