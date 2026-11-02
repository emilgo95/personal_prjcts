package com.agriaudit.trace;

/** Un campo que cambió: nombre técnico, etiqueta en español, valor anterior y valor nuevo (texto). */
public record FieldChange(String field, String label, String oldValue, String newValue) {
}
