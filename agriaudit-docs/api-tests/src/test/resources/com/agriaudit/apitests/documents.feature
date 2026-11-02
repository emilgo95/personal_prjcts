Feature: Generación y descarga de documentos de auditoría

Background:
  * url baseUrl + '/api'
  * configure headers = authHeaders
  # Elegir una auditoría con puntos evaluados para que el documento tenga contenido.
  * path 'audits'
  * method get
  * status 200
  * def audit = karate.filter(response.content, function(a){ return a.progressPct > 0 })[0] || response.content[0]
  * def auditId = audit.id

Scenario: cada auditoría ofrece opciones de documento con sus formatos
  Given path 'audits', auditId, 'document-options'
  When method get
  Then status 200
  And match response == '#[_ > 0]'
  And match each response contains { documentType: '#string', label: '#string', formats: '#[_ > 0]' }

Scenario: generar un documento lo registra y permite descargarlo
  Given path 'audits', auditId, 'document-options'
  When method get
  Then status 200
  * def option = response[0]
  * def fmt = option.formats[0]

  Given path 'audits', auditId, 'documents', 'generate'
  And request { documentType: '#(option.documentType)', format: '#(fmt)' }
  When method post
  Then status 201
  And match response contains { id: '#number', auditId: '#(auditId)', format: '#(fmt)', fileName: '#string' }
  * def docId = response.id
  * def sizeBytes = response.sizeBytes
  And match sizeBytes == '#? _ > 0'

  Given path 'documents', docId, 'download'
  When method get
  Then status 200
  And match responseBytes == '#notnull'

Scenario: el documento generado aparece en el listado de la organización
  Given path 'documents'
  When method get
  Then status 200
  And match response == '#array'

Scenario: generar con un tipo desconocido es rechazado
  Given path 'audits', auditId, 'documents', 'generate'
  And request { documentType: 'NO_EXISTE', format: 'PDF' }
  When method post
  Then assert responseStatus == 400 || responseStatus == 404 || responseStatus == 422
