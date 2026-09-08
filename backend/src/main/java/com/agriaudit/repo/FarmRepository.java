package com.agriaudit.repo;

import com.agriaudit.domain.Farm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FarmRepository extends JpaRepository<Farm, Long> {
    List<Farm> findByOrganizationIdOrderByLegalNameAsc(Long organizationId);
    Optional<Farm> findByIdAndOrganizationId(Long id, Long organizationId);
}
