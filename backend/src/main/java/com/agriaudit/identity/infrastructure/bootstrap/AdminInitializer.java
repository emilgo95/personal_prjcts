package com.agriaudit.identity.infrastructure.bootstrap;

import com.agriaudit.identity.domain.AppUser;
import com.agriaudit.identity.domain.Organization;
import com.agriaudit.identity.Role;
import com.agriaudit.identity.infrastructure.persistence.AppUserRepository;
import com.agriaudit.identity.infrastructure.persistence.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea la organización y el usuario administrador inicial a partir de las variables de entorno ADMIN_*.
 * Si el administrador ya existe no se toca: la contraseña elegida por el usuario sobrevive a los reinicios.
 */
@Component
@Order(10)
public class AdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final OrganizationRepository organizations;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminOrgName;

    public AdminInitializer(OrganizationRepository organizations,
                           AppUserRepository users,
                           PasswordEncoder passwordEncoder,
                           @Value("${agriaudit.admin.email}") String adminEmail,
                           @Value("${agriaudit.admin.password}") String adminPassword,
                           @Value("${agriaudit.admin.org-name}") String adminOrgName) {
        this.organizations = organizations;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminOrgName = adminOrgName;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Organization org = organizations.findByName(adminOrgName).orElseGet(() -> {
            Organization o = new Organization();
            o.setName(adminOrgName);
            o.setCountry("España");
            o.setPlan("STANDARD");
            log.info("Creando organización inicial '{}'", adminOrgName);
            return organizations.save(o);
        });

        users.findByEmailIgnoreCase(adminEmail).ifPresentOrElse(existing ->
                log.info("Usuario administrador '{}' ya existe; no se modifica", adminEmail), () -> {
            AppUser admin = new AppUser();
            admin.setOrganization(org);
            admin.setEmail(adminEmail);
            admin.setFullName("Administrador");
            admin.setRole(Role.ADMIN);
            admin.setActive(true);
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            users.save(admin);
            log.info("Usuario administrador '{}' creado", adminEmail);
        });
    }
}
