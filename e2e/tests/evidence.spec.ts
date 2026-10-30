import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loginViaApi } from './helpers/auth';

test.beforeEach(async ({ page, request }) => {
  await loginViaApi(page, request);
});

test.describe('Evidencias reutilizables', () => {
  test('la sección muestra los registros, la salud del sistema y las equivalencias entre normas', async ({ page }) => {
    await page.getByRole('link', { name: 'Evidencias', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Evidencias', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Nueva evidencia' })).toBeVisible();
    // El juego de datos de demostración trae registros de evidencia.
    await expect(page.getByRole('table', { name: 'Evidencias registradas' })).toBeVisible();

    await page.getByRole('tab', { name: 'Salud del sistema' }).click();
    await expect(page.getByRole('table', { name: 'Salud del sistema de gestión' })).toBeVisible();

    await page.getByRole('tab', { name: 'Equivalencias entre normas' }).click();
    await expect(page.getByText(/borrador a validar/)).toBeVisible();
  });

  test('la pantalla de evidencias no tiene violaciones críticas de accesibilidad', async ({ page }) => {
    await page.getByRole('link', { name: 'Evidencias', exact: true }).click();
    await expect(page.getByRole('table', { name: 'Evidencias registradas' })).toBeVisible();
    const { violations } = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    const critical = violations.filter((v) => v.impact === 'critical');
    expect(critical, JSON.stringify(critical.map((v) => ({ id: v.id, help: v.help })), null, 2)).toEqual([]);
  });
});
