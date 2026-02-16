package br.com.dms.audit.controller;

import br.com.dms.audit.controller.request.AuditEventSearchParams;
import br.com.dms.audit.controller.response.AuditEventSearchResponse;
import br.com.dms.audit.controller.response.AuditEventView;
import br.com.dms.audit.model.AuditEventDocument;
import br.com.dms.audit.service.AuditEventQueryService;
import br.com.dms.audit.service.AuditEventSearchHit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/audit/events")
public class AuditEventController {

    private final AuditEventQueryService queryService;

    public AuditEventController(AuditEventQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<AuditEventSearchResponse> search(@RequestParam(name = "userId", required = false) String userId,
                                                           @RequestParam(name = "tenantId", required = false) String tenantId,
                                                           @RequestParam(name = "entityId", required = false) String entityId,
                                                           @RequestParam(name = "entityType", required = false) String entityType,
                                                           @RequestParam(name = "eventType", required = false) String eventType,
                                                           @RequestParam(name = "occurredAtFrom", required = false)
                                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredAtFrom,
                                                           @RequestParam(name = "occurredAtTo", required = false)
                                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredAtTo,
                                                           @PageableDefault(size = 50) Pageable pageable) {

        if (!StringUtils.hasText(tenantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId is required");
        }

        AuditEventSearchParams params = AuditEventSearchParams.of(userId, tenantId, entityId, entityType, eventType, occurredAtFrom, occurredAtTo);
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
