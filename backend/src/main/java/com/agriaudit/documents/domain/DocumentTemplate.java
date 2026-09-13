package com.agriaudit.documents.domain;

import jakarta.persistence.*;

@Entity
@Table(schema = "documents", name = "document_templates")
public class DocumentTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "standard_code")
    private String standardCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentFormat format;

    @Column(nullable = false)
    private String engine;

    @Column(name = "template_ref")
    private String templateRef;

    @Column(nullable = false)
    private String version = "1";

    @Column(nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStandardCode() { return standardCode; }
    public void setStandardCode(String standardCode) { this.standardCode = standardCode; }
    public DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(DocumentType documentType) { this.documentType = documentType; }
    public DocumentFormat getFormat() { return format; }
    public void setFormat(DocumentFormat format) { this.format = format; }
    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }
    public String getTemplateRef() { return templateRef; }
    public void setTemplateRef(String templateRef) { this.templateRef = templateRef; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
