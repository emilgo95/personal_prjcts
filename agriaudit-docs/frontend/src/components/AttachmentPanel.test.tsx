import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import AttachmentPanel from './AttachmentPanel';
import { ToastProvider } from '../utils/toast';
import { ConfirmProvider } from '../utils/confirm';
import type { Attachment } from '../api/types';

vi.mock('../api/endpoints', () => ({ AttachmentApi: { download: vi.fn() } }));
vi.mock('../utils/useFeatures', () => ({ useFeatures: () => ({ evidenceOcr: false }) }));

const pdf = (id: number, containsPersonalData: boolean | null): Attachment => ({
  id,
  entityType: 'AUDIT',
  entityId: 1,
  fileName: `doc-${id}.pdf`,
  contentType: 'application/pdf',
  sizeBytes: 2048,
  uploadedBy: 'ana@demo.test',
  uploadedAt: '2026-10-01T10:00:00Z',
  image: false,
  containsPersonalData,
});

function renderPanel(props: Partial<Parameters<typeof AttachmentPanel>[0]> = {}) {
  return render(
    <ToastProvider>
      <ConfirmProvider>
        <AttachmentPanel
          attachments={[]}
          canEdit
          onUpload={vi.fn().mockResolvedValue(undefined)}
          onDelete={vi.fn()}
          {...props}
        />
      </ConfirmProvider>
    </ToastProvider>,
  );
}

describe('AttachmentPanel: datos personales', () => {
  it('avisa al subir y envía la casilla marcada con el fichero', async () => {
    const onUpload = vi.fn().mockResolvedValue(undefined);
    const { container } = renderPanel({ onUpload });

    expect(screen.getByText(/Aviso: suba solo lo necesario/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('checkbox'));
    const file = new File(['x'], 'foto.png', { type: 'image/png' });
    fireEvent.change(container.querySelector('input[type=file]') as HTMLInputElement, { target: { files: [file] } });

    await waitFor(() => expect(onUpload).toHaveBeenCalledWith(file, true));
  });

  it('muestra la marca a quien la recibe y la oculta cuando llega null', () => {
    renderPanel({ attachments: [pdf(1, true), pdf(2, false), pdf(3, null)] });

    expect(screen.getByText('Datos personales')).toBeInTheDocument();
    expect(screen.getByText('Sin marca')).toBeInTheDocument();
    expect(screen.getAllByText(/doc-\d\.pdf/)).toHaveLength(3);
    expect(screen.getAllByText(/Datos personales|Sin marca/).length).toBe(2);
  });

  it('permite cambiar la marca cuando hay manejador', async () => {
    const onMark = vi.fn().mockResolvedValue(undefined);
    renderPanel({ attachments: [pdf(1, true)], canEdit: false, onMarkPersonalData: onMark });

    fireEvent.click(screen.getByText('Datos personales'));
    await waitFor(() => expect(onMark).toHaveBeenCalledWith(1, false));
  });
});
