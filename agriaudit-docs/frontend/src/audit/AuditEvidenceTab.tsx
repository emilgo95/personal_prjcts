import { useMemo, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
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
  Link,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import AttachFileIcon from '@mui/icons-material/AttachFile';
import { EvidenceApi, FarmApi } from '../api/endpoints';
import type { EvidenceMap, EvidenceMapRow, EvidenceMapState } from '../api/types';
import QueryBoundary from '../components/QueryBoundary';
import EvidenceStatusChip from '../components/EvidenceStatusChip';
import EvidenceCoverage from './EvidenceCoverage';
import RecordDialog, { type RecordPreset } from '../evidence/RecordDialog';
import { useAuth } from '../auth/AuthContext';
import { evidenceMapStateColors, evidenceMapStateLabels, label, standardShort } from '../utils/labels';

type Filter = 'ALL' | 'COVERED' | 'REVIEW' | 'SUGGESTED' | 'MISSING';

/** Qué estados entran en cada filtro del resumen. */
const filterStates: Record<Exclude<Filter, 'ALL'>, EvidenceMapState[]> = {
  COVERED: ['COVERED', 'DUE_SOON'],
  REVIEW: ['ALERT', 'DUE_SOON'],
  SUGGESTED: ['SUGGESTED'],
  MISSING: ['MISSING', 'ATTACHMENT'],
};

function count(map: EvidenceMap, states: EvidenceMapState[]) {
  return states.reduce((n, s) => n + (map.summary[s] ?? 0), 0);
}

function StateChip({ state }: { state: EvidenceMapState }) {
  const color = evidenceMapStateColors[state] ?? 'default';
  return (
    <Chip
      size="small"
      color={color}
      variant={color === 'default' || state === 'SUGGESTED' ? 'outlined' : 'filled'}
      label={label(evidenceMapStateLabels, state)}
    />
  );
}

/** Lo que justifica el punto: evidencias vinculadas, candidatas por confirmar, la que hay que renovar o lo que falta. */
function EvidenceCell({ row }: { row: EvidenceMapRow }) {
  if (row.evidence.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        {row.evidenceTypes.length > 0 ? `Falta: ${row.evidenceTypes.join(', ')}` : 'Solo adjuntos del checklist'}
      </Typography>
    );
  }
  return (
    <Stack spacing={0.5}>
      {row.evidence.map((e) => (
        <Box key={`${e.recordId}-${e.linked}`}>
          <Typography variant="body2" sx={{ fontWeight: e.linked ? 600 : 400 }}>
            {e.title}
          </Typography>
          <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap', mt: 0.25 }}>
            <EvidenceStatusChip status={e.status} />
            {row.state === 'SUGGESTED' && <Chip size="small" variant="outlined" color="info" label="Sugerida" />}
          </Stack>
        </Box>
      ))}
      {!row.evidence.some((e) => e.linked) && row.state !== 'SUGGESTED' && (
        <Typography variant="caption" color="warning.dark">
          No sirve para este punto: hay que renovarla
        </Typography>
      )}
    </Stack>
  );
}

/**
 * Evidencias de una auditoría en un solo sitio: resumen de cobertura y mapa de equivalencias (cada punto de la norma
 * con lo que lo justifica y los puntos de otras normas donde vale la misma evidencia). Al abrir un punto se confirman
 * los vínculos o se registra la evidencia que falta.
 */
