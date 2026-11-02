package com.agriaudit.documents.infrastructure.persistence;

import com.agriaudit.documents.domain.DocumentTemplate;
import com.agriaudit.documents.domain.DocumentFormat;
import com.agriaudit.documents.domain.DocumentType;
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
