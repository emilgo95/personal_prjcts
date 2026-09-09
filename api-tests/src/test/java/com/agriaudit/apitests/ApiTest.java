package com.agriaudit.apitests;

import com.intuit.karate.junit5.Karate;

/**
 * Punto de entrada de las pruebas de API. Ejecuta todos los `.feature` que
 * cuelgan de este paquete en el classpath de test.
 *
 * <p>La URL de la API y las credenciales se resuelven en {@code karate-config.js}
 * a partir de propiedades del sistema o variables de entorno
 * ({@code API_BASE_URL}, {@code API_ADMIN_EMAIL}, {@code API_ADMIN_PASSWORD}).
 */
class ApiTest {

    @Karate.Test
    Karate all() {
        return Karate.run().relativeTo(getClass());
    }
}
