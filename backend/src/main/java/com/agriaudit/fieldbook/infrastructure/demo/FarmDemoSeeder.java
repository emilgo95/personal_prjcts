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
 * Explotaciones de demostración de la Región de Murcia. Es el primer eslabón del juego de datos de demo:
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
        // Empresas ficticias de la Región de Murcia (perfil real del sector; GGN del rango de pruebas 409999).
        list.add(farm(orgId, "Hortícolas del Segura, S.L.", "Hortícolas del Segura", "4099990000017",
                "Ctra. de Fortuna km 4", "Molina de Segura", "Región de Murcia", "España", "38.054000", "-1.207000",
                "420.00", "Lechuga, Brócoli, Apio", "Molina de Segura, Fortuna y Abanilla",
                "Lucía Marín", "calidad@horticolasdelsegura.example", "+34 968 000 001"));
        list.add(farm(orgId, "Campo de Fortuna, S. Coop.", "Campo de Fortuna", "4099990000024",
                "Camino de los Baños s/n", "Fortuna", "Región de Murcia", "España", "38.181000", "-1.128000",
                "640.00", "Pimiento, Tomate, Calabacín", "Invernaderos y aire libre, 38 socios",
                "Antonio Ruiz Peñas", "sgc@campodefortuna.example", "+34 968 000 002"));
        list.add(farm(orgId, "Frutas Valle del Guadalentín, S.A.", "Frutas del Guadalentín", "4099990000031",
                "Pol. Ind. Saprelorca, calle 3", "Lorca", "Región de Murcia", "España", "37.679000", "-1.700000",
                "310.00", "Melocotón, Nectarina, Ciruela", "Central de fruta de hueso, 22 productores",
                "Marta Soler", "calidad@fvguadalentin.example", "+34 968 000 003"));
        list.add(farm(orgId, "AgroPacheco, S.A.T. 99001", "AgroPacheco", "4099990000048",
                "Av. de la Estación 21", "Torre Pacheco", "Región de Murcia", "España", "37.743000", "-0.955000",
                "890.00", "Melón, Sandía, Lechuga iceberg", "SAT con confección propia, 45 socios",
                "Juan Conesa", "calidad@agropacheco.example", "+34 968 000 004"));
        list.add(farm(orgId, "BioCampo Cartagena, S.L.", "BioCampo", "4099990000055",
                "Finca Los Nietos", "Cartagena", "Región de Murcia", "España", "37.630000", "-0.870000",
                "150.00", "Tomate rama, Pimiento, Hoja baby", "Producción 100 % ecológica",
                "Nerea Gil", "bio@biocampocartagena.example", "+34 968 000 005"));
        list.add(farm(orgId, "Uva del Noroeste, S.L.", "Uva del Noroeste", "4099990000062",
                "Ctra. de Cañara km 2", "Cehegín", "Región de Murcia", "España", "38.092000", "-1.803000",
                "240.00", "Uva de mesa sin semilla", "Parrales en malla, 3 fincas",
                "Pedro Fernández", "calidad@uvadelnoroeste.example", "+34 968 000 006"));
        list.add(farm(orgId, "Cítricos Vega Media, S. Coop.", "Cítricos Vega Media", "4099990000079",
                "Calle Mayor 88", "Ceuti", "Región de Murcia", "España", "38.070000", "-1.270000",
                "1120.00", "Limón, Pomelo, Naranja", "Cooperativa cítricola, 120 socios",
                "Carmen Molina", "sgc@citricosvegamedia.example", "+34 968 000 007"));
        list.add(farm(orgId, "Brócoli Ibérico, S.L.", "Brócoli Ibérico", "4099990000093",
                "Camino Viejo de Cartagena km 9", "Fuente Álamo", "Región de Murcia", "España", "37.722000", "-1.116000",
                "760.00", "Brócoli, Coliflor, Romanesco", "Productor-exportador de brásicas",
                "David Hernández", "calidad@brocoliiberico.example", "+34 968 000 008"));
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
