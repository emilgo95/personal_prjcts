Feature: Panel de indicadores

Background:
  * url baseUrl + '/api'
  * configure headers = authHeaders

Scenario: el panel agrega explotaciones, auditorías y no conformidades
  Given path 'dashboard'
  When method get
  Then status 200
  And match response contains
    """
    {
      farmsCount: '#number',
      auditsTotal: '#number',
      auditsByStatus: '#object',
      openFindings: '#number',
      overdueFindings: '#number',
      documentsGenerated: '#number',
      recentAudits: '#array'
    }
    """
  And match response.farmsCount == '#? _ >= 0'
  And match response.auditsTotal == '#? _ >= 0'

Scenario: el panel requiere autenticación
  * configure headers = null
  Given path 'dashboard'
  When method get
  Then status 401
