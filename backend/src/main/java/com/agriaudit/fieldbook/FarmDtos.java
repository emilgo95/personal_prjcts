package com.agriaudit.fieldbook;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class FarmDtos {

    private FarmDtos() {
    }

    public record FarmRequest(
            @NotBlank @Size(max = 250) String legalName,
            @Size(max = 250) String tradeName,
            @Size(max = 100) String registrationNumber,
            @Size(max = 300) String address,
            @Size(max = 150) String city,
            @Size(max = 150) String region,
            @Size(max = 100) String country,
            @Digits(integer = 3, fraction = 6) BigDecimal latitude,
            @Digits(integer = 3, fraction = 6) BigDecimal longitude,
            @Digits(integer = 10, fraction = 2) BigDecimal totalHectares,
            @Size(max = 5000) String crops,
            @Size(max = 5000) String productionAreas,
            @Size(max = 200) String contactName,
            @Email @Size(max = 200) String contactEmail,
            @Size(max = 60) String contactPhone,
            @Size(max = 5000) String notes) {
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
