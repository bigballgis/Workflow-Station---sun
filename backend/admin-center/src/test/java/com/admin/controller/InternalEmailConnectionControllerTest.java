package com.admin.controller;

import com.admin.component.EmailConnectionSyncComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalEmailConnectionControllerTest {

    @Mock
    private EmailConnectionSyncComponent syncComponent;

    private InternalEmailConnectionController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalEmailConnectionController(syncComponent, "svc-secret");
    }

    @Test
    void getCredentials_rejectsMissingToken() {
        ResponseEntity<Map<String, Object>> response = controller.getCredentials(null, "fu-1", "conn-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("error", "FORBIDDEN");
        verifyNoInteractions(syncComponent);
    }

    @Test
    void getCredentials_rejectsWrongToken() {
        ResponseEntity<Map<String, Object>> response = controller.getCredentials("wrong", "fu-1", "conn-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(syncComponent);
    }

    @Test
    void getCredentials_returnsBodyWhenTokenMatches() {
        when(syncComponent.getCredentials("fu-1", "conn-1"))
                .thenReturn(Optional.of(Map.of("password", "from-vault")));

        ResponseEntity<Map<String, Object>> response = controller.getCredentials("svc-secret", "fu-1", "conn-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("password", "from-vault");
    }
}
