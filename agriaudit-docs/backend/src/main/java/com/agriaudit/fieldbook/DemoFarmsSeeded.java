package com.agriaudit.fieldbook;

import com.agriaudit.fieldbook.FarmDtos.FarmDto;

import java.util.List;

/**
 * Evento de aplicación (RT-05): se han sembrado las explotaciones de demostración de una organización.
 * Lo consume {@code audit} para sembrar sus auditorías de demo. Síncrono y en la misma transacción.
 *
 * @param farms explotaciones en el orden de alta
 */
public record DemoFarmsSeeded(Long organizationId, List<FarmDto> farms) {
}
