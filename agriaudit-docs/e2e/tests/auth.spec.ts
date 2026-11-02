import { test, expect } from '@playwright/test';
import { loginViaForm } from './helpers/auth';

test.describe('Autenticación', () => {
  test('la pantalla de login se muestra a un usuario no autenticado', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: 'AgriAudit Docs' })).toBeVisible();
    await expect(page.getByLabel('Correo electrónico')).toBeVisible();
    await expect(page.getByLabel('Contraseña')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Iniciar sesión' })).toBeVisible();
  });

  test('credenciales incorrectas muestran un error y no navegan', async ({ page }) => {
    await loginViaForm(page, 'admin@demo.test', 'contraseña-incorrecta');
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
  });

  test('credenciales válidas llevan al panel', async ({ page }) => {
    await loginViaForm(page);
    await expect(page.getByRole('region', { name: 'Indicadores principales' })).toBeVisible();
    await expect(page).not.toHaveURL(/\/login$/);
    // El menú lateral con las secciones principales está disponible.
    await expect(page.getByRole('link', { name: 'Auditorías', exact: true })).toBeVisible();
  });

  test('cerrar sesión vuelve al login', async ({ page }) => {
    await loginViaForm(page);
    await expect(page.getByRole('region', { name: 'Indicadores principales' })).toBeVisible();
    await page.getByRole('button', { name: 'Cerrar sesión' }).click();
    await expect(page).toHaveURL(/\/login$/);
  });
});
