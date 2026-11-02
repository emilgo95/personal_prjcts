import { test, expect } from '@playwright/test';
import { loginViaApi } from './helpers/auth';

test.beforeEach(async ({ page, request }) => {
  await loginViaApi(page, request);
});

test.describe('Generación de documentos de auditoría', () => {
  test('genera un documento desde la ficha de una auditoría y lo descarga', async ({ page }) => {
    // Abrir la primera auditoría del listado.
    await page.getByRole('link', { name: 'Auditorías', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Auditorías', exact: true })).toBeVisible();
    await page.getByRole('row').nth(1).click();

    await expect(page.getByRole('tab', { name: 'Documentos' })).toBeVisible();
    await page.getByRole('tab', { name: 'Documentos' }).click();

    await expect(page.getByRole('heading', { name: 'Generar documento' })).toBeVisible();

    // Seleccionar tipo de documento (MUI Select -> combobox + listbox).
    await page.getByRole('combobox', { name: 'Tipo de documento' }).click();
    const firstType = page.getByRole('option').first();
    await expect(firstType).toBeVisible();
    await firstType.click();

    // Seleccionar el primer formato disponible.
    await page.getByRole('combobox', { name: 'Formato' }).click();
    await page.getByRole('option').first().click();

    await page.getByRole('button', { name: 'Generar' }).click();

    // Confirmación (toast) y fila nueva en la tabla de documentos generados.
    await expect(page.getByText('Documento generado')).toBeVisible();
    const generatedTable = page.getByRole('table');
    await expect(generatedTable.getByRole('button', { name: 'Descargar' }).first()).toBeVisible();

    // Descargar el primero y comprobar que el fichero no está vacío.
    const [download] = await Promise.all([
      page.waitForEvent('download'),
      generatedTable.getByRole('button', { name: 'Descargar' }).first().click(),
    ]);
    expect(download.suggestedFilename().length).toBeGreaterThan(0);
    const stream = await download.createReadStream();
    let bytes = 0;
    for await (const chunk of stream) bytes += chunk.length;
    expect(bytes).toBeGreaterThan(100);
  });
});
