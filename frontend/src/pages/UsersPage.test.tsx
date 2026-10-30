import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import UsersPage from './UsersPage';
import { ToastProvider } from '../utils/toast';
import { ConfirmProvider } from '../utils/confirm';
import { UserApi } from '../api/endpoints';
import type { UserDto } from '../api/types';

const users: UserDto[] = [
  { id: 1, email: 'admin@demo.test', fullName: 'Admin', role: 'ADMIN', organizationId: 1, organizationName: 'Org', active: true, blocked: false },
  { id: 2, email: 'baja@demo.test', fullName: 'Baja', role: 'VIEWER', organizationId: 1, organizationName: 'Org', active: false, blocked: false },
  { id: 3, email: 'ana@demo.test', fullName: 'Ana', role: 'AUDITOR', organizationId: 1, organizationName: 'Org', active: true, blocked: false },
  { id: 4, email: null, fullName: 'Usuario bloqueado', role: 'VIEWER', organizationId: 1, organizationName: 'Org', active: false, blocked: true },
];

vi.mock('../api/endpoints', () => ({
  UserApi: { list: () => Promise.resolve(users), create: vi.fn(), update: vi.fn(), block: vi.fn(), dataExport: vi.fn() },
  downloadBlob: vi.fn(),
}));
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: users[0], hasRole: () => true }),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <ConfirmProvider>
          <UsersPage />
        </ConfirmProvider>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('UsersPage', () => {
  it('muestra el estado real de cada usuario y permite reactivar a los desactivados', async () => {
    renderPage();

    expect(await screen.findByText('Desactivado')).toBeInTheDocument();
    expect(screen.getAllByText('Activo')).toHaveLength(2);
    expect(screen.getByText('Bloqueado')).toBeInTheDocument();
    // El desactivado se puede reactivar; el bloqueado (segundo botón) no.
    const reactivar = screen.getAllByRole('button', { name: 'Reactivar' });
    expect(reactivar[0]).toBeEnabled();
    expect(reactivar[1]).toBeDisabled();
  });

  it('no deja que el administrador se desactive a sí mismo', async () => {
    renderPage();
    await screen.findByText('Desactivado');

    const buttons = screen.getAllByRole('button', { name: 'Desactivar' });
    // La fila del propio administrador (la primera) está bloqueada; la de Ana no.
    expect(buttons[0]).toBeDisabled();
    expect(buttons[1]).toBeEnabled();
  });

  it('permite bloquear los datos de otros usuarios pero no los propios ni los ya bloqueados', async () => {
    renderPage();
    await screen.findByText('Desactivado');

    const buttons = screen.getAllByRole('button', { name: 'Bloquear datos' });
    expect(buttons).toHaveLength(4);
    expect(buttons[0]).toBeDisabled(); // el propio administrador
    expect(buttons[2]).toBeEnabled(); // Ana
    expect(buttons[3]).toBeDisabled(); // ya bloqueado
  });

  it('de un usuario bloqueado no muestra ningún correo', async () => {
    renderPage();
    await screen.findByText('Desactivado');

    expect(screen.getByText('Oculto (datos bloqueados)')).toBeInTheDocument();
    expect(screen.queryByText(/@bloqueado/)).not.toBeInTheDocument();
  });

  it('pide confirmación con el cuadro de la aplicación antes de bloquear, y cancelar no bloquea', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm');
    vi.mocked(UserApi.block).mockResolvedValue({ ...users[2], email: null, blocked: true, active: false });
    renderPage();
    await screen.findByText('Desactivado');

    fireEvent.click(screen.getAllByRole('button', { name: 'Bloquear datos' })[2]);
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('¿Bloquear los datos de Ana?')).toBeInTheDocument();
    expect(within(dialog).getByText('No se puede deshacer.')).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(UserApi.block).not.toHaveBeenCalled();

    fireEvent.click(screen.getAllByRole('button', { name: 'Bloquear datos' })[2]);
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Bloquear datos' }));
    await waitFor(() => expect(UserApi.block).toHaveBeenCalledWith(3));
    expect(confirmSpy).not.toHaveBeenCalled();
  });

  it('permite exportar los datos de una persona salvo si están bloqueados', async () => {
    renderPage();
    await screen.findByText('Desactivado');

    const buttons = screen.getAllByRole('button', { name: 'Exportar datos' });
    expect(buttons).toHaveLength(4);
    expect(buttons[2]).toBeEnabled(); // Ana
    expect(buttons[3]).toBeDisabled(); // bloqueado: sus datos ya no se tratan
  });
});
