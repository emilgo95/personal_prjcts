function fn() {
  function pick(prop, envVar, fallback) {
    var fromProp = karate.properties[prop];
    if (fromProp) return fromProp;
    var fromEnv = java.lang.System.getenv(envVar);
    if (fromEnv) return fromEnv;
    return fallback;
  }

  var config = {
    baseUrl: pick('api.baseUrl', 'API_BASE_URL', 'http://localhost:8081'),
    adminEmail: pick('api.adminEmail', 'API_ADMIN_EMAIL', 'admin@demo.test'),
    adminPassword: pick('api.adminPassword', 'API_ADMIN_PASSWORD', 'Admin123!')
  };

  karate.configure('connectTimeout', 10000);
  karate.configure('readTimeout', 30000);
  karate.configure('ssl', true);

  // Inicia sesión una sola vez para toda la ejecución y comparte el token.
  var auth = karate.callSingle('classpath:helpers/auth.feature', config);
  config.accessToken = auth.accessToken;
  config.authHeaders = { Authorization: 'Bearer ' + auth.accessToken };

  return config;
}
