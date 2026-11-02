import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import FindingsPage from './FindingsPage';
import type { FindingList, FindingRow, FindingView } from '../api/types';

const row = (id: number, over: Partial<FindingRow>): FindingRow => ({
  id,
  auditId: 5,
  auditReference: 'AUD-2026-0005',
  auditStatus: 'CLOSING',
  farmId: 3,
  farmName: 'Campo de Fortuna',
  standardCode: 'GLOBALGAP_IFA_V6',
  standardName: 'GLOBALG.A.P. IFA',
  controlPointRef: 'AF 1.2',
  reference: `NC-00${id}`,
  title: `No conformidad ${id}`,
  classification: 'MINOR',
  status: 'OPEN',
  dueDate: '2026-11-20',
  daysLeft: 20,
  closedAt: null,
  closedOnTime: null,
  lateJustification: null,
  ...over,
});

const counts: Record<FindingView, number> = {
  OPEN: 2, OVERDUE: 1, DUE_2: 0, DUE_7: 0, DUE_14: 0, CLOSED_12M: 1, CLOSED_LATE: 0, ALL: 3,
};

const listAll = vi.fn();
vi.mock('../api/endpoints', () => ({
  FindingApi: { listAll: (...a: unknown[]) => listAll(...a) },
}));

function Where() {
  const loc = useLocation();
  return <p>Destino: {loc.pathname + loc.search}</p>;
}

function renderPage(path = '/audits/findings') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/audits/findings" element={<FindingsPage />} />
          <Route path="/audits/:id" element={<Where />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('FindingsPage', () => {
  beforeEach(() => {
    listAll.mockReset();
    listAll.mockImplementation((view: FindingView): Promise<FindingList> =>
      Promise.resolve({
        view,
        counts,
        rows:
          view === 'OVERDUE'
            ? [row(1, { title: 'Vencida', daysLeft: -3, dueDate: '2026-10-27', lateJustification: 'Laboratorio' })]
            : [row(1, { title: 'Vencida', daysLeft: -3, dueDate: '2026-10-27' }), row(2, { title: 'En plazo', farmName: 'Brócoli Ibérico' })],
      }),
    );
  });

  it('abre en la vista de la dirección, con el recuento de cada vista', async () => {
    renderPage('/audits/findings?vista=vencidas');

    expect(await screen.findByText('AF 1.2 · Vencida')).toBeInTheDocument();
    expect(listAll).toHaveBeenCalledWith('OVERDUE');
    expect(screen.getByRole('button', { name: 'Vencidas (1)' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'Cerradas (12 meses) (1)' })).toBeInTheDocument();
    expect(screen.getByText('Justificada')).toBeInTheDocument();
  });

  it('cambia de vista y busca sin tildes en la lista', async () => {
    renderPage();
    expect(await screen.findByText('AF 1.2 · En plazo')).toBeInTheDocument();
    expect(listAll).toHaveBeenCalledWith('OPEN');

    fireEvent.change(screen.getByLabelText('Buscar no conformidades'), { target: { value: 'brocoli' } });
    await waitFor(() => expect(screen.queryByText('AF 1.2 · Vencida')).not.toBeInTheDocument());
    expect(screen.getByText('AF 1.2 · En plazo')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Todas (3)' }));
    await waitFor(() => expect(listAll).toHaveBeenLastCalledWith('ALL'));
  });

  it('al abrir una NC va a su auditoría, a Hallazgos y CAPA con la NC desplegada', async () => {
    renderPage();
    fireEvent.click(await screen.findByText('AF 1.2 · En plazo'));
    expect(await screen.findByText('Destino: /audits/5?tab=hallazgos&nc=2')).toBeInTheDocument();
  });
});
