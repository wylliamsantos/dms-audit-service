package br.com.dms.audit.controller;

import br.com.dms.audit.controller.request.AuditEventSearchParams;
import br.com.dms.audit.controller.response.AuditEventSearchResponse;
import br.com.dms.audit.controller.response.AuditEventView;
import br.com.dms.audit.model.AuditEventDocument;
import br.com.dms.audit.service.AuditEventQueryService;
import br.com.dms.audit.service.AuditEventSearchHit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/audit/events")
@PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN','ROLE_REVIEWER','ROLE_VIEWER','ROLE_DOCUMENT_VIEWER')")
public class AuditEventController {

    private static final int EXPORT_MAX_SIZE = 5000;

    private final AuditEventQueryService queryService;
    private final ObjectMapper objectMapper;

    public AuditEventController(AuditEventQueryService queryService, ObjectMapper objectMapper) {
        this.queryService = queryService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<AuditEventSearchResponse> search(@RequestParam(name = "userId", required = false) String userId,
                                                           @RequestParam(name = "tenantId", required = false) String tenantId,
                                                           @RequestHeader(name = "X-Tenant-Id", required = false) String tenantIdHeader,
                                                           @RequestParam(name = "entityId", required = false) String entityId,
                                                           @RequestParam(name = "entityType", required = false) String entityType,
                                                           @RequestParam(name = "eventType", required = false) String eventType,
                                                           @RequestParam(name = "ip", required = false) String ip,
                                                           @RequestParam(name = "occurredAtFrom", required = false)
                                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredAtFrom,
                                                           @RequestParam(name = "occurredAtTo", required = false)
                                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredAtTo,
                                                           @PageableDefault(size = 50) Pageable pageable) {

        AuditEventSearchParams params = buildParams(userId, tenantId, tenantIdHeader, entityId, entityType, eventType, ip, occurredAtFrom, occurredAtTo);
        Page<AuditEventSearchHit> page = queryService.search(params, pageable);

        List<AuditEventView> views = page.getContent().stream()
            .map(this::toView)
            .collect(Collectors.toList());

        AuditEventSearchResponse response = new AuditEventSearchResponse(
            views,
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/export")
    public ResponseEntity<String> export(@RequestParam(name = "format", defaultValue = "json") String format,
                                         @RequestParam(name = "userId", required = false) String userId,
                                         @RequestParam(name = "tenantId", required = false) String tenantId,
                                         @RequestHeader(name = "X-Tenant-Id", required = false) String tenantIdHeader,
                                         @RequestParam(name = "entityId", required = false) String entityId,
                                         @RequestParam(name = "entityType", required = false) String entityType,
                                         @RequestParam(name = "eventType", required = false) String eventType,
                                         @RequestParam(name = "ip", required = false) String ip,
                                         @RequestParam(name = "occurredAtFrom", required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredAtFrom,
                                         @RequestParam(name = "occurredAtTo", required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredAtTo,
                                         @RequestParam(name = "size", defaultValue = "1000") int size) {

        int clampedSize = Math.max(1, Math.min(size, EXPORT_MAX_SIZE));
        Pageable pageable = PageRequest.of(0, clampedSize);

        AuditEventSearchParams params = buildParams(userId, tenantId, tenantIdHeader, entityId, entityType, eventType, ip, occurredAtFrom, occurredAtTo);
        List<AuditEventView> events = queryService.search(params, pageable)
            .getContent()
            .stream()
            .map(this::toView)
            .toList();

        String normalizedFormat = format == null ? "json" : format.trim().toLowerCase();
        if ("csv".equals(normalizedFormat)) {
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("audit-events.csv").build().toString())
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(toCsv(events));
        }

        if (!"json".equals(normalizedFormat)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "format must be json or csv");
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("audit-events.json").build().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(toJson(events));
    }

    private AuditEventSearchParams buildParams(String userId,
                                               String tenantId,
                                               String tenantIdHeader,
                                               String entityId,
                                               String entityType,
                                               String eventType,
                                               String ip,
                                               Instant occurredAtFrom,
                                               Instant occurredAtTo) {
        String resolvedTenantId = resolveTenantId(tenantId, tenantIdHeader);
        return AuditEventSearchParams.of(userId, resolvedTenantId, entityId, entityType, eventType, ip, occurredAtFrom, occurredAtTo);
    }

    private String toJson(List<AuditEventView> events) {
        try {
            return objectMapper.writeValueAsString(events);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "could not serialize JSON export");
        }
    }

    private String toCsv(List<AuditEventView> events) {
        StringBuilder csv = new StringBuilder();
        csv.append("id,eventType,occurredAt,ingestedAt,userId,tenantId,entityType,entityId,filename,ip").append('\n');
        for (AuditEventView event : events) {
            csv.append(csvCell(event.id())).append(',')
                .append(csvCell(event.eventType())).append(',')
                .append(csvCell(event.occurredAt() != null ? event.occurredAt().toString() : null)).append(',')
                .append(csvCell(event.ingestedAt() != null ? event.ingestedAt().toString() : null)).append(',')
                .append(csvCell(event.userId())).append(',')
                .append(csvCell(event.tenantId())).append(',')
                .append(csvCell(event.entityType())).append(',')
                .append(csvCell(event.entityId())).append(',')
                .append(csvCell(event.filename())).append(',')
                .append(csvCell(resolveIp(event)))
                .append('\n');
        }
        return csv.toString();
    }

    private String resolveIp(AuditEventView event) {
        return stringValue(event.attributes(), "ip", "clientIp", "remoteIp", event.metadata());
    }

    private String stringValue(java.util.Map<String, Object> primary,
                               String key,
                               String fallback1,
                               String fallback2,
                               java.util.Map<String, Object> secondary) {
        String value = asNonBlankString(primary != null ? primary.get(key) : null);
        if (value != null) return value;
        value = asNonBlankString(primary != null ? primary.get(fallback1) : null);
        if (value != null) return value;
        value = asNonBlankString(primary != null ? primary.get(fallback2) : null);
        if (value != null) return value;
        value = asNonBlankString(secondary != null ? secondary.get(key) : null);
        if (value != null) return value;
        value = asNonBlankString(secondary != null ? secondary.get(fallback1) : null);
        if (value != null) return value;
        return asNonBlankString(secondary != null ? secondary.get(fallback2) : null);
    }

    private String asNonBlankString(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return null;
    }

    private String csvCell(String raw) {
        if (raw == null) {
            return "";
        }
        String escaped = raw.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String resolveTenantId(String tenantId, String tenantIdHeader) {
        String requestTenantId = StringUtils.hasText(tenantId) ? tenantId.trim() : null;
        String headerTenantId = StringUtils.hasText(tenantIdHeader) ? tenantIdHeader.trim() : null;

        if (headerTenantId != null && requestTenantId != null && !headerTenantId.equals(requestTenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "tenantId mismatch between header and query param");
        }

        String resolvedTenantId = headerTenantId != null ? headerTenantId : requestTenantId;
        if (!StringUtils.hasText(resolvedTenantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId is required (query param or X-Tenant-Id header)");
        }

        return resolvedTenantId;
    }

    private AuditEventView toView(AuditEventSearchHit hit) {
        AuditEventDocument document = hit.document();
        return new AuditEventView(
            hit.id(),
            document.eventType(),
            document.occurredAt() != null ? Instant.ofEpochMilli(document.occurredAt()) : null,
            document.ingestedAt() != null ? Instant.ofEpochMilli(document.ingestedAt()) : null,
            document.userId(),
            document.tenantId(),
            document.entityType(),
            document.entityId(),
            document.filename(),
            document.metadata(),
            document.attributes()
        );
    }
}
