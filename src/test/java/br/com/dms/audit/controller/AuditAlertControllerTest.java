package br.com.dms.audit.controller;

import br.com.dms.audit.controller.response.AuditAlertListResponse;
import br.com.dms.audit.service.AuditAlert;
import br.com.dms.audit.service.AuditAlertService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditAlertControllerTest {

    private final AuditAlertService alertService = Mockito.mock(AuditAlertService.class);
    private final AuditAlertController controller = new AuditAlertController(alertService);

    @Test
    void shouldRejectWithoutTenantId() {
        assertThatThrownBy(() -> controller.listActive(null, null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400 BAD_REQUEST");

        Mockito.verifyNoInteractions(alertService);
    }

    @Test
    void shouldReturnActiveAlerts() {
        Mockito.when(alertService.findActiveAlerts("tenant-a"))
            .thenReturn(List.of(new AuditAlert(
                "FAILED_ACCESS_SPIKE",
                "HIGH",
                "Múltiplas tentativas negadas",
                "desc",
                Instant.parse("2026-03-05T03:00:00Z"),
                Map.of("attempts", 8)
            )));

        ResponseEntity<AuditAlertListResponse> response = controller.listActive("tenant-a", null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().total()).isEqualTo(1);
        assertThat(response.getBody().alerts().getFirst().code()).isEqualTo("FAILED_ACCESS_SPIKE");
        Mockito.verify(alertService).findActiveAlerts("tenant-a");
    }
}
