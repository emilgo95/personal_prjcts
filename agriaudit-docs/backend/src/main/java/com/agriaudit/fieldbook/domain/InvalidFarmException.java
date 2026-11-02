package com.agriaudit.fieldbook.domain;

import com.agriaudit.common.BadRequestException;

/** Datos de explotación que incumplen una invariante del dominio. */
public class InvalidFarmException extends BadRequestException {

    public InvalidFarmException(String message) {
        super(message);
    }
}
