package br.com.dms.audit.service;

import br.com.dms.audit.model.AuditEventDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuditAlertService {

    private static final Set<String> FAILED_EVENT_TYPES = Set.of("AUTH_FAILURE", "DOCUMENT_ACCESS_DENIED", "LOGIN_FAILED");
    private static final Set<String> BULK_VIEW_EVENT_TYPES = Set.of("DOCUMENT_VIEWED", "DOCUMENT_DOWNLOADED");

    private final AuditEventQueryService queryService;
    private final Clock clock;
    private final int lookbackMinutes;

    public AuditAlertService(AuditEventQueryService queryService,
                             @Value("${dms.audit.alerts.lookback-minutes:30}") int lookbackMinutes) {
        this(queryService, Clock.systemUTC(), lookbackMinutes);
    }

    AuditAlertService(AuditEventQueryService queryService, Clock clock, int lookbackMinutes) {
        this.queryService = queryService;
        this.clock = clock;
        this.lookbackMinutes = lookbackMinutes;
    }

    public List<AuditAlert> findActiveAlerts(String tenantId) {
        Instant now = Instant.now(clock);
        Instant from = now.minus(Duration.ofMinutes(lookbackMinutes));
        List<AuditEventDocument> events = queryService.findRecentByTenant(tenantId, from, 800);

        List<AuditAlert> alerts = new ArrayList<>();
        alerts.addAll(detectFailedAccessSpike(events, now));
        alerts.addAll(detectBulkAccess(events, now));
        alerts.addAll(detectMultipleIpsByUser(events, now));

        return alerts.stream()
            .sorted(Comparator.comparing(AuditAlert::detectedAt).reversed())
            .toList();
    }

    private List<AuditAlert> detectFailedAccessSpike(List<AuditEventDocument> events, Instant now) {
        Instant threshold = now.minus(Duration.ofMinutes(10));
        Map<String, Long> failedByActor = events.stream()
            .filter(event -> isAfter(event, threshold))
            .filter(event -> FAILED_EVENT_TYPES.contains(normalize(event.eventType())))
            .collect(Collectors.groupingBy(this::userOrIp, Collectors.counting()));

        return failedByActor.entrySet().stream()
            .filter(entry -> entry.getValue() >= 5)
            .map(entry -> new AuditAlert(
                "FAILED_ACCESS_SPIKE",
                "HIGH",
                "Múltiplas tentativas negadas",
                "Ator " + entry.getKey() + " teve " + entry.getValue() + " falhas de acesso nos últimos 10 minutos.",
                now,
                Map.of("actor", entry.getKey(), "attempts", entry.getValue(), "windowMinutes", 10)
            ))
            .toList();
    }

    private List<AuditAlert> detectBulkAccess(List<AuditEventDocument> events, Instant now) {
        Instant threshold = now.minus(Duration.ofMinutes(10));
        Map<String, Long> viewsByUser = events.stream()
            .filter(event -> isAfter(event, threshold))
            .filter(event -> BULK_VIEW_EVENT_TYPES.contains(normalize(event.eventType())))
            .filter(event -> event.userId() != null && !event.userId().isBlank())
            .collect(Collectors.groupingBy(AuditEventDocument::userId, Collectors.counting()));

        return viewsByUser.entrySet().stream()
            .filter(entry -> entry.getValue() >= 30)
            .map(entry -> new AuditAlert(
                "BULK_DOCUMENT_ACCESS",
                "MEDIUM",
                "Volume atípico de leitura/download",
                "Usuário " + entry.getKey() + " acessou " + entry.getValue() + " documentos em 10 minutos.",
                now,
                Map.of("userId", entry.getKey(), "count", entry.getValue(), "windowMinutes", 10)
            ))
            .toList();
    }

    private List<AuditAlert> detectMultipleIpsByUser(List<AuditEventDocument> events, Instant now) {
        Instant threshold = now.minus(Duration.ofMinutes(30));
        Map<String, Set<String>> ipsByUser = new HashMap<>();

        for (AuditEventDocument event : events) {
            if (!isAfter(event, threshold) || event.userId() == null || event.userId().isBlank()) {
                continue;
            }
            extractIp(event).ifPresent(ip -> ipsByUser.computeIfAbsent(event.userId(), ignored -> new HashSet<>()).add(ip));
        }

        return ipsByUser.entrySet().stream()
            .filter(entry -> entry.getValue().size() >= 3)
            .map(entry -> new AuditAlert(
                "MULTIPLE_IPS_PER_USER",
                "HIGH",
                "Acesso do mesmo usuário por múltiplos IPs",
                "Usuário " + entry.getKey() + " usou " + entry.getValue().size() + " IPs diferentes em 30 minutos.",
                now,
                Map.of("userId", entry.getKey(), "ips", entry.getValue(), "windowMinutes", 30)
            ))
            .toList();
    }

    private boolean isAfter(AuditEventDocument event, Instant threshold) {
        if (event.occurredAt() == null) {
            return false;
        }
        return Instant.ofEpochMilli(event.occurredAt()).isAfter(threshold);
    }

    private String userOrIp(AuditEventDocument event) {
        if (event.userId() != null && !event.userId().isBlank()) {
            return "user:" + event.userId();
        }
        return extractIp(event).map(ip -> "ip:" + ip).orElse("unknown");
    }

    private Optional<String> extractIp(AuditEventDocument event) {
        List<Object> candidates = Arrays.asList(
            valueFromMap(event.attributes(), "ip"),
            valueFromMap(event.attributes(), "clientIp"),
            valueFromMap(event.attributes(), "remoteIp"),
            valueFromMap(event.metadata(), "ip"),
            valueFromMap(event.metadata(), "clientIp"),
            valueFromMap(event.metadata(), "remoteIp")
        );

        return candidates.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .findFirst();
    }

    private Object valueFromMap(Map<String, Object> map, String key) {
        return map != null ? map.get(key) : null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
