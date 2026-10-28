package com.agriaudit.trace;

/**
 * Anota un cambio en la traza. Se ejecuta en la transacción de quien llama: si la operación falla y se revierte,
 * su entrada también.
 */
public interface ChangeRecorder {

    void record(ChangeEntry entry);
}
