package com.agriaudit.catalog.infrastructure.persistence;

import com.agriaudit.catalog.domain.ControlPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ControlPointRepository extends JpaRepository<ControlPoint, Long> {

    @Query("""
            select cp from ControlPoint cp
            join fetch cp.chapter ch
            join fetch ch.standard s
            where s.code = :standardCode and (s.organizationId is null or s.organizationId = :orgId)
            order by ch.sortOrder asc, cp.sortOrder asc
            """)
    List<ControlPoint> findByStandardCode(@Param("standardCode") String standardCode, @Param("orgId") Long organizationId);

    @Query("""
            select cp from ControlPoint cp
            join fetch cp.chapter ch
            join fetch ch.standard
            where cp.id = :id
            """)
    Optional<ControlPoint> findWithChapterById(@Param("id") Long id);

    @Query("""
            select cp from ControlPoint cp
            join fetch cp.chapter ch
            join fetch ch.standard
            where cp.id in :ids
            order by ch.sortOrder asc, cp.sortOrder asc
            """)
    List<ControlPoint> findWithChapterByIdIn(@Param("ids") Collection<Long> ids);
}
