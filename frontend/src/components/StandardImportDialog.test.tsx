import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import StandardImportDialog from './StandardImportDialog';
import type { ImportPreview } from '../api/types';

const invalid: ImportPreview = {
  valid: false,
  storedCode: 'COOP_V1',
  chapterCount: 0,
  pointCount: 0,
  levelCounts: {},
  issues: [{ row: 3, column: 'Referencia', message: 'La referencia «1.1» está repetida en el capítulo «1»' }],
  totalIssues: 1,
  sample: [],
};
const valid: ImportPreview = {
  valid: true,
  storedCode: 'COOP_V1',
  chapterCount: 2,
  pointCount: 3,
  levelCounts: { MAJOR_MUST: 1, MINOR_MUST: 1, RECOMMENDATION: 1 },
  issues: [],
  totalIssues: 0,
  sample: [{ row: 2, chapterCode: '1', ref: '1.1', title: 'Requisito', level: 'MAJOR_MUST' }],
};

const previewImport = vi.fn();
const importStandard = vi.fn();
vi.mock('../api/endpoints', () => ({
  CatalogApi: {
    previewImport: (...a: unknown[]) => previewImport(...a),
    importStandard: (...a: unknown[]) => importStandard(...a),
    downloadTemplate: vi.fn(),
  },
}));

function setup() {
  const onImported = vi.fn();
  render(<StandardImportDialog open onClose={() => undefined} onImported={onImported} />);
  fireEvent.change(screen.getByLabelText(/Código/), { target: { value: 'COOP' } });
  fireEvent.change(screen.getByLabelText(/Nombre/), { target: { value: 'Mi listado' } });
  fireEvent.change(screen.getByLabelText(/Versión/), { target: { value: '1' } });
  const input = document.querySelector('input[type=file]') as HTMLInputElement;
  const file = new File(['a;b'], 'listado.csv', { type: 'text/csv' });
  return { onImported, input, file };
}

describe('StandardImportDialog', () => {
  it('no deja validar hasta tener código, nombre, versión y fichero', async () => {
    const { input, file } = setup();
    expect(screen.getByRole('button', { name: 'Validar' })).toBeDisabled();

    fireEvent.change(input, { target: { files: [file] } });

    expect(screen.getByRole('button', { name: 'Validar' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Importar' })).toBeDisabled();
  });

  it('muestra los problemas con su fila y columna y no permite importar', async () => {
    previewImport.mockResolvedValueOnce(invalid);
    const { input, file } = setup();
    fireEvent.change(input, { target: { files: [file] } });

    fireEvent.click(screen.getByRole('button', { name: 'Validar' }));

    expect(await screen.findByText(/1 problema/)).toBeInTheDocument();
    expect(screen.getByText('Referencia')).toBeInTheDocument();
    expect(screen.getByText(/está repetida/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Importar' })).toBeDisabled();
  });

  it('con un listado válido permite importar y avisa con el código creado', async () => {
    previewImport.mockResolvedValueOnce(valid);
    importStandard.mockResolvedValueOnce({ code: 'COOP_V1' });
    const { onImported, input, file } = setup();
    fireEvent.change(input, { target: { files: [file] } });
    fireEvent.click(screen.getByRole('button', { name: 'Validar' }));

    expect(await screen.findByText(/El listado es válido: 3 puntos de control en 2 capítulos/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Importar' }));

    await waitFor(() => expect(onImported).toHaveBeenCalledWith('COOP_V1'));
  });
});
