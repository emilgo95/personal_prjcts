import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PrivacyPage from './PrivacyPage';
import { ToastProvider } from '../utils/toast';

vi.mock('../api/endpoints', () => ({
  PrivacyApi: {
    get: () =>
      Promise.resolve({
        blockedRetentionMonths: 60,
        organizationValue: null,
        defaultMonths: 60,
        minMonths: 1,
        maxMonths: 240,
        blockedCount: 2,
        nextDestruction: null,
      }),
    setRetention: vi.fn(),
    exportAll: vi.fn(),
  },
  downloadBlob: vi.fn(),
}));

describe('PrivacyPage', () => {
  it('muestra el plazo en vigor, los usuarios bloqueados y el botón de exportación', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <ToastProvider>
          <PrivacyPage />
        </ToastProvider>
      </QueryClientProvider>,
    );

    expect(await screen.findByText(/En vigor: 60 meses/)).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Descargar exportación/ })).toBeEnabled();
  });
});
