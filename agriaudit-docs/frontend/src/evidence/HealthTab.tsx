import { useQuery } from '@tanstack/react-query';
import { Alert, Box, Button, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import { EvidenceApi } from '../api/endpoints';
import type { EvidenceHealthStatus } from '../api/types';
import QueryBoundary from '../components/QueryBoundary';
import EvidenceStatusChip from '../components/EvidenceStatusChip';
import { useAuth } from '../auth/AuthContext';
import { formatDate } from '../utils/dates';
import type { RecordPreset } from './RecordDialog';

const ORDER: EvidenceHealthStatus[] = ['CURRENT', 'DUE_SOON', 'EXPIRED', 'UNFAVORABLE', 'MISSING'];

/** Salud del sistema de gestión: estado de cada tipo de evidencia en cada explotación, sea cual sea la norma. */
export default function HealthTab({ onRegister }: { onRegister: (preset: RecordPreset) => void }) {
  const { hasRole } = useAuth();
  const canEdit = hasRole('ADMIN', 'AUDITOR', 'QUALITY');
  const { data, isLoading, error } = useQuery({ queryKey: ['evidence-health'], queryFn: EvidenceApi.health });

  return (
    <QueryBoundary isLoading={isLoading} error={error}>
      {data && (
        <Stack spacing={2}>
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'center' }}>
            {ORDER.map((s) => (
              <Stack key={s} direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
                <EvidenceStatusChip status={s} />
                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                  {data.summary[s] ?? 0}
                </Typography>
              </Stack>
            ))}
          </Stack>
          <Alert severity="info">
            «Por renovar» significa que vence en los próximos {data.renewalWindowDays} días. Un registro desfavorable no cuenta como
            cobertura. Cada hueco se rellena una vez y sirve para todas las normas.
          </Alert>
          {data.farms.length === 0 ? (
            <Typography variant="body2">Todavía no hay explotaciones.</Typography>
          ) : (
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small" aria-label="Salud del sistema de gestión">
                <TableHead>
                  <TableRow>
                    <TableCell>Explotación</TableCell>
                    {data.types.map((t) => (
                      <TableCell key={t.code} sx={{ minWidth: 130 }}>
                        {t.name}
                      </TableCell>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {data.farms.map((f) => (
                    <TableRow key={f.farmId}>
                      <TableCell sx={{ fontWeight: 600 }}>{f.farmName}</TableCell>
                      {data.types.map((t) => {
                        const cell = f.cells[t.code];
                        return (
                          <TableCell key={t.code}>
                            <Stack spacing={0.25} sx={{ alignItems: 'flex-start' }}>
                              <EvidenceStatusChip status={cell?.status ?? 'MISSING'} />
                              {cell?.validUntil && (
                                <Typography variant="caption" color="text.secondary">
                                  hasta {formatDate(cell.validUntil)}
                                </Typography>
                              )}
                              {canEdit && (cell == null || cell.status === 'MISSING') && (
                                <Button
                                  size="small"
                                  sx={{ p: 0, minWidth: 0, textTransform: 'none' }}
                                  aria-label={`Registrar ${t.name} de ${f.farmName}`}
                                  onClick={() => onRegister({ farmId: f.farmId, typeCode: t.code })}
                                >
                                  Registrar
                                </Button>
                              )}
                            </Stack>
                          </TableCell>
                        );
                      })}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          )}
        </Stack>
      )}
    </QueryBoundary>
  );
}
