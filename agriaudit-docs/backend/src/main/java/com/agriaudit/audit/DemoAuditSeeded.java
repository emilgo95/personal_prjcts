package com.agriaudit.audit;

import java.util.List;

/**
 * Evento de aplicación (RT-05): se ha sembrado una auditoría de demostración. Lo publica {@code audit}
 * y lo consume {@code findings} para sembrar sus no conformidades, sin que ningún módulo toque las
 * entidades del otro. Síncrono y en la misma transacción que el sembrado.
 *
 * @param nonCompliantControlPointIds puntos de control evaluados como no conformes, en orden de checklist
 */
public record DemoAuditSeeded(AuditRef audit, List<Long> nonCompliantControlPointIds) {
}
