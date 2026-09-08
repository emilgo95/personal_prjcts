import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  LinearProgress,
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
import { AuditApi } from '../api/endpoints';
import type { AuditDetail } from '../api/types';
import QueryBoundary from '../components/QueryBoundary';
import { useToast } from '../utils/toast';
import { extractErrorMessage } from '../api/client';
import {
  auditStatusLabels,
  label,
  levelLabels,
  overallResultColors,
  overallResultLabels,
} from '../utils/labels';

export default function SummaryTab({ auditId, audit }: { auditId: number; audit: AuditDetail }) {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const score = useQuery({ queryKey: ['audit', auditId, 'score'], queryFn: () => AuditApi.score(auditId) });

  const transition = useMutation({
    mutationFn: (status: string) => AuditApi.transition(auditId, status),
    onSuccess: () => {
      notify('Estado actualizado', 'success');
      queryClient.invalidateQueries({ queryKey: ['audit', auditId] });
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  return (
    <Stack spacing={2}>
      <Card>
        <CardContent>
          <Grid container spacing={2}>
            <Grid item xs={12} md={6}>
              <Typography variant="overline" color="text.secondary">
                Explotación
              </Typography>
              <Typography>{audit.farm.legalName}</Typography>
              <Typography variant="body2" color="text.secondary">
                {audit.farm.registrationNumber} · {audit.farm.city} {audit.farm.country}
              </Typography>
            </Grid>
            <Grid item xs={12} md={3}>
              <Typography variant="overline" color="text.secondary">
                Auditor responsable
              </Typography>
              <Typography>{audit.leadAuditor || '—'}</Typography>
            </Grid>
            <Grid item xs={12} md={3}>
              <Typography variant="overline" color="text.secondary">
                Cambiar estado
              </Typography>
              <TextField
                select
                size="small"
                fullWidth
                value={audit.status}
                onChange={(e) => transition.mutate(e.target.value)}
              >
                {Object.entries(auditStatusLabels).map(([k, v]) => (
                  <MenuItem key={k} value={k}>
                    {v}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            {audit.scopeDescription && (
              <Grid item xs={12}>
                <Typography variant="overline" color="text.secondary">
                  Alcance
                </Typography>
                <Typography variant="body2">{audit.scopeDescription}</Typography>
              </Grid>
            )}
          </Grid>
        </CardContent>
      </Card>

      <QueryBoundary isLoading={score.isLoading} error={score.error}>
        {score.data && (
          <>
            <Card>
              <CardContent>
                <Stack direction="row" spacing={2} alignItems="center" mb={1}>
                  <Typography variant="h6">Resultado</Typography>
                  <Chip
                    color={overallResultColors[score.data.overallResult] ?? 'default'}
                    label={label(overallResultLabels, score.data.overallResult)}
                  />
                </Stack>
                <Box sx={{ maxWidth: 420 }}>
                  <Typography variant="body2" gutterBottom>
                    Progreso de la evaluación: {score.data.progressPct}%
                  </Typography>
                  <LinearProgress variant="determinate" value={score.data.progressPct} />
                </Box>
                <Stack component="ul" sx={{ mt: 1, pl: 2 }} spacing={0.5}>
                  {score.data.notes.map((n, i) => (
                    <Typography key={i} component="li" variant="body2">
                      {n}
                    </Typography>
                  ))}
                </Stack>
              </CardContent>
            </Card>

            {score.data.levelBreakdown.length > 0 && (
              <Card>
                <CardContent>
                  <Typography variant="h6" gutterBottom>
                    Cumplimiento por nivel
                  </Typography>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Nivel</TableCell>
                        <TableCell align="right">Conformes</TableCell>
                        <TableCell align="right">No conformes</TableCell>
                        <TableCell align="right">No aplica</TableCell>
                        <TableCell align="right">Sin evaluar</TableCell>
                        <TableCell align="right">% cumpl.</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {score.data.levelBreakdown.map((l) => (
                        <TableRow key={l.level}>
                          <TableCell>{label(levelLabels, l.level)}</TableCell>
                          <TableCell align="right">{l.compliant}</TableCell>
                          <TableCell align="right">{l.nonCompliant}</TableCell>
                          <TableCell align="right">{l.notApplicable}</TableCell>
                          <TableCell align="right">{l.notAssessed}</TableCell>
                          <TableCell align="right">
                            {l.compliancePct != null ? `${l.compliancePct.toFixed(1)}%` : '—'}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
            )}

            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Cumplimiento por capítulo
                </Typography>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Capítulo</TableCell>
                      <TableCell align="right">Conformes</TableCell>
                      <TableCell align="right">No conformes</TableCell>
                      <TableCell align="right">No aplica</TableCell>
                      <TableCell align="right">Sin evaluar</TableCell>
                      <TableCell align="right">% conf.</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {score.data.chapterBreakdown.map((g) => (
                      <TableRow key={g.code}>
                        <TableCell>{g.title}</TableCell>
                        <TableCell align="right">{g.compliant}</TableCell>
                        <TableCell align="right">{g.nonCompliant}</TableCell>
                        <TableCell align="right">{g.notApplicable}</TableCell>
                        <TableCell align="right">{g.notAssessed}</TableCell>
                        <TableCell align="right">{g.compliancePct.toFixed(1)}%</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>

            {score.data.reportOnly && (
              <Alert severity="info">
                Este estándar produce un informe de evaluación; no genera un resultado apto/no apto automático.
              </Alert>
            )}
          </>
        )}
      </QueryBoundary>
    </Stack>
  );
}
