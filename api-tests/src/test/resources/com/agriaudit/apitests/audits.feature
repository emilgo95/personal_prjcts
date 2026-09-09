Feature: Auditorías y puntuación

Background:
  * url baseUrl + '/api'
  * configure headers = authHeaders

Scenario: el listado de auditorías devuelve el juego de datos de demostración
  Given path 'audits'
  When method get
  Then status 200
  And match response == '#[_ > 0]'
  And match each response contains { id: '#number', reference: '#string', standardCode: '#string', status: '#string' }

Scenario: el detalle de una auditoría incluye explotación y progreso
  Given path 'audits'
  When method get
  Then status 200
  * def auditId = response[0].id
  Given path 'audits', auditId
  When method get
  Then status 200
  And match response.id == auditId
  And match response.farm.legalName == '#string'
  And match response.totalControlPoints == '#number'
  And match response.progressPct == '#number'

Scenario: la puntuación de una auditoría se calcula según su estándar
  Given path 'audits'
  When method get
  Then status 200
  * def auditId = response[0].id
  Given path 'audits', auditId, 'score'
  When method get
  Then status 200
  And match response contains { overallResult: '#string' }

Scenario: los resultados del checklist de una auditoría se pueden consultar
  Given path 'audits'
  When method get
  Then status 200
  * def auditId = response[0].id
  Given path 'audits', auditId, 'results'
  When method get
  Then status 200
  And match response == '#array'

Scenario: crear una auditoría exige rol y datos válidos
  # Sin cuerpo -> 400 de validación (el usuario admin sí tiene permiso).
  Given path 'audits'
  And request {}
  When method post
  Then status 400
