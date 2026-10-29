package com.agriaudit.trace;

/**
 * Anota un cambio en la traza. Se ejecuta en la transacción de quien llama: si la operación falla y se revierte,
 * su entrada también.
 */
public interface ChangeRecorder {

    void record(ChangeEntry entry);

    /** Anota un cambio hecho por un proceso interno (sin usuario autenticado), con el actor indicado. */
    void recordAs(TraceActor actor, ChangeEntry entry);
}
