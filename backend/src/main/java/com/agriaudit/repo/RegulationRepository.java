package com.agriaudit.repo;

import com.agriaudit.domain.Regulation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegulationRepository extends JpaRepository<Regulation, Long> {
    Optional<Regulation> findByCode(String code);
    List<Regulation> findAllByOrderByJurisdictionAscTitleAsc();
}
