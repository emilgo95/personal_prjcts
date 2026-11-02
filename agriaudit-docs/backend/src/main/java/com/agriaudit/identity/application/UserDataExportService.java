package com.agriaudit.identity.application;

import com.agriaudit.common.ConflictException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.identity.infrastructure.persistence.AppUserRepository;
import com.agriaudit.privacy.PersonDataExporter;
import com.agriaudit.privacy.PersonIdentity;
import com.agriaudit.trace.ChangeEntry;
import com.agriaudit.trace.ChangeRecorder;
import com.agriaudit.trace.TraceAction;
import com.agriaudit.trace.TraceEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Exporta los datos que la aplicación guarda sobre un usuario (derecho de acceso). Solo administración. */
@Service
@Transactional
public class UserDataExportService {

    private final AppUserRepository users;
    private final PersonDataExporter exporter;
    private final ChangeRecorder trace;

    public UserDataExportService(AppUserRepository users, PersonDataExporter exporter, ChangeRecorder trace) {
        this.users = users;
        this.exporter = exporter;
        this.trace = trace;
    }

    public byte[] export(Long userId) {
        Long orgId = SecurityUtils.currentOrganizationId();
        var user = users.findById(userId)
                .filter(u -> u.getOrganization().getId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + userId));
        if (user.isBlocked()) {
            throw new ConflictException("Los datos de este usuario están bloqueados: ya no se tratan y no se pueden exportar.");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            exporter.export(orgId, new PersonIdentity(userId, user.getEmail(), user.getFullName()), out);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar la exportación", e);
        }
        trace.record(ChangeEntry.of(TraceEntity.USER, userId, "usuario " + userId, null, TraceAction.GENERATE,
                "Exportó los datos personales del usuario n.º " + userId + " (derecho de acceso)", List.of()));
        return out.toByteArray();
    }
}
