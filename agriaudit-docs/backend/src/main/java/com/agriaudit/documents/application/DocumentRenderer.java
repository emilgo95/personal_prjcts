package com.agriaudit.documents.application;

import com.agriaudit.documents.domain.DocumentTemplate;

public interface DocumentRenderer {

    boolean supports(String engine);

    byte[] render(DocumentTemplate template, DocumentModel model);
}
