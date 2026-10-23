import axios from 'axios';
import { useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { AuthApi } from '../api/endpoints';
import { ACCESS_TOKEN_KEY, REFRESH_TOKEN_KEY } from '../api/client';
import type { UserDto } from '../api/types';

interface AuthContextValue {
  user: UserDto | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  hasRole: (...roles: Array<UserDto['role']>) => boolean;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserDto | null>(null);
  const [loading, setLoading] = useState(true);

  const queryClient = useQueryClient();

  const logout = useCallback(() => {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    // Otro usuario del mismo navegador no debe ver datos de la sesión anterior.
    queryClient.clear();
    setUser(null);
  }, [queryClient]);

  useEffect(() => {
    const handler = () => logout();
    window.addEventListener('agriaudit:logout', handler);
    return () => window.removeEventListener('agriaudit:logout', handler);
  }, [logout]);

  useEffect(() => {
    const token = localStorage.getItem(ACCESS_TOKEN_KEY);
    if (!token) {
      setLoading(false);
      return;
    }
    AuthApi.me()
      .then(setUser)
      .catch((e) => {
        // Solo una sesión rechazada por el servidor cierra la sesión; un corte de red no borra los tokens.
        if (axios.isAxiosError(e) && e.response?.status === 401) logout();
      })
      .finally(() => setLoading(false));
  }, [logout]);

  const login = useCallback(async (email: string, password: string) => {
    const res = await AuthApi.login(email, password);
    localStorage.setItem(ACCESS_TOKEN_KEY, res.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, res.refreshToken);
    setUser(res.user);
  }, []);

  const hasRole = useCallback(
    (...roles: Array<UserDto['role']>) => (user ? roles.includes(user.role) : false),
    [user],
  );

  const value = useMemo(
    () => ({ user, loading, login, logout, hasRole }),
    [user, loading, login, logout, hasRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth debe usarse dentro de AuthProvider');
  return ctx;
}
