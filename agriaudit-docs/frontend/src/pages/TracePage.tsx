import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import VerifiedOutlinedIcon from '@mui/icons-material/VerifiedOutlined';
import { TraceApi } from '../api/endpoints';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import TraceTimeline from '../components/TraceTimeline';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../utils/toast';
import { extractErrorMessage } from '../api/client';
import { formatDateTime } from '../utils/dates';
import { traceActionLabels, traceEntityLabels } from '../utils/labels';

/** Trazabilidad de toda la organización: filtros, exportación, comprobación de integridad y sellos. */
export default function TracePage() {
  const { hasRole } = useAuth();
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const [filters, setFilters] = useState({ user: '', action: '', entityType: '', from: '', to: '' });
  const [applied, setApplied] = useState(filters);
  const [page, setPage] = useState(0);
  const [sealsOpen, setSealsOpen] = useState(false);

  const history = useQuery({
    queryKey: ['trace', applied, page],
    queryFn: () => TraceApi.search({ ...applied, page, size: 50 }),
  });
  const verify = useMutation({
    mutationFn: TraceApi.verify,
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });
  const seals = useQuery({ queryKey: ['trace', 'seals'], queryFn: TraceApi.seals, enabled: sealsOpen });
  const sealNow = useMutation({
    mutationFn: TraceApi.sealNow,
    onSuccess: (r) => {
      notify(r.message ?? 'Sello creado', r.message ? 'info' : 'success');
      queryClient.invalidateQueries({ queryKey: ['trace', 'seals'] });
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const set = (key: keyof typeof filters) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setFilters((f) => ({ ...f, [key]: e.target.value }));
  const data = history.data;
  const report = verify.data;

  return (
    <Box>
      <PageHeader
        title="Trazabilidad"
        subtitle="Quién cambió qué y cuándo. Registro inalterable, encadenado con SHA-512 y firmado."
        action={
          <Stack direction="row" spacing={1}>
            <Button variant="outlined" onClick={() => setSealsOpen(true)}>
              Sellos
            </Button>
            <Button variant="outlined" onClick={() => TraceApi.downloadCsv(applied)}>
              Exportar CSV
            </Button>
            <Button
              variant="contained"
              startIcon={<VerifiedOutlinedIcon />}
              disabled={verify.isPending}
              onClick={() => verify.mutate()}
            >
              Verificar integridad
            </Button>
          </Stack>
        }
      />

      {report && (
        <Alert severity={report.ok ? 'success' : 'error'} sx={{ mb: 2 }} onClose={() => verify.reset()}>
          {report.message}
          {report.ok && ` · ${formatDateTime(report.verifiedAt)}`}
        </Alert>
      )}

      <Card sx={{ mb: 2 }}>
        <CardContent>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField label="Usuario" size="small" fullWidth value={filters.user} onChange={set('user')} />
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <TextField select label="Acción" size="small" fullWidth value={filters.action} onChange={set('action')}>
                <MenuItem value="">Todas</MenuItem>
                {Object.entries(traceActionLabels).map(([k, v]) => (
                  <MenuItem key={k} value={k}>
                    {v}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <TextField
                select
                label="Elemento"
                size="small"
                fullWidth
                value={filters.entityType}
                onChange={set('entityType')}
              >
                <MenuItem value="">Todos</MenuItem>
                {Object.entries(traceEntityLabels).map(([k, v]) => (
                  <MenuItem key={k} value={k}>
                    {v}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <TextField
                label="Desde"
                type="date"
                size="small"
                fullWidth
                slotProps={{ inputLabel: { shrink: true } }}
                value={filters.from}
                onChange={set('from')}
              />
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <TextField
                label="Hasta"
                type="date"
                size="small"
                fullWidth
                slotProps={{ inputLabel: { shrink: true } }}
                value={filters.to}
                onChange={set('to')}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 1 }}>
              <Button
                fullWidth
                variant="contained"
                onClick={() => {
                  setPage(0);
                  setApplied(filters);
                }}
              >
                Filtrar
              </Button>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      <QueryBoundary isLoading={history.isLoading} error={history.error}>
        <Card>
          <CardContent>
            <TraceTimeline entries={data?.content ?? []} empty="No hay cambios que coincidan con el filtro." />
            {data && data.totalPages > 1 && (
              <Stack direction="row" spacing={1} sx={{ mt: 2, alignItems: 'center' }}>
                <Button size="small" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                  Más recientes
                </Button>
                <Typography variant="caption">
                  Página {page + 1} de {data.totalPages} · {data.totalElements} entradas
                </Typography>
                <Button size="small" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
                  Más antiguos
                </Button>
              </Stack>
            )}
          </CardContent>
        </Card>
      </QueryBoundary>

      <Dialog open={sealsOpen} onClose={() => setSealsOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>Sellos de la cadena</DialogTitle>
        <DialogContent dividers>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Cada noche se firma la última huella de la cadena. Descarga el fichero y guárdalo fuera de la
            aplicación: permite demostrar que el registro no se reescribió desde esa fecha.
          </Typography>
          <QueryBoundary isLoading={seals.isLoading} error={seals.error}>
            <Stack spacing={1}>
              {(seals.data ?? []).map((s) => (
                <Box key={s.id} sx={{ border: 1, borderColor: 'divider', borderRadius: 1, p: 1 }}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <Chip size="small" label={`Entrada ${s.lastSeq}`} />
                    <Typography variant="caption">{formatDateTime(s.sealedAt)}</Typography>
                  </Stack>
                  <Typography variant="caption" sx={{ wordBreak: 'break-all', fontFamily: 'monospace' }}>
                    {s.lastHash.slice(0, 64)}…
                  </Typography>
                </Box>
              ))}
              {seals.data?.length === 0 && <Typography color="text.secondary">Todavía no hay sellos.</Typography>}
            </Stack>
          </QueryBoundary>
        </DialogContent>
        <DialogActions>
          {hasRole('ADMIN') && (
            <Button onClick={() => sealNow.mutate()} disabled={sealNow.isPending}>
              Sellar ahora
            </Button>
          )}
          <Button onClick={() => TraceApi.downloadSeals()}>Descargar sellos</Button>
          <Button onClick={() => setSealsOpen(false)}>Cerrar</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
