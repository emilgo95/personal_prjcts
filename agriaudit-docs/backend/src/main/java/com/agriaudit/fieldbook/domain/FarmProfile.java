package com.agriaudit.fieldbook.domain;

import java.math.BigDecimal;

/**
 * Datos descriptivos de una explotación (value object). Garantiza las invariantes de forma:
 * razón social obligatoria, superficie no negativa y coordenadas dentro de rango.
 */
public record FarmProfile(
        String legalName,
        String tradeName,
        String registrationNumber,
        String address,
        String city,
        String region,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal totalHectares,
        String crops,
        String productionAreas,
        String contactName,
        String contactEmail,
        String contactPhone,
        String notes) {

    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

    public FarmProfile {
        if (legalName == null || legalName.isBlank()) {
            throw new InvalidFarmException("La razón social de la explotación es obligatoria");
        }
        if (totalHectares != null && totalHectares.signum() < 0) {
            throw new InvalidFarmException("La superficie total no puede ser negativa");
        }
        if (latitude != null && latitude.abs().compareTo(MAX_LATITUDE) > 0) {
            throw new InvalidFarmException("La latitud debe estar entre -90 y 90");
        }
        if (longitude != null && longitude.abs().compareTo(MAX_LONGITUDE) > 0) {
            throw new InvalidFarmException("La longitud debe estar entre -180 y 180");
        }
    }
}
