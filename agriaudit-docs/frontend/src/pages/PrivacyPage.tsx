import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Card,
  CardContent,
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
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { PrivacyApi, UserApi, downloadBlob } from '../api/endpoints';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import { useToast } from '../utils/toast';
import { extractErrorMessage } from '../api/client';
import { daysUntil, formatDate, formatDateTime } from '../utils/dates';
import { label, roleLabels } from '../utils/labels';

/** «en 14 días», «en 11 meses»… para la fecha de destrucción; «vencido» si ya pasó. */
function timeLeft(iso: string): string {
  const days = daysUntil(iso);
  if (days < 0) return 'vencido: se destruirá en la próxima pasada';
  if (days < 60) return `en ${days} ${days === 1 ? 'día' : 'días'}`;
  const months = Math.round(days / 30);
  return months < 24 ? `en ${months} meses` : `en ${(months / 12).toFixed(1).replace('.', ',')} años`;
}

export default function PrivacyPage() {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const { data, isLoading, error } = useQuery({ queryKey: ['privacy'], queryFn: PrivacyApi.get });
  const blocked = useQuery({ queryKey: ['privacy-blocked'], queryFn: PrivacyApi.blocked });
  const inventory = useQuery({ queryKey: ['privacy-inventory'], queryFn: PrivacyApi.inventory });
  const users = useQuery({ queryKey: ['users'], queryFn: UserApi.list });
  const [months, setMonths] = useState('');
  const [personId, setPersonId] = useState('');

  useEffect(() => {
    if (data) setMonths(data.organizationValue != null ? String(data.organizationValue) : '');
  }, [data]);

  const save = useMutation({
    mutationFn: () => PrivacyApi.setRetention(months.trim() === '' ? null : Number(months)),
    onSuccess: () => {
      notify('Plazo de conservación guardado', 'success');
      queryClient.invalidateQueries({ queryKey: ['privacy'] });
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const exportAll = useMutation({
    mutationFn: PrivacyApi.exportAll,
    onSuccess: (blob) => downloadBlob(blob, `datos-organizacion-${new Date().toISOString().slice(0, 10)}.zip`),
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const exportPerson = useMutation({
    mutationFn: (id: number) => UserApi.dataExport(id),
    onSuccess: (blob, id) => downloadBlob(blob, `datos-usuario-${id}.zip`),
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate();
  };

  const selectable = (users.data ?? []).filter((u) => !u.blocked);

  return (
    <Box>
      <PageHeader
        title="Privacidad"
        subtitle="Datos personales: conservación, bloqueo, acceso de las personas y exportación de la organización"
      />
      <QueryBoundary isLoading={isLoading} error={error}>
        {data && (
          <Stack spacing={3}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Plazo de conservación de datos bloqueados
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  Al bloquear a un usuario su nombre y correo dejan de mostrarse, pero se reservan sin tratarse hasta
                  que venza este plazo; después se destruyen automáticamente. El plazo lo decide la organización
                  (responsable del tratamiento); déjelo vacío para usar el valor por defecto ({data.defaultMonths}{' '}
                  meses).
                </Typography>
                <form onSubmit={onSubmit}>
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: 'flex-start' }}>
                    <TextField
                      label="Meses de conservación"
                      type="number"
                      value={months}
                      onChange={(e) => setMonths(e.target.value)}
                      helperText={`Entre ${data.minMonths} y ${data.maxMonths}. En vigor: ${data.blockedRetentionMonths} meses`}
                      slotProps={{ htmlInput: { min: data.minMonths, max: data.maxMonths } }}
                    />
                    <Button type="submit" variant="contained" disabled={save.isPending} sx={{ mt: 1 }}>
                      Guardar
                    </Button>
                  </Stack>
                </form>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Usuarios con datos bloqueados
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                  Su nombre y su correo están reservados y no se muestran aquí. Se destruyen solos en la fecha
                  indicada.
                </Typography>
                {blocked.isLoading ? null : (blocked.data ?? []).length === 0 ? (
                  <Typography variant="body2">No hay usuarios con datos bloqueados.</Typography>
                ) : (
                  <Box sx={{ overflowX: 'auto' }}>
                    <Table size="small" aria-label="Usuarios con datos bloqueados">
                      <TableHead>
                        <TableRow>
                          <TableCell>Usuario</TableCell>
                          <TableCell>Rol</TableCell>
                          <TableCell>Bloqueado el</TableCell>
                          <TableCell>Se destruye el</TableCell>
                          <TableCell>Bloqueado por</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {(blocked.data ?? []).map((b) => (
                          <TableRow key={b.userId}>
                            <TableCell>Usuario n.º {b.userId}</TableCell>
                            <TableCell>{b.role ? label(roleLabels, b.role) : '—'}</TableCell>
                            <TableCell>{formatDate(b.blockedAt)}</TableCell>
                            <TableCell>
                              {formatDate(b.destroyAfter)}{' '}
                              <Typography component="span" variant="caption" color="text.secondary">
                                ({timeLeft(b.destroyAfter)})
                              </Typography>
                            </TableCell>
                            <TableCell>{b.blockedBy ?? '—'}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </Box>
                )}
                {data.nextDestruction && (
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                    Próxima destrucción: {formatDateTime(data.nextDestruction)}
                  </Typography>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Datos de una persona (derecho de acceso)
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  Descarga lo que la aplicación guarda sobre una persona: su cuenta, el trabajo registrado a su nombre
                  y sus acciones en el historial. No incluye la contraseña, el contenido de los ficheros ni los
                  comentarios, que pueden contener datos de otras personas. Quién responde a la solicitud es la
                  organización (responsable del tratamiento), en el plazo de un mes. Queda constancia en la traza.
                </Typography>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: 'flex-start' }}>
                  <TextField
                    select
                    size="small"
                    label="Persona"
                    value={personId}
                    onChange={(e) => setPersonId(e.target.value)}
                    sx={{ minWidth: 280 }}
                  >
                    {selectable.map((u) => (
                      <MenuItem key={u.id} value={String(u.id)}>
                        {u.fullName ? `${u.fullName} · ${u.email}` : u.email}
                      </MenuItem>
                    ))}
                  </TextField>
                  <Button
                    variant="outlined"
                    disabled={!personId || exportPerson.isPending}
                    onClick={() => exportPerson.mutate(Number(personId))}
                  >
                    {exportPerson.isPending ? 'Preparando…' : 'Descargar sus datos (ZIP)'}
                  </Button>
                </Stack>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Exportar todos los datos de la organización
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  Descarga un ZIP con los datos (JSON), los adjuntos, los documentos generados y la traza de cambios
                  con sus huellas. No incluye contraseñas ni los datos de usuarios bloqueados. Queda constancia de la
                  descarga en la traza.
                </Typography>
                <Button variant="outlined" onClick={() => exportAll.mutate()} disabled={exportAll.isPending}>
                  {exportAll.isPending ? 'Preparando…' : 'Descargar exportación (ZIP)'}
                </Button>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Dónde guarda la aplicación datos de personas
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                  Inventario técnico de los datos personales, quién los ve y cuánto se conservan. Sirve de base para el
                  registro de actividades de tratamiento (art. 30 RGPD), que debe redactar la organización con su
                  asesor.
                </Typography>
                {(inventory.data ?? []).map((item) => (
                  <Accordion key={item.category} disableGutters variant="outlined">
                    <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                      <Box>
                        <Typography sx={{ fontWeight: 600 }}>{item.category}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {item.data}
                        </Typography>
                      </Box>
                    </AccordionSummary>
                    <AccordionDetails>
                      <Stack spacing={0.75}>
                        <Typography variant="body2">
                          <strong>De quién:</strong> {item.whose}
                        </Typography>
                        <Typography variant="body2">
                          <strong>Quién lo ve:</strong> {item.visibleTo}
                        </Typography>
                        <Typography variant="body2">
                          <strong>Conservación:</strong> {item.retention}
                        </Typography>
                        <Typography variant="body2" sx={{ wordBreak: 'break-word' }}>
                          <strong>Dónde:</strong>{' '}
                          <Box component="code" sx={{ fontSize: 12 }}>
                            {item.fields.join(' · ')}
                          </Box>
                        </Typography>
                        {item.notes && <Alert severity="info">{item.notes}</Alert>}
                      </Stack>
                    </AccordionDetails>
                  </Accordion>
                ))}
              </CardContent>
            </Card>

            <Alert severity="info">
              Los plazos de conservación y su encaje legal deben validarse con un especialista en protección de datos.
            </Alert>
          </Stack>
        )}
      </QueryBoundary>
    </Box>
  );
}
