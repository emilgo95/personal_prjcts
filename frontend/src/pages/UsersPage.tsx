import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { UserApi } from '../api/endpoints';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import { useToast } from '../utils/toast';
import { extractErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';

const roles = [
  { value: 'ADMIN', label: 'Administrador' },
  { value: 'AUDITOR', label: 'Auditor' },
  { value: 'QUALITY', label: 'Calidad' },
  { value: 'VIEWER', label: 'Lector' },
];

export default function UsersPage() {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const { user: me } = useAuth();
  const { data, isLoading, error } = useQuery({ queryKey: ['users'], queryFn: UserApi.list });

  const [form, setForm] = useState({ email: '', fullName: '', role: 'AUDITOR', password: '' });

  const create = useMutation({
    mutationFn: () => UserApi.create(form),
    onSuccess: () => {
      notify('Usuario creado', 'success');
      setForm({ email: '', fullName: '', role: 'AUDITOR', password: '' });
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const update = useMutation({
    mutationFn: (v: { id: number; role: string; active: boolean; fullName?: string }) =>
      UserApi.update(v.id, { role: v.role, active: v.active, fullName: v.fullName }),
    onSuccess: () => {
      notify('Usuario actualizado', 'success');
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    create.mutate();
  };

  return (
    <Box>
      <PageHeader title="Usuarios" subtitle="Gestión de usuarios y roles de la organización" />

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Nuevo usuario
          </Typography>
          <form onSubmit={onSubmit}>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField
                  fullWidth
                  required
                  type="email"
                  label="Correo electrónico"
                  value={form.email}
                  onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField
                  fullWidth
                  label="Nombre"
                  value={form.fullName}
                  onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))}
                />
              </Grid>
              <Grid size={{ xs: 6, md: 2 }}>
                <TextField
                  select
                  fullWidth
                  label="Rol"
                  value={form.role}
                  onChange={(e) => setForm((f) => ({ ...f, role: e.target.value }))}
                >
                  {roles.map((r) => (
                    <MenuItem key={r.value} value={r.value}>
                      {r.label}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <TextField
                  fullWidth
                  required
                  type="password"
                  label="Contraseña (mín. 8)"
                  value={form.password}
                  onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))}
                />
              </Grid>
            </Grid>
            <Button type="submit" variant="contained" sx={{ mt: 2 }} disabled={create.isPending}>
              Crear usuario
            </Button>
          </form>
        </CardContent>
      </Card>

      <QueryBoundary isLoading={isLoading} error={error}>
        <Card>
          <CardContent>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Correo</TableCell>
                  <TableCell>Nombre</TableCell>
                  <TableCell>Rol</TableCell>
                  <TableCell align="right">Acciones</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(data ?? []).map((u) => (
                  <TableRow key={u.id} sx={u.active ? undefined : { opacity: 0.6 }}>
                    <TableCell>{u.email}</TableCell>
                    <TableCell>{u.fullName || '—'}</TableCell>
                    <TableCell>
                      <TextField
                        select
                        size="small"
                        value={u.role}
                        disabled={!u.active || u.id === me?.id}
                        onChange={(e) =>
                          update.mutate({ id: u.id, role: e.target.value, active: u.active, fullName: u.fullName ?? undefined })
                        }
                      >
                        {roles.map((r) => (
                          <MenuItem key={r.value} value={r.value}>
                            {r.label}
                          </MenuItem>
                        ))}
                      </TextField>
                    </TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} sx={{ justifyContent: "flex-end" }}>
                        <Chip
                          size="small"
                          label={u.active ? 'Activo' : 'Desactivado'}
                          color={u.active ? 'success' : 'default'}
                          variant="outlined"
                        />
                        <Button
                          size="small"
                          disabled={u.id === me?.id}
                          onClick={() => {
                            if (u.active && !window.confirm(`¿Desactivar a ${u.email}? Dejará de poder iniciar sesión.`)) return;
                            update.mutate({ id: u.id, role: u.role, active: !u.active, fullName: u.fullName ?? undefined });
                          }}
                        >
                          {u.active ? 'Desactivar' : 'Reactivar'}
                        </Button>
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </QueryBoundary>
    </Box>
  );
}
