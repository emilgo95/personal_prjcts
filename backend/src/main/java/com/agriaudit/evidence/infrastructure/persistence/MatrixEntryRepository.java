package com.agriaudit.evidence.infrastructure.persistence;

import com.agriaudit.evidence.infrastructure.persistence.MatrixEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatrixEntryRepository extends JpaRepository<MatrixEntry, Long> {

    long countByOrganizationId(Long organizationId);

    List<MatrixEntry> findByOrganizationIdOrderByTypeCodeAscStandardCodeAscControlRefAsc(Long organizationId);

    List<MatrixEntry> findByOrganizationIdAndStandardCode(Long organizationId, String standardCode);

    Optional<MatrixEntry> findByIdAndOrganizationId(Long id, Long organizationId);

    boolean existsByOrganizationIdAndTypeCodeAndStandardCodeAndControlRef(Long organizationId, String typeCode,
                                                                          String standardCode, String controlRef);

    /** Copia el borrador de partida (filas sin organización) a la organización indicada. */
    @Modifying
    @Query(value = "INSERT INTO evidence.type_control_points (organization_id, type_code, standard_code, control_ref, status) "
            + "SELECT :orgId, type_code, standard_code, control_ref, 'DRAFT' FROM evidence.type_control_points "
            + "WHERE organization_id IS NULL ON CONFLICT DO NOTHING", nativeQuery = true)
    int copyBaseTo(@Param("orgId") Long organizationId);
}
