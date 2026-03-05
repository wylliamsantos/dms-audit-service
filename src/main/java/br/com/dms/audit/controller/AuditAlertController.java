package br.com.dms.audit.controller;

import br.com.dms.audit.controller.response.AuditAlertListResponse;
import br.com.dms.audit.controller.response.AuditAlertView;
import br.com.dms.audit.service.AuditAlertService;
import br.com.dms.audit.service.AuditAlert;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/v1/audit/alerts")
@PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
public class AuditAlertController {

    private final AuditAlertService auditAlertService;

    public AuditAlertController(AuditAlertService auditAlertService) {
        this.auditAlertService = auditAlertService;
    }

    @GetMapping("/active")
    public ResponseEntity<AuditAlertListResponse> listActive(@RequestParam(name = "tenantId", required = false) String tenantId,
                                                             @RequestHeader(name = "X-Tenant-Id", required = false) String tenantIdHeader) {
        String resolvedTenantId = resolveTenantId(tenantId, tenantIdHeader);
        List<AuditAlert> alerts = auditAlertService.findActiveAlerts(resolvedTenantId);
        List<AuditAlertView> views = alerts.stream()
            .map(alert -> new AuditAlertView(alert.code(), alert.severity(), alert.title(), alert.description(), alert.detectedAt(), alert.context()))
            .toList();
        return ResponseEntity.ok(new AuditAlertListResponse(views, views.size()));
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
}
