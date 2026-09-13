package com.agriaudit.fieldbook;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public final class FarmDtos {

    private FarmDtos() {
    }

    public record FarmRequest(
            @NotBlank String legalName,
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
    }

    public record FarmDto(
            Long id,
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
    }
}
