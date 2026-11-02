package com.agriaudit.trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Constructor de la lista de campos que cambiaron: solo se anotan los que difieren entre antes y después. */
public final class Changes {

    static final int MAX_VALUE_CHARS = 2000;

    private final List<FieldChange> changes = new ArrayList<>();

    private Changes() {
    }

    public static Changes create() {
        return new Changes();
    }

    /** Anota el campo si su valor cambió (texto vacío y nulo se consideran iguales). */
    public Changes track(String field, String label, Object before, Object after) {
        String oldText = text(before);
        String newText = text(after);
        if (!Objects.equals(oldText, newText)) {
            changes.add(new FieldChange(field, label, oldText, newText));
        }
        return this;
    }

    /** Anota un valor inicial (alta): sin «antes». */
    public Changes initial(String field, String label, Object value) {
        String newText = text(value);
        if (newText != null) {
            changes.add(new FieldChange(field, label, null, newText));
        }
        return this;
    }

    /**
     * Anota que un dato personal cambió SIN guardar su valor (ni el anterior ni el nuevo): la traza es inalterable y
     * no debe retener datos que luego haya que bloquear o suprimir.
     */
    public Changes personal(String field, String label, Object before, Object after) {
        if (!Objects.equals(text(before), text(after))) {
            changes.add(text(before) == null
                    ? new FieldChange(field, label, null, "(dato personal informado)")
                    : new FieldChange(field, label, "(dato personal)", text(after) == null ? "(eliminado)" : "(modificado)"));
        }
        return this;
    }

    /** Alta de un dato personal: consta que se informó, no su valor. */
    public Changes personalInitial(String field, String label, Object value) {
        if (text(value) != null) {
            changes.add(new FieldChange(field, label, null, "(dato personal informado)"));
        }
        return this;
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    public List<FieldChange> list() {
        return List.copyOf(changes);
    }

    static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return null;
        }
        return s.length() > MAX_VALUE_CHARS ? s.substring(0, MAX_VALUE_CHARS) + "…" : s;
    }
}
