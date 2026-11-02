package com.agriaudit.trace.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceCryptoTest {

    private final TraceCrypto crypto = new TraceCrypto("una-clave-de-prueba-suficientemente-larga-0123456789");

    @Test
    void laHuellaEsSha512EnHexadecimal() {
        // SHA-512("abc"), valor de referencia del estándar.
        assertThat(crypto.hash("abc")).isEqualTo(
                "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd"
                        + "454d4423643ce80e2a9ac94fa54ca49f");
        assertThat(crypto.hash("abc")).hasSize(128);
    }

    @Test
    void laFirmaDependeDeLaClave() {
        var otra = new TraceCrypto("otra-clave-distinta-de-prueba-0123456789abcdef");

        assertThat(crypto.sign("huella")).hasSize(128).isEqualTo(crypto.sign("huella"));
        assertThat(crypto.sign("huella")).isNotEqualTo(otra.sign("huella"));
    }

    @Test
    void elTextoCanonicoNoPermiteAmbiguedades() {
        // Mover un carácter de un campo al siguiente no puede dar el mismo texto.
        assertThat(TraceCrypto.canonical("ab", "c")).isNotEqualTo(TraceCrypto.canonical("a", "bc"));
        assertThat(TraceCrypto.canonical("a", null)).isNotEqualTo(TraceCrypto.canonical("a", "x"));
    }
}
