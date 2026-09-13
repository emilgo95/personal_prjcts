package com.agriaudit.fieldbook.infrastructure.demo;

import com.agriaudit.fieldbook.DemoFarmsSeeded;
import com.agriaudit.fieldbook.FarmDirectory;
import com.agriaudit.fieldbook.FarmDtos.FarmDto;
import com.agriaudit.fieldbook.FarmDtos.FarmRequest;
import com.agriaudit.identity.OrganizationDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Explotaciones de demostración de varios países. Es el primer eslabón del juego de datos de demo:
 * publica {@link DemoFarmsSeeded} y {@code audit} y {@code findings} siembran lo suyo en cadena.
 *
 * <p>Idempotente: no hace nada si ya existe alguna explotación. Se desactiva con
 * {@code agriaudit.demo.seed=false} ({@code AGRIAUDIT_DEMO_SEED=false}).
 */
@Component
@Order(20)
class FarmDemoSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FarmDemoSeeder.class);

    private final boolean enabled;
    private final String orgName;
    private final OrganizationDirectory organizations;
    private final FarmDirectory farms;
    private final ApplicationEventPublisher events;

    FarmDemoSeeder(@Value("${agriaudit.demo.seed:true}") boolean enabled,
                   @Value("${agriaudit.admin.org-name}") String orgName,
                   OrganizationDirectory organizations, FarmDirectory farms, ApplicationEventPublisher events) {
        this.enabled = enabled;
        this.orgName = orgName;
        this.organizations = organizations;
        this.farms = farms;
        this.events = events;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        if (farms.anyFarmExists()) {
            log.info("Datos de demostración ya presentes; se omite el sembrado");
            return;
        }
        Long orgId = organizations.findOrganizationIdByName(orgName)
                .or(organizations::anyOrganizationId).orElse(null);
        if (orgId == null) {
            log.warn("No hay organización disponible; se omite el sembrado de demostración");
            return;
        }
        log.info("Sembrando datos de demostración para la organización '{}'",
                organizations.organizationName(orgId).orElse(orgName));

        List<FarmDto> seeded = seedFarms(orgId);
        log.info("Datos de demostración: {} explotaciones", seeded.size());
        events.publishEvent(new DemoFarmsSeeded(orgId, seeded));
    }

    private List<FarmDto> seedFarms(Long orgId) {
        List<FarmDto> list = new ArrayList<>();
        list.add(farm(orgId, "Frutas del Sur S.L.", "Frutas del Sur", "4049928111111",
                "Ctra. Cartaya km 4", "Cartaya", "Huelva", "España", "37.283000", "-7.148000",
                "85.00", "Fresa, Frambuesa, Arándano", "Túnel 1-12, Parcela norte",
                "Lucía Márquez", "lmarquez@frutasdelsur.example", "+34 959 000 111"));
        list.add(farm(orgId, "Cítricos Valencianos S.C.V.", "Citriva", "4049928222222",
                "Camí de l'Horta 22", "Carcaixent", "Valencia", "España", "39.121000", "-0.449000",
                "210.00", "Naranja, Mandarina", "Sectores A-F",
                "Vicent Aparicio", "vaparicio@citriva.example", "+34 962 000 222"));
        list.add(farm(orgId, "AgroExport Perú S.A.C.", "AgroExport Perú", "4049928333333",
                "Fundo San José, Panamericana Sur km 300", "Ica", "Ica", "Perú", "-14.087000", "-75.760000",
                "540.00", "Uva de mesa, Arándano, Palta", "Fundos San José, El Carmen y La Huaca",
                "Rosa Quispe", "rquispe@agroexportperu.example", "+51 56 000 333"));
        list.add(farm(orgId, "Golden Farms Ltd", "Golden Farms", "4049928444444",
                "R45 Farm Road", "Ceres", "Western Cape", "Sudáfrica", "-33.363000", "19.310000",
                "320.00", "Manzana, Pera", "Blocks 1-18",
                "Pieter van Wyk", "pvw@goldenfarms.example", "+27 23 000 444"));
        list.add(farm(orgId, "Coopérative Agricole Souss", "Coop Souss", "4049928555555",
                "Route de Taroudant, Aït Melloul", "Agadir", "Souss-Massa", "Marruecos", "30.334000", "-9.497000",
                "140.00", "Tomate, Pimiento, Calabacín", "Serres A1-A9",
                "Khadija Amrani", "kamrani@coopsouss.example", "+212 528 000 555"));
        return list;
    }

    private FarmDto farm(Long orgId, String legal, String trade, String reg, String address, String city,
                         String region, String country, String lat, String lon, String hectares,
                         String crops, String areas, String contact, String email, String phone) {
        return farms.registerFarm(orgId, new FarmRequest(legal, trade, reg, address, city, region, country,
                new BigDecimal(lat), new BigDecimal(lon), new BigDecimal(hectares), crops, areas,
                contact, email, phone, null));
    }
}
