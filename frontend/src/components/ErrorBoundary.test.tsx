import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import ErrorBoundary from './ErrorBoundary';

function Broken({ fail }: { fail: boolean }) {
  if (fail) throw new Error('fallo de prueba');
  return <p>Contenido</p>;
}

describe('ErrorBoundary', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('muestra un aviso con salida en lugar de una pantalla en blanco', () => {
    render(
      <ErrorBoundary>
        <Broken fail />
      </ErrorBoundary>,
    );

    expect(screen.getByText('Algo ha fallado al mostrar esta pantalla')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Volver a cargar' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ir al panel' })).toBeInTheDocument();
  });

  it('vuelve a mostrar el contenido al cambiar de página', () => {
    const { rerender } = render(
      <ErrorBoundary resetKey="/audits/1">
        <Broken fail />
      </ErrorBoundary>,
    );
    expect(screen.queryByText('Contenido')).not.toBeInTheDocument();

    rerender(
      <ErrorBoundary resetKey="/audits">
        <Broken fail={false} />
      </ErrorBoundary>,
    );

    expect(screen.getByText('Contenido')).toBeInTheDocument();
  });
});
