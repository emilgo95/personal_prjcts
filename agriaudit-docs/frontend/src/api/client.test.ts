import axios, { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ACCESS_TOKEN_KEY, REFRESH_TOKEN_KEY, api } from './client';

type Handler = (config: InternalAxiosRequestConfig) => { status: number; data?: unknown } | 'network-error';

/** Adaptador de prueba: responde según el handler y deja constancia de cada petición. */
function fakeServer(handler: Handler) {
  const calls: Array<{ url: string; auth?: string }> = [];
  const adapter: AxiosAdapter = async (config) => {
    calls.push({ url: config.url ?? '', auth: config.headers?.Authorization as string | undefined });
    const out = handler(config);
    if (out === 'network-error') {
      throw new AxiosError('Network Error', 'ERR_NETWORK', config);
    }
    const response = { data: out.data ?? {}, status: out.status, statusText: '', headers: {}, config };
    if (out.status >= 200 && out.status < 300) return response;
    throw new AxiosError('error', String(out.status), config, null, response);
  };
  api.defaults.adapter = adapter;
  axios.defaults.adapter = adapter;
  return calls;
}

describe('renovación de la sesión', () => {
  let logouts = 0;
  const onLogout = () => {
    logouts += 1;
  };

  beforeEach(() => {
    logouts = 0;
    localStorage.clear();
    localStorage.setItem(ACCESS_TOKEN_KEY, 'acceso-caducado');
    localStorage.setItem(REFRESH_TOKEN_KEY, 'renovacion-valida');
    window.addEventListener('agriaudit:logout', onLogout);
  });

  afterEach(() => {
    window.removeEventListener('agriaudit:logout', onLogout);
    vi.restoreAllMocks();
  });

  it('renueva el token y repite /auth/me cuando el de acceso ha caducado', async () => {
    const calls = fakeServer((config) => {
      if (config.url === '/auth/refresh' || config.url?.endsWith('/auth/refresh')) {
        return { status: 200, data: { accessToken: 'acceso-nuevo', refreshToken: 'renovacion-nueva' } };
      }
      return config.headers?.Authorization === 'Bearer acceso-nuevo'
        ? { status: 200, data: { email: 'ana@demo.test' } }
        : { status: 401 };
    });

    const res = await api.get('/auth/me');

    expect(res.data).toEqual({ email: 'ana@demo.test' });
    expect(calls.map((c) => c.url.replace(/^.*\/auth/, '/auth'))).toEqual(['/auth/me', '/auth/refresh', '/auth/me']);
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBe('acceso-nuevo');
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBe('renovacion-nueva');
    expect(logouts).toBe(0);
  });

  it('cierra la sesión si el servidor rechaza el token de renovación', async () => {
    fakeServer((config) => (config.url?.endsWith('/auth/refresh') ? { status: 401 } : { status: 401 }));

    await expect(api.get('/farms')).rejects.toBeTruthy();

    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull();
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull();
    expect(logouts).toBe(1);
  });

  it('no cierra la sesión ni borra los tokens si la renovación falla por la red', async () => {
    fakeServer((config) => (config.url?.endsWith('/auth/refresh') ? 'network-error' : { status: 401 }));

    await expect(api.get('/farms')).rejects.toBeTruthy();

    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBe('renovacion-valida');
    expect(logouts).toBe(0);
  });

  it('un 401 en el inicio de sesión no intenta renovar nada', async () => {
    const calls = fakeServer(() => ({ status: 401 }));

    await expect(api.post('/auth/login', { email: 'a', password: 'b' })).rejects.toBeTruthy();

    expect(calls).toHaveLength(1);
    expect(logouts).toBe(0);
  });
});
