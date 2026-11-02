import { describe, expect, it } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import TraceTimeline from './TraceTimeline';
import type { TraceEntry } from '../api/types';

const entry: TraceEntry = {
  id: 1,
  seq: 1,
  occurredAt: '2026-10-28T09:10:00Z',
  createdAt: '2026-10-28T09:10:00Z',
  userId: 3,
  username: 'lucia.calidad@demo.test',
  userFullName: 'Lucía Calidad',
  userRole: 'QUALITY',
  entityType: 'FINDING',
  entityId: 7,
  entityRef: 'NC-003',
  parentType: null,
  parentId: null,
  auditId: 5,
  action: 'UPDATE',
  summary: 'Cambió el estado de la no conformidad NC-003 de «En curso» a «Cerrada»',
  changes: [{ field: 'status', label: 'Estado', oldValue: 'En curso', newValue: 'Cerrada' }],
  prevHash: '0'.repeat(128),
  hash: 'a'.repeat(128),
  signature: 'b'.repeat(128),
};

describe('TraceTimeline', () => {
  it('muestra quién cambió qué y, al desplegar, el valor anterior y el nuevo', () => {
    render(<TraceTimeline entries={[entry]} />);

    expect(screen.getByText(/Cambió el estado de la no conformidad NC-003/)).toBeInTheDocument();
    expect(screen.getByText(/Lucía Calidad/)).toBeInTheDocument();
    expect(screen.getByText(/Calidad\)/)).toBeInTheDocument();
    expect(screen.queryByText('Cerrada', { selector: 'li *' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Ver los cambios' }));

    expect(screen.getByText('Estado:')).toBeInTheDocument();
    expect(screen.getByText('En curso', { selector: 'span' })).toBeInTheDocument();
  });

  it('avisa cuando todavía no hay cambios', () => {
    render(<TraceTimeline entries={[]} />);

    expect(screen.getByText('Todavía no hay cambios registrados.')).toBeInTheDocument();
  });
});
