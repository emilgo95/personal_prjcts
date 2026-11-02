package com.agriaudit.audit.application;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Buscador de auditorías: sin distinguir mayúsculas ni tildes, y todas las palabras escritas deben aparecer (en
 * cualquier campo y orden), igual que el de explotaciones.
 */
final class AuditSearch {

    /** Longitud máxima del texto buscado; lo que sobra se ignora. */
    static final int MAX_QUERY = 100;

    private AuditSearch() {
    }

    /** Minúsculas y sin tildes, para que «almeria» encuentre «Almería». */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    /** Palabras del texto buscado; vacía si no hay nada que buscar. */
    static List<String> terms(String query) {
        if (query == null) {
            return List.of();
        }
        String q = query.length() > MAX_QUERY ? query.substring(0, MAX_QUERY) : query;
        return Arrays.stream(normalize(q).split("\\s+")).filter(t -> !t.isEmpty()).toList();
    }

    /** Une los campos en un solo texto normalizado en el que buscar. */
    static String text(String... fields) {
        return normalize(Stream.of(fields).filter(Objects::nonNull).collect(Collectors.joining(" ")));
    }

    static boolean matches(String text, List<String> terms) {
        return terms.stream().allMatch(text::contains);
    }
}
