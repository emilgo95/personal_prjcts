import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router-dom';
import {
  Box,
  Card,
  CardActionArea,
  CardContent,
  Chip,
  Grid,
  LinearProgress,
  Stack,
  Typography,
} from '@mui/material';
import AgricultureIcon from '@mui/icons-material/Agriculture';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import AssignmentLateIcon from '@mui/icons-material/AssignmentLate';
import ReportProblemIcon from '@mui/icons-material/ReportProblem';
import EventBusyIcon from '@mui/icons-material/EventBusy';
import DescriptionIcon from '@mui/icons-material/Description';
import TaskAltIcon from '@mui/icons-material/TaskAlt';
import { DashboardApi } from '../api/endpoints';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import DeadlineChip from '../components/DeadlineChip';
import {
  auditStatusColors,
  auditStatusLabels,
  auditTypeLabels,
  classificationLabels,
  label,
  standardShort,
} from '../utils/labels';

type Tone = 'primary' | 'error' | 'warning' | 'info' | 'success';

function StatCard({
  value,
  title,
  icon,
  tone = 'primary',
  to,
  highlight = false,
}: {
  value: number;
  title: string;
  icon: ReactNode;
  tone?: Tone;
  to?: string;
  highlight?: boolean;
}) {
  const inner = (
    <CardContent>
      <Stack direction="row" spacing={1.5} alignItems="center" mb={1}>
        <Box
          sx={{
            width: 40,
            height: 40,
            borderRadius: '12px',
            display: 'grid',
            placeItems: 'center',
            color: `${tone}.dark`,
            bgcolor: (t) => `${t.palette[tone].main}1f`,
            flexShrink: 0,
          }}
        >
          {icon}
        </Box>
        <Typography variant="h4" fontWeight={800} lineHeight={1} color={highlight ? `${tone}.dark` : 'text.primary'}>
          {value}
        </Typography>
      </Stack>
      <Typography variant="body2" color="text.secondary">
        {title}
      </Typography>
    </CardContent>
  );
  return (
    <Card
      sx={{
        height: '100%',
        borderLeft: 4,
        borderColor: `${tone}.main`,
        ...(highlight && { bgcolor: (t) => `${t.palette[tone].main}0d` }),
      }}
    >
      {to ? (
        <CardActionArea component={RouterLink} to={to} sx={{ height: '100%' }}>
          {inner}
        </CardActionArea>
      ) : (
        inner
      )}
    </Card>
  );
}

const statusOrder = ['DRAFT', 'IN_PROGRESS', 'CLOSING', 'SUBMITTED', 'CLOSED'];

function statusColor(status: string) {
  const c = auditStatusColors[status] ?? 'default';
  return c === 'default' ? 'grey.400' : `${c}.main`;
}

function StatusBar({ byStatus, total }: { byStatus: Record<string, number>; total: number }) {
  const parts = statusOrder.filter((s) => (byStatus[s] ?? 0) > 0);
  const summary = parts.map((s) => `${label(auditStatusLabels, s)}: ${byStatus[s]}`).join(', ');
  return (
    <Box>
      <Box
        role="img"
        aria-label={`Auditorías por estado. ${summary}`}
        sx={{ display: 'flex', height: 14, borderRadius: 7, overflow: 'hidden', bgcolor: 'grey.200', mb: 1.5 }}
      >
        {parts.map((s) => (
          <Box key={s} sx={{ width: `${(100 * byStatus[s]) / total}%`, bgcolor: statusColor(s) }} />
        ))}
      </Box>
      <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
        {parts.map((s) => (
          <Stack key={s} direction="row" spacing={0.75} alignItems="center">
            <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: statusColor(s) }} />
            <Typography variant="body2">
              {label(auditStatusLabels, s)}: <strong>{byStatus[s]}</strong>
            </Typography>
          </Stack>
        ))}
      </Stack>
    </Box>
  );
}

