package com.agriaudit.repo;

import com.agriaudit.domain.DocumentTemplate;
import com.agriaudit.domain.enums.DocumentFormat;
import com.agriaudit.domain.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, Long> {

    Optional<DocumentTemplate> findByStandardCodeAndDocumentTypeAndFormat(
            String standardCode, DocumentType documentType, DocumentFormat format);

    Optional<DocumentTemplate> findByStandardCodeIsNullAndDocumentTypeAndFormat(
            DocumentType documentType, DocumentFormat format);

    List<DocumentTemplate> findByActiveTrue();
}