export default function AuditEvidenceTab({ auditId }: { auditId: number }) {
  const queryClient = useQueryClient();
  const { hasRole } = useAuth();
  const canLink = hasRole('ADMIN', 'AUDITOR');
  const canRegister = hasRole('ADMIN', 'AUDITOR', 'QUALITY');
  const [filter, setFilter] = useState<Filter>('ALL');
  const [openPoint, setOpenPoint] = useState<number | null>(null);
  const [register, setRegister] = useState<RecordPreset | null>(null);

  const map = useQuery({ queryKey: ['evidence-map', auditId], queryFn: () => EvidenceApi.map(auditId) });
  const data = map.data;
  const coverage = useQuery({
    queryKey: ['evidence-coverage', auditId],
    queryFn: () => EvidenceApi.coverage(auditId),
    enabled: openPoint != null,
  });
  const farms = useQuery({ queryKey: ['farms'], queryFn: FarmApi.list, enabled: register != null });
  const types = useQuery({ queryKey: ['evidence-types'], queryFn: EvidenceApi.types, enabled: register != null });

  const rows = useMemo(() => {
    const all = data?.rows ?? [];
    return filter === 'ALL' ? all : all.filter((r) => filterStates[filter].includes(r.state));
  }, [data, filter]);

  const selected = data?.rows.find((r) => r.controlPointId === openPoint) ?? null;
  const selectedCoverage = coverage.data?.items.find((i) => i.controlPointId === openPoint) ?? null;
  const missingType = selectedCoverage?.types.find((t) => t.records.length === 0) ?? selectedCoverage?.types[0];

  const onRegistered = () => {
    queryClient.invalidateQueries({ queryKey: ['evidence-map', auditId] });
    queryClient.invalidateQueries({ queryKey: ['evidence-coverage', auditId] });
    queryClient.invalidateQueries({ queryKey: ['evidence-records'] });
    queryClient.invalidateQueries({ queryKey: ['evidence-health'] });
  };

  return (
    <QueryBoundary isLoading={map.isLoading} error={map.error}>
      {data && (
        <Stack spacing={2}>
          <Card>
            <CardContent>
              <Stack
                direction={{ xs: 'column', md: 'row' }}
                spacing={1}
                sx={{ justifyContent: 'space-between', alignItems: { md: 'center' }, mb: 1.5 }}
              >
                <Box>
                  <Typography variant="h6">Evidencias de la auditoría</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {data.rows.length} de {data.summary.POINTS ?? 0} puntos de {standardShort(data.standardCode, data.standardName)} piden
                    evidencia o tienen adjuntos · {data.summary.ATTACHMENTS ?? 0} adjuntos en el checklist
                  </Typography>
                </Box>
                <Stack direction="row" spacing={2} sx={{ flexShrink: 0 }}>
                  <Link component={RouterLink} to={`/audits/evidence?farm=${data.farmId}`} variant="body2">
                    Evidencias de la explotación
                  </Link>
                  <Link component={RouterLink} to="/audits/equivalences" variant="body2">
                    Gestionar equivalencias
                  </Link>
                </Stack>
              </Stack>
              <Stack
                direction="row"
                spacing={1}
                useFlexGap
                sx={{ flexWrap: 'wrap' }}
                role="group"
                aria-label="Filtrar puntos por estado de la evidencia"
              >
                {(
                  [
                    ['ALL', 'Todos', data.rows.length],
                    ['COVERED', 'Cubiertos', count(data, filterStates.COVERED)],
                    ['REVIEW', 'A revisar', count(data, filterStates.REVIEW)],
                    ['SUGGESTED', 'Por confirmar', count(data, filterStates.SUGGESTED)],
                    ['MISSING', 'Sin evidencia válida', count(data, filterStates.MISSING)],
                  ] as Array<[Filter, string, number]>
                ).map(([value, text, n]) => (
                  <Chip
                    key={value}
                    label={`${text} (${n})`}
                    clickable
                    color={filter === value ? 'primary' : 'default'}
                    variant={filter === value ? 'filled' : 'outlined'}
                    aria-pressed={filter === value}
                    onClick={() => setFilter(value)}
                  />
                ))}
              </Stack>
            </CardContent>
          </Card>

          {data.rows.length === 0 ? (
            <Alert severity="info">
              La matriz de equivalencias no relaciona ningún punto de esta norma con evidencias reutilizables y no hay
              adjuntos en el checklist.{' '}
              <Link component={RouterLink} to="/audits/equivalences">
                Revisar las equivalencias
              </Link>
            </Alert>
          ) : (
            <Card>
              <CardContent>
                <Typography variant="h6">Mapa de equivalencias</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                  Cada punto con lo que lo justifica y, en las columnas de la derecha, los puntos de otras normas donde
                  vale la misma evidencia. Abre un punto para confirmar el vínculo o registrar lo que falta.
                </Typography>
                <TableContainer sx={{ maxWidth: '100%', overflowX: 'auto' }}>
                  <Table size="small" aria-label={`Mapa de equivalencias de ${standardShort(data.standardCode, data.standardName)}`}>
                    <TableHead>
                      <TableRow>
                        <TableCell sx={{ minWidth: 200 }}>Punto ({standardShort(data.standardCode, data.standardName)})</TableCell>
                        <TableCell sx={{ minWidth: 200 }}>Evidencia</TableCell>
                        {data.otherStandards.map((s) => (
                          <TableCell key={s.code} sx={{ minWidth: 110 }}>
                            {standardShort(s.code, s.name)}
                          </TableCell>
                        ))}
                        <TableCell align="center">Adjuntos</TableCell>
                        <TableCell>Estado</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {rows.map((row) => (
                        <TableRow
                          key={row.controlPointId}
                          hover
                          tabIndex={0}
                          sx={{ cursor: 'pointer', verticalAlign: 'top' }}
                          onClick={() => setOpenPoint(row.controlPointId)}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter' || e.key === ' ') {
                              e.preventDefault();
                              setOpenPoint(row.controlPointId);
                            }
                          }}
                          aria-label={`Abrir el punto ${row.controlRef}`}
                        >
                          <TableCell>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                              {row.controlRef}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {row.controlTitle}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <EvidenceCell row={row} />
                          </TableCell>
                          {data.otherStandards.map((s) => (
                            <TableCell key={s.code}>
                              {row.equivalences[s.code]?.join(', ') ?? (
                                <Typography component="span" variant="body2" color="text.disabled">
                                  —
                                </Typography>
                              )}
                            </TableCell>
                          ))}
                          <TableCell align="center">
                            {row.attachments > 0 ? (
                              <Stack direction="row" spacing={0.25} sx={{ justifyContent: 'center', alignItems: 'center' }}>
                                <AttachFileIcon fontSize="small" aria-hidden />
                                <span>{row.attachments}</span>
                              </Stack>
                            ) : (
                              '—'
                            )}
                          </TableCell>
                          <TableCell>
                            <StateChip state={row.state} />
                          </TableCell>
                        </TableRow>
                      ))}
                      {rows.length === 0 && (
                        <TableRow>
                          <TableCell colSpan={4 + data.otherStandards.length}>
                            <Typography variant="body2" color="text.secondary">
                              Ningún punto en este estado.
                            </Typography>
                          </TableCell>
                        </TableRow>
                      )}
                    </TableBody>
                  </Table>
                </TableContainer>
              </CardContent>
            </Card>
          )}

          <Dialog open={selected != null} onClose={() => setOpenPoint(null)} maxWidth="sm" fullWidth>
            {selected && (
              <>
                <DialogTitle>
                  {selected.controlRef} · {selected.controlTitle}
                </DialogTitle>
                <DialogContent dividers>
                  <Stack spacing={2}>
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      <StateChip state={selected.state} />
                      {selected.attachments > 0 && (
                        <Typography variant="body2" color="text.secondary">
                          {selected.attachments} adjunto(s) en el checklist
                        </Typography>
                      )}
                    </Stack>
                    {Object.keys(selected.equivalences).length > 0 && (
                      <Box>
                        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                          La misma evidencia vale también para
                        </Typography>
                        {data.otherStandards
                          .filter((s) => selected.equivalences[s.code])
                          .map((s) => (
                            <Typography key={s.code} variant="body2">
                              {standardShort(s.code, s.name)}: {selected.equivalences[s.code].join(', ')}
                            </Typography>
                          ))}
                      </Box>
                    )}
                    {coverage.isLoading && (
                      <Typography variant="body2" color="text.secondary">
                        Cargando evidencias…
                      </Typography>
                    )}
                    {selectedCoverage && (
                      <EvidenceCoverage
                        auditId={auditId}
                        item={selectedCoverage}
                        canEdit={canLink && !data.locked}
                      />
                    )}
                    {!selectedCoverage && !coverage.isLoading && (
                      <Typography variant="body2" color="text.secondary">
                        La matriz no relaciona este punto con ningún tipo de evidencia reutilizable: se justifica con
                        los adjuntos del checklist.
                      </Typography>
                    )}
                  </Stack>
                </DialogContent>
                <DialogActions>
                  {canRegister && missingType && (
                    <Button
                      onClick={() => setRegister({ farmId: data.farmId, typeCode: missingType.typeCode })}
                    >
                      Registrar {missingType.typeName.toLowerCase()}
                    </Button>
                  )}
                  <Button variant="contained" onClick={() => setOpenPoint(null)}>
                    Cerrar
                  </Button>
                </DialogActions>
              </>
            )}
          </Dialog>

          <RecordDialog
            open={register != null}
            onClose={() => setRegister(null)}
            farms={farms.data ?? []}
            types={types.data ?? []}
            preset={register ?? undefined}
            onSaved={onRegistered}
          />
        </Stack>
      )}
    </QueryBoundary>
  );
}
