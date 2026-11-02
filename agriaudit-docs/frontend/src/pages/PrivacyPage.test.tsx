import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PrivacyPage from './PrivacyPage';
import { ToastProvider } from '../utils/toast';

const dataExport = vi.fn().mockResolvedValue(new Blob(['zip']));
const downloadBlob = vi.fn();

vi.mock('../api/endpoints', () => ({
  PrivacyApi: {
    get: () =>
      Promise.resolve({
        blockedRetentionMonths: 60,
        organizationValue: null,
        defaultMonths: 60,
        minMonths: 1,
        maxMonths: 240,
        blockedCount: 1,
        nextDestruction: '2031-10-29T00:20:00Z',
      }),
    blocked: () =>
      Promise.resolve([
        {
          userId: 12,
          role: 'AUDITOR',
          blockedAt: '2026-10-29T10:00:00Z',
          destroyAfter: '2031-10-29T10:00:00Z',
          blockedBy: 'Admin Demo',
        },
      ]),
    inventory: () =>
      Promise.resolve([
        {
          category: 'Contacto de explotaciones',
          data: 'Nombre, correo y teléfono del contacto',
          whose: 'Contacto del socio',
          fields: ['fieldbook.farms.contact_name'],
          visibleTo: 'Toda la organización',
          retention: 'Mientras exista la explotación',
          notes: 'No son usuarios',
        },
      ]),
    setRetention: vi.fn(),
    exportAll: vi.fn(),
  },
  UserApi: {
    list: () =>
      Promise.resolve([
        { id: 1, email: 'admin@demo.test', fullName: 'Admin Demo', role: 'ADMIN', blocked: false },
        { id: 12, email: 'bloqueado-12@bloqueado.invalid', fullName: 'Usuario bloqueado', role: 'AUDITOR', blocked: true },
        { id: 3, email: 'ana@demo.test', fullName: 'Ana', role: 'AUDITOR', blocked: false },
      ]),
    dataExport: (id: number) => dataExport(id),
  },
  downloadBlob: (...args: unknown[]) => downloadBlob(...args),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <PrivacyPage />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('PrivacyPage', () => {
  it('muestra el plazo en vigor y el botón de exportación de la organización', async () => {
    renderPage();

    expect(await screen.findByText(/En vigor: 60 meses/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Descargar exportación/ })).toBeEnabled();
  });

  it('lista los usuarios bloqueados sin nombre ni correo y con la fecha de destrucción', async () => {
    renderPage();

    const table = await screen.findByRole('table', { name: 'Usuarios con datos bloqueados' });
    expect(within(table).getByText('Usuario n.º 12')).toBeInTheDocument();
    expect(within(table).getByText('Auditor')).toBeInTheDocument();
    expect(within(table).getByText('Admin Demo')).toBeInTheDocument();
    expect(table).not.toHaveTextContent('@');
  });

  it('descarga los datos de una persona elegida y no ofrece a los bloqueados', async () => {
    renderPage();
    await screen.findByText(/En vigor/);
    const descargar = screen.getByRole('button', { name: /Descargar sus datos/ });
    expect(descargar).toBeDisabled();

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Persona' }));
    const options = await screen.findAllByRole('option');
    expect(options.map((o) => o.textContent)).toEqual(['Admin Demo · admin@demo.test', 'Ana · ana@demo.test']);
    fireEvent.click(options[1]);

    fireEvent.click(descargar);
    await waitFor(() => expect(dataExport).toHaveBeenCalledWith(3));
    await waitFor(() => expect(downloadBlob).toHaveBeenCalled());
  });

  it('muestra el inventario de datos personales con sus salvedades', async () => {
    renderPage();

    expect(await screen.findByText('Contacto de explotaciones')).toBeInTheDocument();
    expect(screen.getByText('Nombre, correo y teléfono del contacto')).toBeInTheDocument();
    expect(screen.getByText('No son usuarios')).toBeInTheDocument();
  });
});
