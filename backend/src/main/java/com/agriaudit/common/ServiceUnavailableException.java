package com.agriaudit.common;

/**
 * Una dependencia externa opcional (proveedor de IA, OCR…) no está disponible (HTTP 503). El flujo manual
 * debe seguir funcionando: quien la lanza no debe dejar ninguna operación de negocio a medias (RS-IA-10).
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
