import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loginViaApi } from './helpers/auth';

/**
 * Comprobaciones de accesibilidad con axe-core (WCAG 2.1 A y AA).
 *
 * Política: las violaciones `critical` hacen fallar el test; las `serious` se
 * anotan como aviso para no bloquear por incidencias preexistentes de terceros
 * (MUI). El informe completo queda adjunto a la ejecución.
 */
const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

async function checkA11y(page: import('@playwright/test').Page, exclude: string[] = []) {
  let builder = new AxeBuilder({ page }).withTags(WCAG_TAGS);
  for (const sel of exclude) builder = builder.exclude(sel);
  const { violations } = await builder.analyze();

  const critical = violations.filter((v) => v.impact === 'critical');
  const serious = violations.filter((v) => v.impact === 'serious');

  if (serious.length) {
    test.info().annotations.push({
      type: 'a11y-serious',
      description: serious.map((v) => `${v.id} (${v.nodes.length})`).join(', '),
    });
  }
  expect(critical, JSON.stringify(critical.map((v) => ({ id: v.id, help: v.help })), null, 2)).toEqual([]);
}

test('la pantalla de login no tiene violaciones críticas de accesibilidad', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('button', { name: 'Iniciar sesión' })).toBeVisible();
  await checkA11y(page);
});

test('el panel no tiene violaciones críticas de accesibilidad', async ({ page, request }) => {
  await loginViaApi(page, request);
  await expect(page.getByRole('region', { name: 'Indicadores principales' })).toBeVisible();
  await checkA11y(page);
});
