import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import App from './App';

let role = 'ADMIN';

vi.mock('./auth/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 1, email: 'admin@demo.test', fullName: 'Admin', role, organizationName: 'Org' },
    loading: false,
    logout: vi.fn(),
    hasRole: (...roles: string[]) => roles.includes(role),
  }),
}));
vi.mock('./pages/PrivacyPage', () => ({ default: () => <h1>Pantalla de privacidad</h1> }));
vi.mock('./pages/TracePage', () => ({ default: () => <h1>Pantalla de trazabilidad</h1> }));
vi.mock('./pages/UsersPage', () => ({ default: () => <h1>Pantalla de usuarios</h1> }));
vi.mock('./pages/DashboardPage', () => ({ default: () => <h1>Panel</h1> }));

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe('rutas protegidas por rol', () => {
  it.each([
    ['/privacy', 'Pantalla de privacidad'],
    ['/users', 'Pantalla de usuarios'],
    ['/trace', 'Pantalla de trazabilidad'],
  ])('el administrador con sesión abre %s', (path, heading) => {
    role = 'ADMIN';
    renderAt(path);
    expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument();
  });

  it('quien no es administrador vuelve al panel al abrir /privacy', () => {
    role = 'AUDITOR';
    renderAt('/privacy');
    expect(screen.getByRole('heading', { name: 'Panel' })).toBeInTheDocument();
    expect(screen.queryByText('Pantalla de privacidad')).not.toBeInTheDocument();
  });
});
