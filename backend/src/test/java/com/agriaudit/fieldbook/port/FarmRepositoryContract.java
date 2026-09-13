package com.agriaudit.fieldbook.port;

import com.agriaudit.fieldbook.domain.Farm;
import com.agriaudit.fieldbook.domain.FarmProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato del puerto {@link FarmRepository}: cualquier adaptador (JPA/PostgreSQL, memoria, otro motor)
 * debe pasar esta batería (RT-03). Cambiar de base de datos = escribir un adaptador que la pase.
 */
public abstract class FarmRepositoryContract {

    protected abstract FarmRepository repository();

    /** Dos organizaciones distintas existentes en el almacenamiento. */
    protected abstract long organizationA();

    protected abstract long organizationB();

    @Test
    void guardaYRecuperaPorIdYOrganizacion() {
        Farm saved = repository().save(Farm.register(organizationA(), profile("Finca Contrato S.L.")));

        assertThat(saved.id()).isNotNull();
        assertThat(repository().findById(saved.id(), organizationA()))
                .hasValueSatisfying(f -> assertThat(f.profile()).isEqualTo(saved.profile()));
    }

    @Test
    void noDevuelveExplotacionesDeOtraOrganizacion() {
        Farm saved = repository().save(Farm.register(organizationA(), profile("Finca Aislada")));

        assertThat(repository().findById(saved.id(), organizationB())).isEmpty();
        assertThat(repository().findByIds(organizationB(), List.of(saved.id()))).isEmpty();
        assertThat(repository().findByOrganization(organizationB())).extracting(Farm::id).doesNotContain(saved.id());
    }

    @Test
    void persisteLosCambiosDePerfil() {
        Farm farm = repository().save(Farm.register(organizationA(), profile("Nombre Antiguo")));
        farm.updateProfile(profile("Nombre Nuevo"));
        repository().save(farm);

        assertThat(repository().findById(farm.id(), organizationA()).orElseThrow().profile().legalName())
                .isEqualTo("Nombre Nuevo");
    }

    @Test
    void listaOrdenadaPorRazonSocialYCuenta() {
        long before = repository().countByOrganization(organizationA());
        repository().save(Farm.register(organizationA(), profile("Zeta Agrícola")));
        repository().save(Farm.register(organizationA(), profile("Alfa Agrícola")));

        List<String> names = repository().findByOrganization(organizationA()).stream()
                .map(f -> f.profile().legalName()).toList();
        assertThat(names).isSorted();
        assertThat(repository().countByOrganization(organizationA())).isEqualTo(before + 2);
        assertThat(repository().existsAny()).isTrue();
    }

    @Test
    void borraSoloDentroDeSuOrganizacion() {
        Farm farm = repository().save(Farm.register(organizationA(), profile("Finca Borrable")));
        repository().delete(farm);

        assertThat(repository().findById(farm.id(), organizationA())).isEmpty();
    }

    protected static FarmProfile profile(String legalName) {
        return new FarmProfile(legalName, null, "4049928000000", "Camino 1", "Murcia", "Región de Murcia",
                "España", new BigDecimal("37.990000"), new BigDecimal("-1.130000"), new BigDecimal("12.50"),
                "Lechuga", null, "Contacto", "contacto@finca.example", null, null);
    }
}
