import { test, expect } from '@playwright/test';
import { loginViaApi } from './helpers/auth';

test.beforeEach(async ({ page, request }) => {
  await loginViaApi(page, request);
});

test.describe('Navegación y catálogo', () => {
  test('el panel muestra los bloques de indicadores', async ({ page }) => {
    await expect(page.getByRole('region', { name: 'Indicadores principales' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Auditorías por estado' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Auditorías recientes' })).toBeVisible();
  });

  test('el listado de auditorías carga filas del juego de datos de demo', async ({ page }) => {
    await page.getByRole('link', { name: 'Auditorías', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Auditorías', exact: true })).toBeVisible();
    // La DataGrid renderiza al menos una fila (demo seed: 8 auditorías).
    await expect(page.getByRole('row').nth(1)).toBeVisible();
    await expect(page.getByRole('columnheader', { name: 'Estándar' })).toBeVisible();
  });

  test('el catálogo de estándares lista los tres estándares implementados', async ({ page }) => {
    await page.getByRole('link', { name: 'Estándares', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Estándares de auditoría' })).toBeVisible();
    await expect(page.getByText('GLOBALG.A.P. Integrated Farm Assurance (IFA)')).toBeVisible();
    await expect(page.getByText(/GRASP/)).toBeVisible();
    await expect(page.getByText(/SMETA/)).toBeVisible();
  });

  test('el centro de documentos lista documentos ya generados', async ({ page }) => {
    await page.getByRole('link', { name: 'Centro de documentos', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Centro de documentos' })).toBeVisible();
    // El demo seed genera documentos para las auditorías cerradas/enviadas.
    await expect(page.getByRole('row').nth(1)).toBeVisible();
  });

  test('la sección de normativa es accesible y busca', async ({ page }) => {
    await page.getByRole('link', { name: 'Normativa', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Normativa aplicable' })).toBeVisible();
    await expect(page.getByPlaceholder(/Buscar por título/)).toBeVisible();
  });
});
