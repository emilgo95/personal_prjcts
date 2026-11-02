package com.agriaudit.privacy;

import java.util.List;

/**
 * Inventario técnico de dónde guarda AgriAudit Docs datos de personas. No es el registro de actividades de tratamiento
 * (art. 30 RGPD), que debe redactar el responsable con su asesor: es la base técnica para hacerlo.
 */
public final class PersonalDataInventory {

    /**
     * @param fields columnas reales de la base de datos ({@code esquema.tabla.columna}); un test comprueba que existen
     * @param notes  límites o salvedades que conviene conocer
     */
    public record Item(String category, String data, String whose, List<String> fields, String visibleTo,
                       String retention, String notes) {
    }

    private PersonalDataInventory() {
    }

    public static List<Item> items() {
        return List.of(
                new Item("Cuenta de usuario", "Correo, nombre, rol y contraseña (cifrada)", "Personas con cuenta",
                        List.of("identity.app_users.email", "identity.app_users.full_name", "identity.app_users.role",
                                "identity.app_users.password_hash"),
                        "Administración; el nombre aparece donde la persona actúa",
                        "Mientras exista la cuenta; al bloquear se sustituye por «Usuario bloqueado»", null),
                new Item("Datos de usuarios bloqueados", "Correo y nombre originales", "Personas con datos bloqueados",
                        List.of("identity.blocked_identity.email", "identity.blocked_identity.full_name"),
                        "Nadie (reservados, no se tratan)",
                        "Hasta el plazo de conservación de la organización; después se destruyen solos", null),
                new Item("Contacto de explotaciones", "Nombre, correo y teléfono del contacto", "Contacto del socio o explotación",
                        List.of("fieldbook.farms.contact_name", "fieldbook.farms.contact_email", "fieldbook.farms.contact_phone"),
                        "Toda la organización", "Mientras exista la explotación",
                        "No son usuarios, así que el bloqueo de usuarios no los alcanza; se editan o borran en la explotación"),
                new Item("Auditor jefe y equipo auditor", "Nombres", "Auditores",
                        List.of("audit.audits.lead_auditor", "audit.audits.audit_team"),
                        "Quien ve la auditoría", "Mientras exista la auditoría; al bloquear se sustituyen", null),
                new Item("Quién evaluó cada punto", "Nombre o correo", "Auditores",
                        List.of("audit.audit_item_results.assessed_by"),
                        "Quien ve la auditoría", "Mientras exista la auditoría; al bloquear se sustituye", null),
                new Item("Responsable y verificador de acciones correctivas", "Nombre", "Responsables y verificadores",
                        List.of("findings.corrective_actions.responsible_person", "findings.corrective_actions.verified_by"),
                        "Quien ve la auditoría", "Mientras exista la acción; al bloquear se sustituye",
                        "Se sustituye cuando coincide exactamente con el nombre de un usuario bloqueado"),
                new Item("Evidencias adjuntas", "Fotos y escaneados que pueden mostrar personas o nombres; quién los subió",
                        "Personas que aparecen y quien sube", List.of("storage.attachments.uploaded_by",
                                "storage.attachments.contains_personal_data"),
                        "Quien ve la auditoría; la marca de datos personales solo administración, calidad y quien subió",
                        "Mientras exista el adjunto; al bloquear se sustituye quién lo subió",
                        "El contenido del fichero no se modifica: la marca solo avisa de que contiene datos personales"),
                new Item("Documentos generados", "Quién los generó; el propio documento puede incluir nombres (p. ej. auditor jefe)",
                        "Auditores y personas citadas", List.of("documents.generated_documents.generated_by"),
                        "Quien ve la auditoría", "Mientras exista el documento",
                        "Los documentos ya generados (PDF, Word, Excel) no se reescriben al bloquear a una persona"),
                new Item("Estándares importados", "Quién los importó", "Usuarios que importan",
                        List.of("catalog.audit_standards.imported_by"), "Organización",
                        "Mientras exista el estándar; al bloquear se sustituye", null),
                new Item("Propuestas de IA", "Quién revisó cada propuesta", "Usuarios que revisan",
                        List.of("ai.ai_proposals.reviewed_by"), "Quien ve la auditoría",
                        "Mientras exista la propuesta; al bloquear se sustituye",
                        "La IA está desactivada por defecto; si se activa, los textos de evidencia se envían al proveedor configurado"),
                new Item("Evidencias reutilizables", "Documentos de evidencia (análisis, evaluaciones, certificados…) que pueden incluir nombres; quién dio de alta el registro (código de usuario)",
                        "Personas que aparecen en los documentos y quien registra",
                        List.of("evidence.evidence_records.created_by_user_id", "evidence.evidence_records.contains_personal_data"),
                        "Toda la organización (el registro); el documento, quien ve el registro",
                        "Mientras exista el registro",
                        "El contenido del documento no se modifica: la marca solo avisa de que contiene datos personales. Los listados de trabajadores no deben subirse hasta hacer la evaluación de impacto"),
                new Item("Historial de cambios", "Código de usuario y rol de quien hizo cada cambio", "Usuarios",
                        List.of("trace.change_log.user_id", "trace.change_log.user_role"),
                        "Administración y calidad; cada auditor, el de sus auditorías",
                        "Inalterable: no se borra", "El nombre se busca al mostrarlo, así que un usuario bloqueado aparece como «Usuario bloqueado»"),
                new Item("Historial de cambios anterior al formato actual", "Nombre y correo de quien hizo el cambio",
                        "Usuarios (entradas antiguas)", List.of("trace.change_log.username", "trace.change_log.user_full_name"),
                        "No se muestran (se resuelven por código de usuario)", "Inalterable: no se borra",
                        "Las entradas anteriores siguen guardando el nombre y el correo en la base de datos; borrarlos rompería la cadena de huellas. Consultar con el especialista")
        );
    }
}
