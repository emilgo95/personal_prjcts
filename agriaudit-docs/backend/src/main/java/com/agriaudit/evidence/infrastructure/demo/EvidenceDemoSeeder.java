package com.agriaudit.evidence.infrastructure.demo;

import com.agriaudit.evidence.infrastructure.persistence.EvidenceRecord;
import com.agriaudit.evidence.infrastructure.persistence.EvidenceRecordRepository;
import com.agriaudit.fieldbook.DemoFarmsSeeded;
import com.agriaudit.fieldbook.FarmDtos.FarmDto;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Registros de evidencia de demostración para las explotaciones sembradas, con fechas relativas a hoy: así el panel de
 * salud muestra evidencias vigentes, por renovar, vencidas, desfavorables y huecos, y el checklist ofrece sugerencias.
 */
@Component
class EvidenceDemoSeeder {

    /** Tipo, título, días desde la emisión hasta hoy, días de vigencia desde la emisión (0 = sin caducidad), resultado. */
    private record Tpl(String type, String title, int issuedDaysAgo, int validDays, String outcome) {
    }

    private static final List<List<Tpl>> BY_FARM = List.of(
            List.of(new Tpl("WATER_ANALYSIS", "Análisis microbiológico del agua de riego", 60, 365, "FAVORABLE"),
                    new Tpl("RISK_ASSESSMENT", "Evaluación de riesgos del sitio", 400, 365, "FAVORABLE"),
                    new Tpl("EQUIPMENT_INSPECTION", "Inspección del equipo de aplicación", 340, 365, "FAVORABLE"),
                    new Tpl("TRAINING", "Plan anual de formación", 120, 365, "UNRATED")),
            List.of(new Tpl("WATER_ANALYSIS", "Análisis del agua de lavado", 30, 365, "FAVORABLE"),
                    new Tpl("RISK_ASSESSMENT", "Evaluación de riesgos laborales", 90, 730, "FAVORABLE"),
                    new Tpl("FIRST_AID", "Certificados de primeros auxilios", 200, 1095, "FAVORABLE"),
                    new Tpl("CHEMICALS_INVENTORY", "Inventario y fichas de seguridad", 45, 365, "UNRATED")),
            List.of(new Tpl("WATER_ANALYSIS", "Análisis del agua de riego (parámetros fuera de límite)", 10, 365, "UNFAVORABLE"),
                    new Tpl("SOIL_ANALYSIS", "Análisis de suelo de la finca", 150, 730, "FAVORABLE"),
                    new Tpl("RESIDUE_ANALYSIS", "Boletín de residuos de plaguicidas", 70, 180, "FAVORABLE")),
            List.of(new Tpl("RISK_ASSESSMENT", "Evaluación de riesgos del sitio", 20, 365, "FAVORABLE"),
                    new Tpl("WASTE_MANAGEMENT", "Plan de gestión de residuos y contrato con gestor", 100, 0, "UNRATED"),
                    new Tpl("FACILITIES_INSPECTION", "Inspección de instalaciones de bienestar", 25, 365, "FAVORABLE")));

    private final EvidenceRecordRepository records;

    EvidenceDemoSeeder(EvidenceRecordRepository records) {
        this.records = records;
    }

    @EventListener
    @Transactional
    public void on(DemoFarmsSeeded event) {
        LocalDate today = LocalDate.now();
        int i = 0;
        for (FarmDto farm : event.farms()) {
            if (i >= BY_FARM.size()) {
                break;
            }
            for (Tpl t : BY_FARM.get(i)) {
                EvidenceRecord r = new EvidenceRecord(event.organizationId(), farm.id(), t.type(), null);
                LocalDate issued = today.minusDays(t.issuedDaysAgo());
                r.setTitle(t.title());
                r.setIssuedOn(issued);
                r.setValidUntil(t.validDays() == 0 ? null : issued.plusDays(t.validDays()));
                r.setOutcome(t.outcome());
                records.save(r);
            }
            i++;
        }
    }
}
