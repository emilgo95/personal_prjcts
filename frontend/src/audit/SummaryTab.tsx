import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  Stack,
  Step,
  StepLabel,
  Stepper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { AuditApi } from '../api/endpoints';
import type { AuditDetail } from '../api/types';
import QueryBoundary from '../components/QueryBoundary';
import ProgressMeter from '../components/ProgressMeter';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../utils/toast';
import { formatDate } from '../utils/dates';
import { extractErrorMessage } from '../api/client';
import {
  auditStatusLabels,
  formatPct,
  label,
  levelLabels,
  overallResultColors,
  overallResultLabels,
} from '../utils/labels';

const statusFlow = ['DRAFT', 'IN_PROGRESS', 'CLOSING', 'SUBMITTED', 'CLOSED'];

export default function SummaryTab({ auditId, audit }: { auditId: number; audit: AuditDetail }) {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const { hasRole } = useAuth();
  const canEdit = hasRole('ADMIN', 'AUDITOR', 'QUALITY');
  // Calidad solo envía, cierra o reabre desde «Enviada»; el resto de pasos son del auditor o de administración.
  const allowedTransitions =
    hasRole('QUALITY') && !hasRole('ADMIN', 'AUDITOR')
      ? audit.allowedTransitions.filter(
          (t) => t === 'SUBMITTED' || t === 'CLOSED' || (audit.status === 'SUBMITTED' && t === 'CLOSING'),
        )
      : audit.allowedTransitions;
  const currentStep = statusFlow.indexOf(audit.status);
  const score = useQuery({ queryKey: ['audit', auditId, 'score'], queryFn: () => AuditApi.score(auditId) });

  const transition = useMutation({
    mutationFn: (status: string) => AuditApi.transition(auditId, status),
    onSuccess: () => {
      notify('Estado actualizado', 'success');
      queryClient.invalidateQueries({ queryKey: ['audit', auditId] });
      queryClient.invalidateQueries({ queryKey: ['audits'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  return (
    <Stack spacing={2}>
      <Card>
        <CardContent>
          <Typography variant="overline" color="text.secondary">
            Estado de la auditoría
          </Typography>
          <Stepper activeStep={currentStep} alternativeLabel sx={{ mt: 1, mb: 2 }}>
            {statusFlow.map((s) => (
              <Step key={s} completed={statusFlow.indexOf(s) < currentStep || audit.status === 'CLOSED'}>
                <StepLabel>{label(auditStatusLabels, s)}</StepLabel>
              </Step>
            ))}
          </Stepper>
          {canEdit && allowedTransitions.length > 0 && (
            <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap", justifyContent: "center" }}>
              {allowedTransitions.map((next) => {
                const forward = statusFlow.indexOf(next) > currentStep;
                return (
                  <Button
                    key={next}
                    variant={forward ? 'contained' : 'outlined'}
                    disabled={transition.isPending}
                    onClick={() => transition.mutate(next)}
                  >
                    {forward
                      ? `Pasar a «${label(auditStatusLabels, next)}»`
                      : `Reabrir como «${label(auditStatusLabels, next)}»`}
                  </Button>
                );
              })}
            </Stack>
          )}
          {audit.status === 'IN_PROGRESS' && audit.assessedCount < audit.totalControlPoints && (
            <Alert severity="info" sx={{ mt: 2 }}>
              Para pasar a cierre hay que evaluar todos los puntos de control: faltan{' '}
              {audit.totalControlPoints - audit.assessedCount}.
            </Alert>
          )}
          {audit.status === 'CLOSED' && (
            <Alert severity="success" sx={{ mt: 2 }}>
              Auditoría cerrada: todas las no conformidades están cerradas o verificadas.
            </Alert>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 5 }}>
              <Typography variant="overline" color="text.secondary">
                Explotación
              </Typography>
              <Typography>{audit.farm.legalName}</Typography>
              <Typography variant="body2" color="text.secondary">
                {audit.farm.registrationNumber} · {audit.farm.city}, {audit.farm.country}
              </Typography>
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <Typography variant="overline" color="text.secondary">
                Auditor responsable
              </Typography>
              <Typography>{audit.leadAuditor || '—'}</Typography>
              {audit.auditTeam && (
                <Typography variant="body2" color="text.secondary">
                  {audit.auditTeam}
                </Typography>
              )}
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <Typography variant="overline" color="text.secondary">
                Fecha de auditoría
              </Typography>
              <Typography>{formatDate(audit.actualDate ?? audit.plannedStartDate)}</Typography>
              {!audit.actualDate && audit.plannedStartDate && (
                <Typography variant="body2" color="text.secondary">
                  Prevista
                </Typography>
              )}
            </Grid>
            {audit.scopeDescription && (
              <Grid size={{ xs: 12 }}>
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
                <Stack direction="row" spacing={2} sx={{ alignItems: "center", mb: 1 }}>
                  <Typography variant="h6">Resultado</Typography>
                  <Chip
                    color={overallResultColors[score.data.overallResult] ?? 'default'}
                    label={label(overallResultLabels, score.data.overallResult)}
                  />
                </Stack>
                <Box sx={{ maxWidth: 420 }}>
                  <ProgressMeter
                    value={score.data.progressPct}
                    assessed={score.data.assessedCount}
                    total={score.data.totalControlPoints}
                    label="Progreso de la evaluación"
                  />
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
                  <TableContainer>
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
                            {l.compliancePct != null ? formatPct(l.compliancePct) : '—'}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                  </TableContainer>
                </CardContent>
              </Card>
            )}

            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Cumplimiento por capítulo
                </Typography>
                <TableContainer>
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
                        <TableCell align="right">{formatPct(g.compliancePct)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
                </TableContainer>
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
