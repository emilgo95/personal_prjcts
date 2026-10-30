import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import EvidenceCoverage from './EvidenceCoverage';
import { ToastProvider } from '../utils/toast';
import type { EvidenceCoverageItem } from '../api/types';

const link = vi.fn().mockResolvedValue({});
const unlink = vi.fn().mockResolvedValue({});

vi.mock('../api/endpoints', () => ({
  EvidenceApi: {
    link: (...a: unknown[]) => link(...a),
    unlink: (...a: unknown[]) => unlink(...a),
  },
}));

const item: EvidenceCoverageItem = {
  controlPointId: 7,
  controlRef: 'FV 9.3',
  controlTitle: 'Agua de lavado',
  covered: true,
  alert: true,
  types: [
    {
      typeCode: 'WATER_ANALYSIS',
      typeName: 'Análisis de agua',
      matrixStatus: 'DRAFT',
      health: 'CURRENT',
      records: [
        {
          id: 1,
          title: 'Análisis del pozo 2',
          issuedOn: '2026-09-01',
          validUntil: '2027-09-01',
          status: 'CURRENT',
          outcome: 'FAVORABLE',
          documents: 1,
          linkId: null,
          linkable: true,
          notLinkableReason: null,
        },
        {
          id: 2,
          title: 'Análisis antiguo',
          issuedOn: '2025-01-01',
          validUntil: '2025-12-31',
          status: 'EXPIRED',
          outcome: 'FAVORABLE',
          documents: 0,
          linkId: null,
          linkable: false,
          notLinkableReason: 'El registro está vencido: renuévalo para poder usarlo.',
        },
        {
          id: 3,
          title: 'Análisis ya vinculado',
          issuedOn: '2026-08-01',
          validUntil: null,
          status: 'CURRENT',
          outcome: 'FAVORABLE',
          documents: 2,
          linkId: 55,
          linkable: true,
          notLinkableReason: null,
        },
      ],
    },
    { typeCode: 'RISK_ASSESSMENT', typeName: 'Evaluación de riesgos', matrixStatus: 'VALIDATED', health: 'MISSING', records: [] },
  ],
};

function renderIt(canEdit = true) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <MemoryRouter>
          <EvidenceCoverage auditId={3} item={item} canEdit={canEdit} />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('EvidenceCoverage', () => {
  it('sugiere los registros de la explotación y avisa de lo que no sirve', () => {
    renderIt();

    expect(screen.getByText('Análisis de agua')).toBeInTheDocument();
    expect(screen.getByText('Equivalencia por validar')).toBeInTheDocument();
    expect(screen.getByText('Cubierto')).toBeInTheDocument();
    expect(screen.getByText(/Una evidencia vinculada ya no es válida/)).toBeInTheDocument();
    expect(screen.getByText('El registro está vencido: renuévalo para poder usarlo.')).toBeInTheDocument();
    expect(screen.getByText(/No hay ningún registro de este tipo/)).toBeInTheDocument();
    // El que está vencido no se puede vincular
    expect(screen.getByRole('button', { name: 'Vincular Análisis antiguo' })).toBeDisabled();
  });

  it('el auditor confirma el vínculo y puede quitarlo', async () => {
    renderIt();

    fireEvent.click(screen.getByRole('button', { name: 'Vincular Análisis del pozo 2' }));
    await waitFor(() => expect(link).toHaveBeenCalledWith(3, 7, 1));

    fireEvent.click(screen.getByRole('button', { name: 'Quitar vínculo de Análisis ya vinculado' }));
    await waitFor(() => expect(unlink).toHaveBeenCalledWith(3, 55));
  });

  it('sin permiso de edición no hay botones de vincular', () => {
    renderIt(false);
    expect(screen.queryByRole('button', { name: /Vincular/ })).not.toBeInTheDocument();
  });
});
