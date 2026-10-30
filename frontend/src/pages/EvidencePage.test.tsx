import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import EvidencePage from './EvidencePage';
import { ToastProvider } from '../utils/toast';
import { ConfirmProvider } from '../utils/confirm';

const types = [
  { code: 'WATER_ANALYSIS', name: 'Análisis de agua', description: 'Boletín del laboratorio', personalDataNotice: null },
  { code: 'TRAINING', name: 'Formación', description: null, personalDataNotice: 'Si incluye nombres, márcalo.' },
];

vi.mock('../api/endpoints', () => ({
  FarmApi: { list: () => Promise.resolve([{ id: 1, legalName: 'Finca Uno' }]) },
  EvidenceApi: {
    types: () => Promise.resolve(types),
    records: () =>
      Promise.resolve([
        {
          id: 10,
          farmId: 1,
          farmName: 'Finca Uno',
          typeCode: 'WATER_ANALYSIS',
          typeName: 'Análisis de agua',
          title: 'Análisis del pozo 2',
          issuedOn: '2026-09-01',
          validUntil: '2027-09-01',
          outcome: 'FAVORABLE',
          notes: null,
          containsPersonalData: false,
          status: 'CURRENT',
          documents: 1,
          usedIn: 2,
          createdAt: '2026-09-01T10:00:00Z',
          updatedAt: '2026-09-01T10:00:00Z',
        },
      ]),
    health: () =>
      Promise.resolve({
        types,
        farms: [
          {
            farmId: 1,
            farmName: 'Finca Uno',
            cells: {
              WATER_ANALYSIS: { status: 'DUE_SOON', recordId: 10, validUntil: '2026-11-10' },
              TRAINING: { status: 'MISSING', recordId: null, validUntil: null },
            },
          },
        ],
        summary: { CURRENT: 0, DUE_SOON: 1, EXPIRED: 0, UNFAVORABLE: 0, MISSING: 1 },
        renewalWindowDays: 30,
      }),
    matrix: () =>
      Promise.resolve({
        types,
        entries: [
          {
            id: 1,
            typeCode: 'WATER_ANALYSIS',
            typeName: 'Análisis de agua',
            standardCode: 'GLOBALGAP_IFA_V6',
            standardName: 'GLOBALG.A.P. IFA v6',
            controlRef: 'FV 9.3',
            controlTitle: 'Agua de lavado',
            status: 'DRAFT',
          },
        ],
      }),
    create: vi.fn(),
    update: vi.fn(),
  },
  CatalogApi: { standards: () => Promise.resolve([]), standard: vi.fn() },
  AttachmentApi: {},
  downloadBlob: vi.fn(),
}));
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ hasRole: () => true }),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <ConfirmProvider>
          <MemoryRouter>
            <EvidencePage />
          </MemoryRouter>
        </ConfirmProvider>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('EvidencePage', () => {
  it('lista los registros con su vigencia y dónde se usan', async () => {
    renderPage();

    const table = await screen.findByRole('table', { name: 'Evidencias registradas' });
    expect(within(table).getByText('Análisis del pozo 2')).toBeInTheDocument();
    expect(within(table).getByText('Vigente')).toBeInTheDocument();
    expect(within(table).getByText('Finca Uno')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Nueva evidencia' })).toBeEnabled();
    // Un registro en uso no se puede borrar
    expect(screen.getByRole('button', { name: 'Eliminar Análisis del pozo 2' })).toBeDisabled();
  });

  it('el panel de salud muestra el estado de cada tipo y permite registrar lo que falta', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('tab', { name: 'Salud del sistema' }));

    const table = await screen.findByRole('table', { name: 'Salud del sistema de gestión' });
    expect(within(table).getByText('Por renovar')).toBeInTheDocument();
    expect(within(table).getByText('Falta')).toBeInTheDocument();
    expect(screen.getByText(/vence en los próximos 30 días/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Registrar Formación de Finca Uno' }));
    expect(await screen.findByText('Nueva evidencia', { selector: 'h2' })).toBeInTheDocument();
    // El aviso de datos personales del tipo aparece al elegirlo (viene preseleccionado)
    expect(await screen.findByText('Si incluye nombres, márcalo.')).toBeInTheDocument();
  });

  it('las equivalencias se muestran como borrador a validar', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('tab', { name: 'Equivalencias entre normas' }));

    expect(await screen.findByText(/borrador a validar/)).toBeInTheDocument();
    const table = await screen.findByRole('table', { name: 'Equivalencias de Análisis de agua' });
    expect(within(table).getByText('Borrador')).toBeInTheDocument();
    expect(within(table).getByText('FV 9.3')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Validar todas' })).toBeEnabled();
  });
});
