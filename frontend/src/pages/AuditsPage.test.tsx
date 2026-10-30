import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import AuditsPage from './AuditsPage';
import type { AuditPage, AuditSummary } from '../api/types';

const audit = (id: number, reference: string, farmName: string): AuditSummary =>
  ({
    id,
    reference,
    farmId: id,
    farmName,
    standardCode: 'GLOBALGAP_IFA_V6',
    standardName: 'GLOBALG.A.P. IFA',
    auditType: 'INTERNAL',
    status: 'IN_PROGRESS',
    plannedStartDate: null,
    actualDate: null,
    leadAuditor: 'Ana García',
    createdAt: '2026-10-01T10:00:00Z',
    findingsCount: id === 5 ? 4 : 0,
    assessedCount: 0,
    totalControlPoints: 10,
    progressPct: 0,
  }) as AuditSummary;

const page = (content: AuditSummary[]): AuditPage =>
  ({ content, page: 0, size: 25, totalElements: content.length, totalPages: 1 }) as AuditPage;

const list = vi.fn();
vi.mock('../api/endpoints', () => ({
  AuditApi: { list: (...a: unknown[]) => list(...a) },
}));
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ hasRole: () => true }),
}));

function renderPage(path = '/audits') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <AuditsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('AuditsPage: buscador', () => {
  beforeEach(() => {
    list.mockReset();
    list.mockImplementation((_p: number, _s: number, q?: string) =>
      Promise.resolve(
        q ? page([audit(5, 'AUD-2026-0005', 'Campo de Fortuna')]) : page([
          audit(5, 'AUD-2026-0005', 'Campo de Fortuna'),
          audit(4, 'AUD-2026-0004', 'Cítricos Vega Media'),
        ]),
      ),
    );
  });

  it('busca en el servidor al escribir, vuelve a la primera página y dice cuántas hay', async () => {
    renderPage();
    expect(await screen.findByText('2 auditorías')).toBeInTheDocument();
    expect(screen.getByText('Filas por página:')).toBeInTheDocument();
    // El nº de NC lleva a las NC de esa auditoría; sin NC, solo el cero.
    expect(screen.getByRole('link', { name: 'Ver las 4 no conformidades de AUD-2026-0005' })).toHaveAttribute(
      'href',
      '/audits/5?tab=hallazgos',
    );

    fireEvent.change(screen.getByLabelText('Buscar auditorías'), { target: { value: '  fortuna ' } });

    expect(await screen.findByText('1 auditoría encontrada')).toBeInTheDocument();
    expect(list).toHaveBeenLastCalledWith(0, 25, 'fortuna');
    expect(screen.queryByText('Cítricos Vega Media')).not.toBeInTheDocument();
  });

  it('conserva la búsqueda de la dirección y se puede borrar', async () => {
    renderPage('/audits?q=fortuna');
    expect(await screen.findByText('1 auditoría encontrada')).toBeInTheDocument();
    expect(screen.getByLabelText('Buscar auditorías')).toHaveValue('fortuna');

    fireEvent.click(screen.getByRole('button', { name: 'Borrar búsqueda' }));

    expect(await screen.findByText('2 auditorías')).toBeInTheDocument();
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(0, 25, ''));
  });
});
