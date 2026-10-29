package com.agriaudit.catalog.infrastructure.persistence;

import com.agriaudit.catalog.domain.StandardChapter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StandardChapterRepository extends JpaRepository<StandardChapter, Long> {
}
