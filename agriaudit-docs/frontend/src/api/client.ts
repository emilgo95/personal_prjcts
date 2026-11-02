import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';

export const ACCESS_TOKEN_KEY = 'agriaudit.accessToken';
export const REFRESH_TOKEN_KEY = 'agriaudit.refreshToken';

export const api = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/** Llamadas de autenticación cuyo 401 significa «credenciales o sesión no válidas», no «token caducado». */
const NO_REFRESH_URLS = ['/auth/login', '/auth/refresh'];

type RefreshResult =
  | { token: string }
  | { token: null; sessionInvalid: boolean };

let refreshing: Promise<RefreshResult> | null = null;

async function tryRefresh(): Promise<RefreshResult> {
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
  if (!refreshToken) return { token: null, sessionInvalid: true };
  try {
    const { data } = await axios.post(`${BASE_URL}/auth/refresh`, { refreshToken });
    localStorage.setItem(ACCESS_TOKEN_KEY, data.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, data.refreshToken);
    return { token: data.accessToken as string };
  } catch (e) {
    // Solo una respuesta del servidor que rechaza el token invalida la sesión. Un corte de red o un
    // error 5xx son transitorios: se conservan los tokens para reintentar sin pedir la contraseña.
    const status = axios.isAxiosError(e) ? e.response?.status : undefined;
    const sessionInvalid = status === 400 || status === 401 || status === 403;
    if (sessionInvalid) {
      localStorage.removeItem(ACCESS_TOKEN_KEY);
      localStorage.removeItem(REFRESH_TOKEN_KEY);
    }
    return { token: null, sessionInvalid };
  }
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
    const status = error.response?.status;
    const skipRefresh = NO_REFRESH_URLS.some((u) => original?.url?.includes(u));
    if (status === 401 && original && !original._retried && !skipRefresh) {
      original._retried = true;
      if (!refreshing) {
        refreshing = tryRefresh().finally(() => {
          refreshing = null;
        });
      }
      const result = await refreshing;
      if (!('sessionInvalid' in result)) {
        original.headers.Authorization = `Bearer ${result.token}`;
        return api(original);
      }
      if (result.sessionInvalid) {
        window.dispatchEvent(new CustomEvent('agriaudit:logout'));
      }
    }
    return Promise.reject(error);
  },
);

export function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; fields?: Record<string, string> } | undefined;
    if (data?.message) {
      const fields = data.fields ? ' (' + Object.values(data.fields).join(', ') + ')' : '';
      return data.message + fields;
    }
    return error.message;
  }
  return error instanceof Error ? error.message : 'Error desconocido';
}
