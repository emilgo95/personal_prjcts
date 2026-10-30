import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ChangePasswordPage, { newPasswordProblem } from './ChangePasswordPage';
import { ToastProvider } from '../utils/toast';

const changePassword = vi.fn();
const logout = vi.fn();
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    user: { fullName: 'Lucía Calidad', organizationName: 'Central Hortofrutícola' },
    changePassword: (...a: unknown[]) => changePassword(...a),
    logout,
  }),
}));

function renderPage(forced: boolean) {
  return render(
    <ToastProvider>
      <MemoryRouter>
        <ChangePasswordPage forced={forced} />
      </MemoryRouter>
    </ToastProvider>,
  );
}

function fill(current: string, next: string, repeat: string) {
  fireEvent.change(screen.getByLabelText(/Contraseña (temporal|actual)/), { target: { value: current } });
  fireEvent.change(screen.getByLabelText(/^Contraseña nueva/), { target: { value: next } });
  fireEvent.change(screen.getByLabelText(/Repite la contraseña nueva/), { target: { value: repeat } });
}

describe('newPasswordProblem', () => {
  it('explica por qué una contraseña nueva aún no vale', () => {
    expect(newPasswordProblem('actual', 'corta', 'corta')).toMatch(/Al menos 8/);
    expect(newPasswordProblem('actual', 'x'.repeat(73), 'x'.repeat(73))).toMatch(/máximo 72/);
    expect(newPasswordProblem('misma-clave', 'misma-clave', 'misma-clave')).toMatch(/distinta/);
    expect(newPasswordProblem('actual', 'nueva-clave', 'otra-clave')).toMatch(/no coinciden/);
    expect(newPasswordProblem('actual', 'nueva-clave', 'nueva-clave')).toBeNull();
  });
});

describe('ChangePasswordPage', () => {
  beforeEach(() => {
    changePassword.mockReset();
    logout.mockReset();
  });

  it('en el primer acceso pide crear la contraseña y solo deja seguir con una válida', async () => {
    changePassword.mockResolvedValue(undefined);
    renderPage(true);

    expect(screen.getByRole('heading', { name: 'Crea tu contraseña' })).toBeInTheDocument();
    expect(screen.getByText(/contraseña temporal que te ha dado el administrador/)).toBeInTheDocument();
    const submit = screen.getByRole('button', { name: 'Cambiar contraseña' });
    expect(submit).toBeDisabled();

    fill('Temporal-1', 'mi-clave-nueva', 'mi-clave-otra');
    expect(screen.getByText('No coincide con la contraseña nueva')).toBeInTheDocument();
    expect(submit).toBeDisabled();

    fill('Temporal-1', 'mi-clave-nueva', 'mi-clave-nueva');
    fireEvent.click(submit);
    await waitFor(() => expect(changePassword).toHaveBeenCalledWith('Temporal-1', 'mi-clave-nueva'));
  });

  it('muestra el error del servidor y permite salir', async () => {
    changePassword.mockRejectedValue({ isAxiosError: true, response: { data: { message: 'La contraseña actual no es correcta' } } });
    renderPage(true);

    fill('mal', 'mi-clave-nueva', 'mi-clave-nueva');
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar contraseña' }));
    expect(await screen.findByText('La contraseña actual no es correcta')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Salir' }));
    expect(logout).toHaveBeenCalled();
  });

  it('desde el menú se puede cancelar y avisa de que se cierran las otras sesiones', () => {
    renderPage(false);
    expect(screen.getByRole('heading', { name: 'Cambiar contraseña' })).toBeInTheDocument();
    expect(screen.getByText(/se cierran tus sesiones abiertas en otros equipos/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeInTheDocument();
  });
});
