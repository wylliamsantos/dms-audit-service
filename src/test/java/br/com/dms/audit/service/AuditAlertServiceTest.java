package br.com.dms.audit.service;

import br.com.dms.audit.model.AuditEventDocument;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditAlertServiceTest {

    private final AuditEventQueryService queryService = Mockito.mock(AuditEventQueryService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-03-05T03:36:00Z"), ZoneOffset.UTC);
    private final AuditAlertService service = new AuditAlertService(queryService, clock, 30);

    @Test
    void shouldDetectFailedAccessSpike() {
        List<AuditEventDocument> events = List.of(
            event("AUTH_FAILURE", "user-a", "10.1.0.1", "2026-03-05T03:31:00Z"),
            event("AUTH_FAILURE", "user-a", "10.1.0.1", "2026-03-05T03:31:10Z"),
            event("LOGIN_FAILED", "user-a", "10.1.0.1", "2026-03-05T03:31:20Z"),
            event("DOCUMENT_ACCESS_DENIED", "user-a", "10.1.0.1", "2026-03-05T03:31:30Z"),
            event("AUTH_FAILURE", "user-a", "10.1.0.1", "2026-03-05T03:31:40Z")
        );

        Mockito.when(queryService.findRecentByTenant(Mockito.eq("tenant-a"), Mockito.any(), Mockito.eq(800)))
            .thenReturn(events);

        List<AuditAlert> alerts = service.findActiveAlerts("tenant-a");

        assertThat(alerts).extracting(AuditAlert::code).contains("FAILED_ACCESS_SPIKE");
    }

    @Test
    void shouldDetectMultipleIpsForSameUser() {
        List<AuditEventDocument> events = List.of(
            event("DOCUMENT_VIEWED", "user-z", "10.0.0.1", "2026-03-05T03:20:00Z"),
            event("DOCUMENT_VIEWED", "user-z", "10.0.0.2", "2026-03-05T03:21:00Z"),
            event("DOCUMENT_VIEWED", "user-z", "10.0.0.3", "2026-03-05T03:22:00Z")
        );

        Mockito.when(queryService.findRecentByTenant(Mockito.eq("tenant-a"), Mockito.any(), Mockito.eq(800)))
            .thenReturn(events);

        List<AuditAlert> alerts = service.findActiveAlerts("tenant-a");

        assertThat(alerts).extracting(AuditAlert::code).contains("MULTIPLE_IPS_PER_USER");
    }

    private AuditEventDocument event(String type, String userId, String ip, String occurredAt) {
        return new AuditEventDocument(
            null,
            type,
            Instant.parse(occurredAt).toEpochMilli(),
            Instant.parse(occurredAt).toEpochMilli(),
            userId,
            "tenant-a",
            "DOCUMENT",
            "doc-1",
            "x.pdf",
            Map.of(),
            Map.of("ip", ip)
        );
    }
}
