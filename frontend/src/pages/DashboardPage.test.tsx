import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import DashboardPage from './DashboardPage';

const dashboard = {
  farmsCount: 8,
  auditsTotal: 12,
  auditsByStatus: { IN_PROGRESS: 4, CLOSED: 8 },
  openFindings: 5,
  overdueFindings: 2,
  dueSoonFindings: 0,
  dueIn2Findings: 0,
  dueIn14Findings: 3,
  closedWithDeadlineFindings: 20,
  closedOnTimeFindings: 18,
  documentsGenerated: 30,
  upcomingDeadlines: [
    {
      findingId: 1,
      findingReference: 'NC-001',
      title: 'Registro de fitosanitarios',
      auditId: 7,
      auditReference: 'AUD-2026-0007',
      farmName: 'Hortícolas del Segura',
      classification: 'MAJOR',
      dueDate: '2026-11-20',
    },
  ],
  recentAudits: [
    {
      id: 7,
      reference: 'AUD-2026-0007',
      farmId: 1,
      farmName: 'Hortícolas del Segura',
      standardCode: 'GLOBALGAP_IFA_V6',
      standardName: 'GLOBALG.A.P. IFA v6',
      auditType: 'INTERNAL',
      status: 'IN_PROGRESS',
      plannedStartDate: null,
      actualDate: null,
      leadAuditor: null,
      createdAt: '2026-10-01T10:00:00Z',
      findingsCount: 3,
      assessedCount: 32,
      totalControlPoints: 40,
      progressPct: 80,
    },
  ],
};

vi.mock('../api/endpoints', () => ({ DashboardApi: { load: () => Promise.resolve(dashboard) } }));
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { fullName: 'Ana García', organizationName: 'Central Hortofrutícola' } }),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('DashboardPage', () => {
  it('muestra primero los indicadores grandes con el saludo y los avisos de estado', async () => {
    renderPage();

    expect(await screen.findByLabelText('8 Explotaciones')).toBeInTheDocument();
    expect(screen.getByLabelText('12 Auditorías')).toBeInTheDocument();
    // Todo lo de las no conformidades va junto en una sola tarjeta.
    const nc = within(screen.getByRole('region', { name: 'No conformidades' }));
    expect(nc.getByLabelText('5 No conformidades abiertas')).toBeInTheDocument();
    expect(nc.getByLabelText('2 No conformidades vencidas')).toBeInTheDocument();
    expect(nc.getByLabelText(/No conformidades que vencen en 7 días/)).toBeInTheDocument();
    expect(nc.getByLabelText('90% Cerradas en plazo')).toBeInTheDocument();
    expect(screen.getByText('18 de 20 en 12 meses')).toBeInTheDocument();
    expect(screen.getByText('3 vencen en 14 días')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(/, Ana$/);
    // El color no va solo: cada alerta lleva su texto.
    expect(screen.getByText('Requieren atención')).toBeInTheDocument();
    expect(screen.getByText('Sin urgencias')).toBeInTheDocument();
    expect(screen.getByTestId('dashboard-scene')).toHaveAttribute('aria-hidden', 'true');
  });

  it('el detalle (vencimientos y auditorías recientes) está debajo y la flecha desplaza hasta él', async () => {
    const scrollTo = vi.fn();
    window.scrollTo = scrollTo as unknown as typeof window.scrollTo;
    renderPage();

    expect(await screen.findByText('Próximos vencimientos')).toBeInTheDocument();
    expect(screen.getByText('Auditorías recientes')).toBeInTheDocument();
    expect(screen.getByText('NC-001 · Registro de fitosanitarios')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Ver el detalle/ }));
    expect(scrollTo).toHaveBeenCalledWith(expect.objectContaining({ top: expect.any(Number) }));

    fireEvent.click(screen.getByRole('button', { name: /Volver a los indicadores/ }));
    expect(scrollTo).toHaveBeenLastCalledWith(expect.objectContaining({ top: 0 }));
  });
});
