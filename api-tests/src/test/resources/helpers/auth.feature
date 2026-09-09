@ignore
Feature: helper de autenticación (obtiene un token de acceso)

Scenario: login como administrador
  Given url baseUrl + '/api/auth/login'
  And request { email: '#(adminEmail)', password: '#(adminPassword)' }
  When method post
  Then status 200
  And match response.accessToken == '#string'
  And match response.refreshToken == '#string'
  * def accessToken = response.accessToken
  * def refreshToken = response.refreshToken
