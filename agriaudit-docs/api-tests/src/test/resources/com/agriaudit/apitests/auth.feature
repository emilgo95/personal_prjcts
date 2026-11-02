Feature: Autenticación y autorización de la API

Background:
  * url baseUrl + '/api'

Scenario: login correcto devuelve tokens y datos del usuario
  Given path 'auth', 'login'
  And request { email: '#(adminEmail)', password: '#(adminPassword)' }
  When method post
  Then status 200
  And match response ==
    """
    {
      accessToken: '#string',
      refreshToken: '#string',
      tokenType: '#string',
      expiresInMinutes: '#number',
      user: {
        id: '#number',
        email: '#(adminEmail)',
        fullName: '##string',
        role: '#string',
        organizationId: '#number',
        organizationName: '#string',
        active: '#boolean',
        blocked: '#boolean',
        mustChangePassword: '#boolean'
      }
    }
    """

Scenario: credenciales incorrectas devuelven 401
  Given path 'auth', 'login'
  And request { email: '#(adminEmail)', password: 'contraseña-incorrecta' }
  When method post
  Then status 401

Scenario: petición sin cuerpo válido devuelve 400
  Given path 'auth', 'login'
  And request { email: 'no-es-un-email', password: '' }
  When method post
  Then status 400

Scenario: /auth/me requiere token
  Given path 'auth', 'me'
  When method get
  Then status 401

Scenario: /auth/me con token devuelve el usuario actual
  Given path 'auth', 'me'
  And configure headers = authHeaders
  When method get
  Then status 200
  And match response.email == adminEmail
  And match response.role == '#string'

Scenario: un endpoint protegido rechaza un token manipulado
  Given path 'audits'
  And header Authorization = 'Bearer token-invalido'
  When method get
  Then status 401
