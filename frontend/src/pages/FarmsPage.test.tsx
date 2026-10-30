import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import FarmsPage from './FarmsPage';
import { ToastProvider } from '../utils/toast';
import { ConfirmProvider } from '../utils/confirm';

const base = {
  tradeName: null, registrationNumber: null, address: null, region: null, latitude: null, longitude: null,
  totalHectares: null, productionAreas: null, contactName: null, contactEmail: null, contactPhone: null, notes: null,
};
const farms = [
  { ...base, id: 1, legalName: 'Hortícola Águilas S.L.', city: 'Águilas', country: 'España', crops: 'Lechuga' },
  { ...base, id: 2, legalName: 'Invernaderos del Poniente', city: 'El Ejido', country: 'España', crops: 'Tomate' },
];

vi.mock('../api/endpoints', () => ({ FarmApi: { list: () => Promise.resolve(farms), remove: vi.fn() } }));
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ hasRole: () => true }) }));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <ConfirmProvider>
          <MemoryRouter>
            <FarmsPage />
          </MemoryRouter>
        </ConfirmProvider>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('FarmsPage: búsqueda', () => {
  it('filtra la tabla al escribir (sin tildes), indica cuántas coinciden y se puede borrar', async () => {
    renderPage();
    expect(await screen.findByText('Invernaderos del Poniente')).toBeInTheDocument();
    expect(screen.getByText('2 explotaciones')).toBeInTheDocument();
    // La paginación sigue en español aunque la tabla tenga sus propios textos.
    expect(screen.getByText('Filas por página:')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Buscar explotaciones'), { target: { value: 'aguilas' } });

    expect(await screen.findByText('1 de 2 explotaciones')).toBeInTheDocument();
    expect(screen.queryByText('Invernaderos del Poniente')).not.toBeInTheDocument();
    expect(screen.getByText('Hortícola Águilas S.L.')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Borrar búsqueda' }));
    expect(await screen.findByText('Invernaderos del Poniente')).toBeInTheDocument();
  });

  it('avisa cuando no hay coincidencias', async () => {
    renderPage();
    await screen.findByText('Invernaderos del Poniente');

    fireEvent.change(screen.getByLabelText('Buscar explotaciones'), { target: { value: 'zzz' } });

    expect(await screen.findByText('Ninguna explotación coincide con «zzz»')).toBeInTheDocument();
  });
});