export default function DashboardPage() {
  const { data, isLoading, error } = useQuery({ queryKey: ['dashboard'], queryFn: DashboardApi.load });

  return (
    <Box>
      <PageHeader title="Panel" subtitle="Resumen de auditorías y cumplimiento" />
      <QueryBoundary isLoading={isLoading} error={error}>
        {data && (
          <Stack spacing={3}>
            <Box>
              <Grid container spacing={2}>
                <Grid item xs={6} md={4} lg={2}>
                  <StatCard value={data.farmsCount} title="Explotaciones" icon={<AgricultureIcon />} to="/farms" />
                </Grid>
                <Grid item xs={6} md={4} lg={2}>
                  <StatCard value={data.auditsTotal} title="Auditorías" icon={<FactCheckIcon />} to="/audits" />
                </Grid>
                <Grid item xs={6} md={4} lg={2}>
                  <StatCard
                    value={data.openFindings}
                    title="No conformidades abiertas"
                    icon={<AssignmentLateIcon />}
                    tone="info"
                  />
                </Grid>
                <Grid item xs={6} md={4} lg={2}>
                  <StatCard
                    value={data.overdueFindings}
                    title="No conformidades vencidas"
                    icon={<ReportProblemIcon />}
                    tone={data.overdueFindings > 0 ? 'error' : 'success'}
                    highlight={data.overdueFindings > 0}
                  />
                </Grid>
                <Grid item xs={6} md={4} lg={2}>
                  <StatCard
                    value={data.dueSoonFindings}
                    title="Vencen en 7 días"
                    icon={<EventBusyIcon />}
                    tone={data.dueSoonFindings > 0 ? 'warning' : 'success'}
                    highlight={data.dueSoonFindings > 0}
                  />
                </Grid>
                <Grid item xs={6} md={4} lg={2}>
                  <StatCard
                    value={data.documentsGenerated}
                    title="Documentos generados"
                    icon={<DescriptionIcon />}
                    to="/documents"
                  />
                </Grid>
              </Grid>
            </Box>

            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Auditorías por estado
                </Typography>
                {data.auditsTotal === 0 ? (
                  <Typography variant="body2" color="text.secondary">
                    Todavía no hay auditorías registradas.
                  </Typography>
                ) : (
                  <StatusBar byStatus={data.auditsByStatus} total={data.auditsTotal} />
                )}
              </CardContent>
            </Card>

            <Box>
              <Grid container spacing={3}>
                <Grid item xs={12} lg={6}>
                  <Card sx={{ height: '100%' }}>
                    <CardContent>
                      <Typography variant="h6">Próximos vencimientos</Typography>
                      <Typography variant="body2" color="text.secondary" mb={1}>
                        No conformidades abiertas, de la más urgente a la menos
                      </Typography>
                      {data.upcomingDeadlines.length === 0 ? (
                        <Stack direction="row" spacing={1} alignItems="center" py={2} color="success.dark">
                          <TaskAltIcon />
                          <Typography variant="body2">No hay no conformidades abiertas con plazo pendiente.</Typography>
                        </Stack>
                      ) : (
                        <Stack divider={<Box sx={{ borderBottom: '1px solid', borderColor: 'divider' }} />}>
                          {data.upcomingDeadlines.map((d) => (
                            <Box
                              key={d.findingId}
                              component={RouterLink}
                              to={`/audits/${d.auditId}`}
                              sx={{ textDecoration: 'none', color: 'inherit', py: 1.25, '&:hover': { bgcolor: 'action.hover' } }}
                            >
                              <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2}>
                                <Box sx={{ minWidth: 0 }}>
                                  <Typography fontWeight={600} noWrap>
                                    {d.findingReference} · {d.title || 'Sin título'}
                                  </Typography>
                                  <Typography variant="body2" color="text.secondary" noWrap>
                                    {d.auditReference} · {d.farmName} · {label(classificationLabels, d.classification)}
                                  </Typography>
                                </Box>
                                <DeadlineChip dueDate={d.dueDate} />
                              </Stack>
                            </Box>
                          ))}
                        </Stack>
                      )}
                    </CardContent>
                  </Card>
                </Grid>

                <Grid item xs={12} lg={6}>
                  <Card sx={{ height: '100%' }}>
                    <CardContent>
                      <Typography variant="h6" gutterBottom>
                        Auditorías recientes
                      </Typography>
                      <Stack divider={<Box sx={{ borderBottom: '1px solid', borderColor: 'divider' }} />}>
                        {data.recentAudits.map((a) => (
                          <Box
                            key={a.id}
                            component={RouterLink}
                            to={`/audits/${a.id}`}
                            sx={{ textDecoration: 'none', color: 'inherit', py: 1.25, '&:hover': { bgcolor: 'action.hover' } }}
                          >
                            <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2}>
                              <Box sx={{ minWidth: 0 }}>
                                <Typography fontWeight={600} noWrap>
                                  {a.reference} · {a.farmName}
                                </Typography>
                                <Typography variant="body2" color="text.secondary" noWrap>
                                  {standardShort(a.standardCode, a.standardName)} · {label(auditTypeLabels, a.auditType)}
                                </Typography>
                              </Box>
                              <Stack alignItems="flex-end" spacing={0.5} sx={{ minWidth: 110 }}>
                                <Chip
                                  size="small"
                                  color={auditStatusColors[a.status] ?? 'default'}
                                  label={label(auditStatusLabels, a.status)}
                                />
                                <Box sx={{ width: 100 }}>
                                  <LinearProgress
                                    variant="determinate"
                                    value={a.progressPct}
                                    aria-label={`${a.progressPct}% evaluado`}
                                  />
                                </Box>
                                <Typography variant="caption" color="text.secondary">
                                  {a.progressPct}% evaluado
                                </Typography>
                              </Stack>
                            </Stack>
                          </Box>
                        ))}
                        {data.recentAudits.length === 0 && (
                          <Typography variant="body2" color="text.secondary" py={1}>
                            Sin auditorías todavía.
                          </Typography>
                        )}
                      </Stack>
                    </CardContent>
                  </Card>
                </Grid>
              </Grid>
            </Box>
          </Stack>
        )}
      </QueryBoundary>
    </Box>
  );
}
