import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import SubmitAuditDialog from './SubmitAuditDialog';
import type { SubmissionCheck } from '../api/types';

const overdue = { id: 1, reference: 'NC-001', title: 'Registro de fitosanitarios', dueDate: '2026-10-01', lateJustification: null };
const toReview = { id: 3, reference: 'NC-003', title: 'Contenedores sin limpieza', dueDate: '2026-11-20', lateJustification: null };

function renderDialog(check: SubmissionCheck) {
  const onSubmit = vi.fn();
  const onClose = vi.fn();
  render(<SubmitAuditDialog check={check} submitting={false} onClose={onClose} onSubmit={onSubmit} />);
  return { onSubmit, onClose };
}

describe('SubmitAuditDialog', () => {
  it('con NC por revisar no deja enviar y dice cuáles verificar', () => {
    renderDialog({ toReview: [toReview], overdue: [overdue] });

    expect(screen.getByText('Todavía no se puede enviar la auditoría')).toBeInTheDocument();
    expect(screen.getByText('NC-003')).toBeInTheDocument();
    expect(screen.getByText(/hay 1 no conformidad vencida/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Enviar la auditoría' })).not.toBeInTheDocument();
  });

  it('con NC vencidas pide el motivo de cada una antes de enviar', () => {
    const { onSubmit } = renderDialog({ toReview: [], overdue: [overdue] });

    expect(screen.getByText('Justifica las no conformidades vencidas')).toBeInTheDocument();
    const send = screen.getByRole('button', { name: 'Enviar la auditoría' });
    expect(send).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/Motivo del retraso de NC-001/), {
      target: { value: '  Pendiente del laboratorio  ' },
    });
    expect(send).toBeEnabled();
    fireEvent.click(send);

    expect(onSubmit).toHaveBeenCalledWith({ 1: 'Pendiente del laboratorio' });
  });

  it('propone la justificación que ya tenía si se envía de nuevo', () => {
    renderDialog({ toReview: [], overdue: [{ ...overdue, lateJustification: 'Motivo anterior' }] });
    expect(screen.getByLabelText(/Motivo del retraso de NC-001/)).toHaveValue('Motivo anterior');
    expect(screen.getByRole('button', { name: 'Enviar la auditoría' })).toBeEnabled();
  });
});
