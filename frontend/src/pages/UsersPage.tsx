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
import { UserApi, downloadBlob } from '../api/endpoints';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import { useToast } from '../utils/toast';
import { useConfirm } from '../utils/confirm';
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
  const confirm = useConfirm();
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

  const block = useMutation({
    mutationFn: (id: number) => UserApi.block(id),
    onSuccess: () => {
      notify('Datos del usuario bloqueados', 'success');
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const exportData = useMutation({
    mutationFn: async (u: { id: number }) => ({ id: u.id, blob: await UserApi.dataExport(u.id) }),
    onSuccess: ({ id, blob }) => downloadBlob(blob, `datos-usuario-${id}.zip`),
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
                  <TableRow key={u.id} sx={u.active && !u.blocked ? undefined : { opacity: 0.6 }}>
                    <TableCell>
                      {u.blocked ? (
                        <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
                          Oculto (datos bloqueados)
                        </Typography>
                      ) : (
                        u.email
                      )}
                    </TableCell>
                    <TableCell>{u.fullName || '—'}</TableCell>
                    <TableCell>
                      <TextField
                        select
                        size="small"
                        value={u.role}
                        disabled={!u.active || u.blocked || u.id === me?.id}
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
                          label={u.blocked ? 'Bloqueado' : u.active ? 'Activo' : 'Desactivado'}
                          color={u.blocked ? 'warning' : u.active ? 'success' : 'default'}
                          variant="outlined"
                        />
                        <Button
                          size="small"
                          disabled={u.blocked || u.id === me?.id}
                          onClick={async () => {
                            if (
                              u.active &&
                              !(await confirm({
                                title: `¿Desactivar a ${u.fullName || u.email}?`,
                                message: `${u.email} dejará de poder iniciar sesión. Podrás reactivarlo cuando quieras.`,
                                confirmLabel: 'Desactivar',
                                tone: 'warning',
                              }))
                            )
                              return;
                            update.mutate({ id: u.id, role: u.role, active: !u.active, fullName: u.fullName ?? undefined });
                          }}
                        >
                          {u.active ? 'Desactivar' : 'Reactivar'}
                        </Button>
                        <Button
                          size="small"
                          color="warning"
                          disabled={u.blocked || u.id === me?.id}
                          onClick={async () => {
                            const ok = await confirm({
                              title: `¿Bloquear los datos de ${u.fullName || u.email}?`,
                              message: (
                                <>
                                  <Typography variant="body2" sx={{ mb: 1 }}>
                                    Es lo que pide la protección de datos cuando una persona deja la organización:
                                  </Typography>
                                  <Box component="ul" sx={{ m: 0, pl: 2.5, '& li': { mb: 0.5 } }}>
                                    <li>
                                      Su nombre y su correo ({u.email}) dejarán de mostrarse en toda la aplicación;
                                      aparecerá «Usuario bloqueado».
                                    </li>
                                    <li>No podrá volver a entrar.</li>
                                    <li>
                                      Sus datos originales se guardan aparte, sin acceso desde la aplicación, hasta que
                                      venza el plazo de conservación; después se destruyen.
                                    </li>
                                  </Box>
                                  <Typography variant="body2" sx={{ mt: 1.5, fontWeight: 600, color: 'warning.dark' }}>
                                    No se puede deshacer.
                                  </Typography>
                                </>
                              ),
                              confirmLabel: 'Bloquear datos',
                              tone: 'warning',
                            });
                            if (ok) block.mutate(u.id);
                          }}
                        >
                          Bloquear datos
                        </Button>
                        <Button
                          size="small"
                          disabled={u.blocked || exportData.isPending}
                          title="Descarga lo que la aplicación guarda sobre esta persona (derecho de acceso)"
                          onClick={() => exportData.mutate({ id: u.id })}
                        >
                          Exportar datos
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
