Feature: Catálogo de estándares y normativa

Background:
  * url baseUrl + '/api'
  * configure headers = authHeaders

Scenario: lista de estándares incluye los tres implementados end-to-end
  Given path 'standards'
  When method get
  Then status 200
  And match response == '#[_ > 0]'
  * def codes = karate.map(response, function(s){ return s.code })
  And match codes contains 'GLOBALGAP_IFA_V6'
  And match codes contains 'GRASP_V2'
  And match codes contains 'SMETA_6_1'

Scenario Outline: el detalle de cada estándar trae capítulos y puntos de control
  Given path 'standards', '<code>'
  When method get
  Then status 200
  And match response.code == '<code>'
  And match response.chapters == '#[_ > 0]'

  Examples:
    | code             |
    | GLOBALGAP_IFA_V6 |
    | GRASP_V2         |
    | SMETA_6_1        |

Scenario: los puntos de control de un estándar se pueden consultar
  Given path 'standards', 'GLOBALGAP_IFA_V6', 'controls'
  When method get
  Then status 200
  And match response == '#[_ > 0]'
  And match each response contains { ref: '#string', title: '#string', level: '#string' }

Scenario: la normativa aplicable se lista con enlace a la fuente oficial
  Given path 'regulations'
  When method get
  Then status 200
  And match response == '#[_ > 0]'
  And match each response contains { code: '#string', title: '#string', sourceUrl: '##string' }
