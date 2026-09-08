package com.agriaudit.config;

import com.agriaudit.domain.Audit;
import com.agriaudit.domain.enums.AuditStatus;
import com.agriaudit.repo.AuditRepository;
import com.agriaudit.repo.GeneratedDocumentRepository;
import com.agriaudit.security.AppUserDetailsService;
import com.agriaudit.security.AppUserPrincipal;
import com.agriaudit.service.document.DocumentGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Genera un conjunto de documentos reales (PDF/DOCX/XLSX) para las auditorías de
 * demostración que ya están cerradas, enviadas o en cierre, de forma que el
 * "Centro de documentos" no aparezca vacío en una demo.
 *
 * Se ejecuta después de {@link DemoDataSeeder} y solo para auditorías sin documentos.
 */
@Component
@Order(30)
public class DemoDocumentSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDocumentSeeder.class);

    private final boolean enabled;
    private final String adminEmail;
    private final AuditRepository audits;
    private final AppUserDetailsService userDetailsService;
    private final GeneratedDocumentRepository generatedDocuments;
    private final DocumentGenerationService documentGenerationService;

    public DemoDocumentSeeder(@Value("${agriaudit.demo.seed:true}") boolean enabled,
                              @Value("${agriaudit.admin.email}") String adminEmail,
                              AuditRepository audits, AppUserDetailsService userDetailsService,
                              GeneratedDocumentRepository generatedDocuments,
                              DocumentGenerationService documentGenerationService) {
        this.enabled = enabled;
        this.adminEmail = adminEmail;
        this.audits = audits;
        this.userDetailsService = userDetailsService;
        this.generatedDocuments = generatedDocuments;
        this.documentGenerationService = documentGenerationService;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        AppUserPrincipal principal;
        try {
            principal = (AppUserPrincipal) userDetailsService.loadUserByUsername(adminEmail);
        } catch (Exception e) {
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            int total = 0;
            for (Audit audit : audits.findByOrganizationIdOrderByCreatedAtDesc(principal.getOrganizationId())) {
                if (!isFinished(audit.getStatus())) {
                    continue;
                }
                if (!generatedDocuments.findByAuditIdOrderByGeneratedAtDesc(audit.getId()).isEmpty()) {
                    continue;
                }
                total += generateFor(audit);
            }
            if (total > 0) {
                log.info("Datos de demostración: {} documentos generados", total);
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isFinished(AuditStatus status) {
        return status == AuditStatus.CLOSED || status == AuditStatus.SUBMITTED || status == AuditStatus.CLOSING;
    }

    private int generateFor(Audit audit) {
        List<String[]> docs = switch (audit.getStandard().getCode()) {
            case "GRASP_V2" -> List.of(
                    new String[]{"GRASP_ASSESSMENT", "PDF"},
                    new String[]{"GRASP_ASSESSMENT", "XLSX"},
                    new String[]{"CAPA_REPORT", "DOCX"});
            case "SMETA_6_1" -> List.of(
                    new String[]{"SMETA_REPORT", "PDF"},
                    new String[]{"CHECKLIST", "XLSX"},
                    new String[]{"CAPA_REPORT", "XLSX"});
            default -> List.of(
                    new String[]{"AUDIT_REPORT", "PDF"},
                    new String[]{"NONCONFORMITY_REPORT", "DOCX"},
                    new String[]{"CHECKLIST", "XLSX"},
                    new String[]{"COMPLIANCE_SUMMARY", "PDF"},
                    new String[]{"AUDIT_PLAN", "PDF"});
        };
        int count = 0;
        for (String[] d : docs) {
            try {
                documentGenerationService.generate(audit.getId(), d[0], d[1]);
                count++;
            } catch (Exception e) {
                log.warn("No se pudo generar el documento de demo {}/{} para {}: {}",
                        d[0], d[1], audit.getReference(), e.getMessage());
            }
        }
        return count;
    }
}
