import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import AuditEvidenceTab from './AuditEvidenceTab';
import { ToastProvider } from '../utils/toast';
import type { EvidenceMap } from '../api/types';

const map: EvidenceMap = {
  auditId: 5,
  farmId: 7,
  standardCode: 'GLOBALGAP_IFA_V6',
  standardName: 'GLOBALG.A.P. IFA v6',
  locked: false,
  otherStandards: [
    { code: 'GRASP_V2', name: 'GRASP' },
    { code: 'SMETA_6_1', name: 'SMETA 6.1' },
  ],
  rows: [
    {
      controlPointId: 1,
      controlRef: 'FV 9.3',
      controlTitle: 'Agua de lavado',
      chapterCode: 'FV',
      level: 'MAJOR_MUST',
      compliance: 'COMPLIANT',
      evidenceTypes: ['Análisis de agua'],
      evidence: [
        { recordId: 10, title: 'Análisis del pozo 2', typeName: 'Análisis de agua', status: 'CURRENT', outcome: 'FAVORABLE', validUntil: '2027-09-01', linked: true },
      ],
      attachments: 2,
      equivalences: { SMETA_6_1: ['3.D'] },
      state: 'COVERED',
    },
    {
      controlPointId: 2,
      controlRef: 'AF 3.2',
      controlTitle: 'Evaluación de riesgos',
      chapterCode: 'AF',
      level: 'MAJOR_MUST',
      compliance: null,
      evidenceTypes: ['Evaluación de riesgos'],
      evidence: [
        { recordId: 11, title: 'Evaluación 2026', typeName: 'Evaluación de riesgos', status: 'DUE_SOON', outcome: 'UNRATED', validUntil: '2026-11-20', linked: false },
      ],
      attachments: 0,
      equivalences: { GRASP_V2: ['1.4'], SMETA_6_1: ['3.2'] },
      state: 'SUGGESTED',
    },
    {
      controlPointId: 3,
      controlRef: 'CB 5.3',
      controlTitle: 'Calibración',
      chapterCode: 'CB',
      level: 'MINOR_MUST',
      compliance: null,
      evidenceTypes: ['Certificado de calibración'],
      evidence: [],
      attachments: 0,
      equivalences: {},
      state: 'MISSING',
    },
  ],
  summary: { COVERED: 1, DUE_SOON: 0, ALERT: 0, SUGGESTED: 1, ATTACHMENT: 0, MISSING: 1, POINTS: 40, ATTACHMENTS: 2 },
};

const coverage = {
  auditId: 5,
  farmId: 7,
  locked: false,
  items: [
    {
      controlPointId: 3,
      controlRef: 'CB 5.3',
      controlTitle: 'Calibración',
      covered: false,
      alert: false,
      types: [{ typeCode: 'CALIBRATION', typeName: 'Certificado de calibración', matrixStatus: 'DRAFT', health: 'MISSING', records: [] }],
    },
  ],
};

vi.mock('../api/endpoints', () => ({
  EvidenceApi: {
    map: () => Promise.resolve(map),
    coverage: () => Promise.resolve(coverage),
    types: () => Promise.resolve([{ code: 'CALIBRATION', name: 'Certificado de calibración', description: null, personalDataNotice: null }]),
    link: vi.fn(),
    unlink: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  },
  FarmApi: { list: () => Promise.resolve([{ id: 7, legalName: 'Campo de Fortuna' }]) },
}));
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ hasRole: () => true }),
}));

function renderTab() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <MemoryRouter>
          <AuditEvidenceTab auditId={5} />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('AuditEvidenceTab', () => {
  it('muestra el resumen y el mapa con una columna por cada otra norma', async () => {
    renderTab();

    expect(await screen.findByText(/3 de 40 puntos de GLOBALG.A.P. IFA v6/)).toBeInTheDocument();
    const table = screen.getByRole('table', { name: 'Mapa de equivalencias de GLOBALG.A.P. IFA v6' });
    expect(within(table).getByRole('columnheader', { name: 'GRASP v2' })).toBeInTheDocument();
    expect(within(table).getByRole('columnheader', { name: 'SMETA 6.1' })).toBeInTheDocument();

    const fv = within(table).getByRole('row', { name: /Abrir el punto FV 9.3/ });
    expect(within(fv).getByText('Análisis del pozo 2')).toBeInTheDocument();
    expect(within(fv).getByText('3.D')).toBeInTheDocument();
    expect(within(fv).getByText('Cubierto')).toBeInTheDocument();

    const af = within(table).getByRole('row', { name: /Abrir el punto AF 3.2/ });
    expect(within(af).getByText('Sugerida')).toBeInTheDocument();
    expect(within(af).getByText('Por confirmar')).toBeInTheDocument();
    expect(within(af).getByText('1.4')).toBeInTheDocument();

    const cb = within(table).getByRole('row', { name: /Abrir el punto CB 5.3/ });
    expect(within(cb).getByText('Falta: Certificado de calibración')).toBeInTheDocument();
  });

  it('filtra por estado', async () => {
    renderTab();
    await screen.findByText(/3 de 40 puntos/);

    fireEvent.click(screen.getByRole('button', { name: 'Por confirmar (1)' }));
    expect(screen.getByRole('button', { name: 'Por confirmar (1)' })).toHaveAttribute('aria-pressed', 'true');

    const table = screen.getByRole('table', { name: /Mapa de equivalencias/ });
    expect(within(table).getByText('AF 3.2')).toBeInTheDocument();
    expect(within(table).queryByText('FV 9.3')).not.toBeInTheDocument();
    expect(within(table).queryByText('CB 5.3')).not.toBeInTheDocument();
  });

  it('al abrir un punto sin evidencia permite registrarla para la explotación de la auditoría', async () => {
    renderTab();
    fireEvent.click(await screen.findByRole('row', { name: /Abrir el punto CB 5.3/ }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('CB 5.3 · Calibración')).toBeInTheDocument();
    expect(await within(dialog).findByText(/No hay ningún registro de este tipo/)).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole('button', { name: 'Registrar certificado de calibración' }));

    expect(await screen.findByText('Nueva evidencia', { selector: 'h2' })).toBeInTheDocument();
  });

  it('enlaza con las evidencias de la explotación y con las equivalencias', async () => {
    renderTab();
    expect(await screen.findByRole('link', { name: 'Evidencias de la explotación' })).toHaveAttribute(
      'href',
      '/audits/evidence?farm=7',
    );
    expect(screen.getByRole('link', { name: 'Gestionar equivalencias' })).toHaveAttribute('href', '/audits/equivalences');
  });
});
