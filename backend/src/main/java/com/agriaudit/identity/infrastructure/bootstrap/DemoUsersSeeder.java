package com.agriaudit.identity.infrastructure.bootstrap;

import com.agriaudit.identity.OrganizationDirectory;
import com.agriaudit.identity.Role;
import com.agriaudit.identity.UserProvisioning;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Usuarios de demostración (auditora y lector) en la organización inicial. Idempotente: si el correo ya
 * existe no hace nada. Se desactiva con {@code agriaudit.demo.seed=false}.
 */
@Component
@Order(15)
class DemoUsersSeeder implements CommandLineRunner {

    private final boolean enabled;
    private final String orgName;
    private final OrganizationDirectory organizations;
    private final UserProvisioning users;

    DemoUsersSeeder(@Value("${agriaudit.demo.seed:true}") boolean enabled,
                    @Value("${agriaudit.admin.org-name}") String orgName,
                    OrganizationDirectory organizations, UserProvisioning users) {
        this.enabled = enabled;
        this.orgName = orgName;
        this.organizations = organizations;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        organizations.findOrganizationIdByName(orgName).or(organizations::anyOrganizationId).ifPresent(orgId -> {
            users.ensureUser(orgId, "ana.auditora@demo.test", "Ana Auditora", Role.AUDITOR, "Demo1234!");
            users.ensureUser(orgId, "carlos.lector@demo.test", "Carlos Lector", Role.VIEWER, "Demo1234!");
        });
    }
}
