package br.com.dms.audit.controller;

import br.com.dms.audit.model.AuditEventDocument;
import br.com.dms.audit.service.AuditEventQueryService;
import br.com.dms.audit.service.AuditEventSearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import br.com.dms.audit.config.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = AuditEventController.class)
@Import(SecurityConfig.class)
class AuditEventControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditEventQueryService queryService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setupQueryService() {
        AuditEventDocument document = new AuditEventDocument(
            "DOCUMENT_INDEXED",
            Instant.parse("2026-02-16T15:00:00Z").toEpochMilli(),
            Instant.parse("2026-02-16T15:00:01Z").toEpochMilli(),
            "user-1",
            "tenant-a",
            "DOCUMENT",
            "doc-123",
            "contract.pdf",
            Map.of(),
            Map.of()
        );

        Mockito.when(queryService.search(any(), any()))
            .thenReturn(new PageImpl<>(List.of(new AuditEventSearchHit("evt-1", document)), PageRequest.of(0, 50), 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"OWNER", "ADMIN", "REVIEWER", "VIEWER", "DOCUMENT_VIEWER"})
    @DisplayName("should allow access for production roles")
    void searchAllowsProductionRoles(String role) throws Exception {
        mockMvc.perform(get("/v1/audit/events")
                .param("tenantId", "tenant-a")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should deny access for non-authorized role")
    void searchDeniesUnauthorizedRole() throws Exception {
        mockMvc.perform(get("/v1/audit/events")
                .param("tenantId", "tenant-a")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GUEST"))))
            .andExpect(status().isForbidden());

        Mockito.verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("should require authentication")
    void searchRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/v1/audit/events")
                .param("tenantId", "tenant-a"))
            .andExpect(status().isUnauthorized());

        Mockito.verifyNoInteractions(queryService);
    }
}
