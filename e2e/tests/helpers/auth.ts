import { expect, type Page, type APIRequestContext } from '@playwright/test';
import { API_URL, BASE_URL, CREDENTIALS } from '../../playwright.config';

const ACCESS_TOKEN_KEY = 'agriaudit.accessToken';
const REFRESH_TOKEN_KEY = 'agriaudit.refreshToken';

export interface Tokens {
  accessToken: string;
  refreshToken: string;
}

/** Obtiene un par de tokens llamando directamente a la API de login. */
export async function apiLogin(
  request: APIRequestContext,
  email = CREDENTIALS.email,
  password = CREDENTIALS.password,
): Promise<Tokens> {
  const res = await request.post(`${API_URL}/api/auth/login`, {
    data: { email, password },
  });
  expect(res.ok(), `login API respondió ${res.status()}`).toBeTruthy();
  const body = await res.json();
  return { accessToken: body.accessToken, refreshToken: body.refreshToken };
}

/**
 * Deja la sesión iniciada sin pasar por el formulario: inyecta los tokens en
 * localStorage antes de que cargue la SPA. Úsalo en los tests que no prueban
 * el propio login.
 */
export async function loginViaApi(page: Page, request: APIRequestContext): Promise<void> {
  const tokens = await apiLogin(request);
  await page.addInitScript(
    ({ tokens, keys }) => {
      window.localStorage.setItem(keys.access, tokens.accessToken);
      window.localStorage.setItem(keys.refresh, tokens.refreshToken);
    },
    { tokens, keys: { access: ACCESS_TOKEN_KEY, refresh: REFRESH_TOKEN_KEY } },
  );
  await page.goto(BASE_URL);
  await expect(page.getByRole('heading', { name: 'Panel' })).toBeVisible();
}

/** Inicia sesión rellenando el formulario real (para los tests de login). */
export async function loginViaForm(
  page: Page,
  email = CREDENTIALS.email,
  password = CREDENTIALS.password,
): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña').fill(password);
  await page.getByRole('button', { name: /Iniciar sesión|Entrando/ }).click();
}
