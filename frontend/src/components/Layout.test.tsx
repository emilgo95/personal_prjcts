import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Layout from './Layout';

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    user: { fullName: 'Lucía Calidad', role: 'QUALITY', organizationName: 'Central Hortofrutícola' },
    logout: vi.fn(),
    hasRole: (...roles: string[]) => roles.includes('QUALITY'),
  }),
}));

function renderLayout() {
  return render(
    <MemoryRouter initialEntries={['/audits']}>
      <Layout>
        <p>Contenido</p>
      </Layout>
    </MemoryRouter>,
  );
}

/** El menú fijo de escritorio (el de móvil también está en el documento, cerrado). */
function desktopNav() {
  const navs = screen.getAllByRole('navigation', { name: 'Menú principal' });
  return navs[0];
}

describe('Layout: menú lateral', () => {
  beforeEach(() => localStorage.clear());

  it('se contrae a solo iconos, conserva el nombre de cada enlace y recuerda la preferencia', () => {
    const { unmount } = renderLayout();
    const nav = desktopNav();
    expect(within(nav).getAllByText('Auditorías').length).toBeGreaterThan(0);

    fireEvent.click(within(nav).getAllByRole('button', { name: 'Contraer el menú' })[0]);

    expect(within(nav).getAllByRole('button', { name: 'Expandir el menú' })[0]).toHaveAttribute('aria-expanded', 'false');
    // Sin texto visible, pero cada enlace sigue teniendo nombre accesible.
    expect(within(nav).getAllByRole('link', { name: 'Auditorías' }).length).toBeGreaterThan(0);
    expect(within(nav).getAllByRole('link', { name: 'Equivalencias entre normas' }).length).toBeGreaterThan(0);
    expect(localStorage.getItem('agriaudit.navCollapsed')).toBe('true');

    unmount();
    renderLayout();
    expect(within(desktopNav()).getAllByRole('button', { name: 'Expandir el menú' }).length).toBeGreaterThan(0);
  });

  it('solo muestra las secciones del rol', () => {
    renderLayout();
    const nav = desktopNav();
    expect(within(nav).getAllByRole('link', { name: /Trazabilidad/ }).length).toBeGreaterThan(0);
    expect(within(nav).queryByRole('link', { name: /Usuarios/ })).not.toBeInTheDocument();
  });
});
