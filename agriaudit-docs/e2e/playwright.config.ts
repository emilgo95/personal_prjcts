import { defineConfig, devices } from '@playwright/test';

/**
 * Configuración de Playwright para AgriAudit Docs.
 *
 * La app se prueba tal y como se despliega con `docker compose`:
 *   - Frontend (nginx + SPA)  -> E2E_BASE_URL   (por defecto http://localhost:8080)
 *   - Backend  (API REST)     -> E2E_API_URL    (por defecto http://localhost:8081)
 *
 * No se arranca ningún servidor desde aquí: la pila debe estar levantada
 * (`make up` en local, el job `e2e` en CI).
 */
export const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:8080';
export const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8081';

export const CREDENTIALS = {
  email: process.env.E2E_ADMIN_EMAIL ?? 'admin@demo.test',
  password: process.env.E2E_ADMIN_PASSWORD ?? 'Admin123!',
};

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI
    ? [['html', { open: 'never' }], ['list'], ['github']]
    : [['html', { open: 'never' }], ['list']],
  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    locale: 'es-ES',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
