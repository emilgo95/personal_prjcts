package com.agriaudit.trace;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ChangesTest {

    @Test
    void soloSeAnotanLosCamposQueCambian() {
        var changes = Changes.create()
                .track("title", "Título", "Antes", "Después")
                .track("area", "Área", "Almacén", "Almacén")
                .track("dueDate", "Fecha", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15))
                .list();

        assertThat(changes).extracting(FieldChange::field).containsExactly("title", "dueDate");
        assertThat(changes.get(0).oldValue()).isEqualTo("Antes");
        assertThat(changes.get(0).newValue()).isEqualTo("Después");
        assertThat(changes.get(1).newValue()).isEqualTo("2026-10-15");
    }

    @Test
    void textoVacioYNuloSeConsideranIguales() {
        assertThat(Changes.create().track("n", "Notas", null, "  ").isEmpty()).isTrue();
        assertThat(Changes.create().track("n", "Notas", "", null).isEmpty()).isTrue();
        assertThat(Changes.create().track("n", "Notas", null, "algo").isEmpty()).isFalse();
    }

    @Test
    void losValoresMuyLargosSeRecortan() {
        var change = Changes.create().track("d", "Descripción", null, "x".repeat(5000)).list().get(0);

        assertThat(change.newValue()).hasSize(Changes.MAX_VALUE_CHARS + 1).endsWith("…");
    }

    @Test
    void unAltaSoloAnotaLosValoresPresentes() {
        var changes = Changes.create().initial("a", "A", "valor").initial("b", "B", null).list();

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).oldValue()).isNull();
    }

    @Test
    void losDatosPersonalesNoGuardanSuValor() {
        var changed = Changes.create().personal("name", "Nombre", "Ana García", "Ana López").list();
        var created = Changes.create().personalInitial("email", "Correo", "ana@x.test").list();

        assertThat(changed.get(0).oldValue()).isEqualTo("(dato personal)");
        assertThat(changed.get(0).newValue()).isEqualTo("(modificado)");
        assertThat(changed.toString()).doesNotContain("Ana");
        assertThat(created.get(0).newValue()).isEqualTo("(dato personal informado)");
        assertThat(created.toString()).doesNotContain("ana@x.test");
        assertThat(Changes.create().personal("name", "Nombre", "Ana", "Ana").isEmpty()).isTrue();
    }
}
