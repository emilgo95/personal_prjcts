import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import ProgressMeter, { progressLevel } from './ProgressMeter';

describe('progressLevel', () => {
  it('rojo si se lleva poco, naranja por la mitad y verde solo si está completo', () => {
    expect(progressLevel(0)).toBe('low');
    expect(progressLevel(39)).toBe('low');
    expect(progressLevel(40)).toBe('mid');
    expect(progressLevel(99)).toBe('mid');
    expect(progressLevel(100)).toBe('done');
  });
});

describe('ProgressMeter', () => {
  it('acompaña el color con texto y expone el valor a los lectores de pantalla', () => {
    render(<ProgressMeter value={55} assessed={11} total={20} />);

    expect(screen.getByText('En curso')).toBeInTheDocument();
    expect(screen.getByText('55%')).toBeInTheDocument();
    expect(screen.getByText('11 de 20 puntos evaluados')).toBeInTheDocument();
    const bar = screen.getByRole('progressbar');
    expect(bar).toHaveAttribute('aria-valuenow', '55');
    expect(bar).toHaveAttribute('aria-valuetext', '55 %, en curso, 11 de 20 puntos evaluados');
  });

  it('marca como completa al 100 % y acota valores fuera de rango', () => {
    const { rerender } = render(<ProgressMeter value={100} />);
    expect(screen.getByText('Completa')).toBeInTheDocument();

    rerender(<ProgressMeter value={250} />);
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100');
    rerender(<ProgressMeter value={-5} />);
    expect(screen.getByText('Poco avance')).toBeInTheDocument();
  });

  it('en la versión compacta muestra porcentaje y puntos', () => {
    render(<ProgressMeter value={10} assessed={2} total={20} compact />);
    expect(screen.getByText('10%')).toBeInTheDocument();
    expect(screen.getByText('2/20 puntos')).toBeInTheDocument();
  });
});
