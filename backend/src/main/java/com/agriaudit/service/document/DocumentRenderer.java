package com.agriaudit.service.document;

import com.agriaudit.domain.DocumentTemplate;

public interface DocumentRenderer {

    boolean supports(String engine);

    byte[] render(DocumentTemplate template, DocumentModel model);
}
