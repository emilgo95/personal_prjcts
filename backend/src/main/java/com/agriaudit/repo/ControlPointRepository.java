package com.agriaudit.repo;

import com.agriaudit.domain.ControlPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ControlPointRepository extends JpaRepository<ControlPoint, Long> {

    @Query("""
            select cp from ControlPoint cp
            join fetch cp.chapter ch
            join ch.standard s
            where s.code = :standardCode
            order by ch.sortOrder asc, cp.sortOrder asc
            """)
    List<ControlPoint> findByStandardCode(@Param("standardCode") String standardCode);

    Optional<ControlPoint> findByChapterStandardCodeAndRef(String standardCode, String ref);
}
