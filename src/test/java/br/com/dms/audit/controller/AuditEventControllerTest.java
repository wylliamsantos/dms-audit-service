package br.com.dms.audit.controller;

import br.com.dms.audit.controller.response.AuditEventSearchResponse;
import br.com.dms.audit.model.AuditEventDocument;
import br.com.dms.audit.service.AuditEventQueryService;
import br.com.dms.audit.service.AuditEventSearchHit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditEventControllerTest {

    private final AuditEventQueryService queryService = Mockito.mock(AuditEventQueryService.class);
    private final AuditEventController controller = new AuditEventController(queryService);

    @Test
    void shouldRejectSearchWithoutTenantId() {
        assertThatThrownBy(() -> controller.search(null, null, null, null, null, null, null, null, PageRequest.of(0, 50)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400 BAD_REQUEST")
            .hasMessageContaining("tenantId is required");

        Mockito.verifyNoInteractions(queryService);
    }

    @Test
    void shouldRejectSearchWhenTenantHeaderAndQueryDiffer() {
        assertThatThrownBy(() -> controller.search(
            null,
            "tenant-a",
            "tenant-b",
            null,
            null,
            null,
            null,
            null,
            PageRequest.of(0, 50)
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN")
            .hasMessageContaining("tenantId mismatch");

        Mockito.verifyNoInteractions(queryService);
    }

    @Test
    void shouldReturnSearchResponseWhenTenantIdIsPresent() {
        AuditEventDocument document = new AuditEventDocument(
            null,
            "DOCUMENT_INDEXED",
            Instant.parse("2026-02-16T15:00:00Z").toEpochMilli(),
            Instant.parse("2026-02-16T15:00:01Z").toEpochMilli(),
            "user-1",
            "tenant-a",
            "DOCUMENT",
            "doc-123",
            "contract.pdf",
            Map.<String, Object>of("source", "watch"),
            Map.<String, Object>of("status", "indexed")
        );

        Mockito.when(queryService.search(Mockito.any(), Mockito.any()))
            .thenReturn(new PageImpl<>(List.of(new AuditEventSearchHit("evt-1", document)), PageRequest.of(0, 50), 1));

        ResponseEntity<AuditEventSearchResponse> response = controller.search(
            "user-1",
            "tenant-a",
            null,
            null,
            null,
            null,
            null,
            null,
            PageRequest.of(0, 50)
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().events()).hasSize(1);
        assertThat(response.getBody().events().getFirst().tenantId()).isEqualTo("tenant-a");
        Mockito.verify(queryService).search(Mockito.any(), Mockito.any());
    }

    @Test
    void shouldAcceptTenantFromHeaderOnly() {
        AuditEventDocument document = new AuditEventDocument(
            null,
            "DOCUMENT_INDEXED",
            Instant.parse("2026-02-16T15:00:00Z").toEpochMilli(),
            Instant.parse("2026-02-16T15:00:01Z").toEpochMilli(),
            "user-1",
            "tenant-header",
            "DOCUMENT",
            "doc-123",
            "contract.pdf",
            Map.of(),
            Map.of()
        );

        Mockito.when(queryService.search(Mockito.any(), Mockito.any()))
            .thenReturn(new PageImpl<>(List.of(new AuditEventSearchHit("evt-1", document)), PageRequest.of(0, 50), 1));

        ResponseEntity<AuditEventSearchResponse> response = controller.search(
            "user-1",
            null,
            "tenant-header",
            null,
            null,
            null,
            null,
            null,
            PageRequest.of(0, 50)
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().events().getFirst().tenantId()).isEqualTo("tenant-header");
        Mockito.verify(queryService).search(Mockito.any(), Mockito.any());
    }
}
