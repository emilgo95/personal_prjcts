import { useQuery } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router-dom';
import {
  Box,
  Card,
  CardActionArea,
  CardContent,
  Chip,
  Grid,
  Stack,
  Typography,
} from '@mui/material';
import { DashboardApi } from '../api/endpoints';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import { auditStatusLabels, auditTypeLabels, label } from '../utils/labels';

function StatCard({ value, title, to }: { value: number; title: string; to?: string }) {
  const inner = (
    <CardContent>
      <Typography variant="h3" fontWeight={800} color="primary">
        {value}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        {title}
      </Typography>
    </CardContent>
  );
  return (
    <Card>
      {to ? <CardActionArea component={RouterLink} to={to}>{inner}</CardActionArea> : inner}
    </Card>
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
            <Grid container spacing={2}>
              <Grid item xs={6} md={3}>
                <StatCard value={data.farmsCount} title="Explotaciones" to="/farms" />
              </Grid>
              <Grid item xs={6} md={3}>
                <StatCard value={data.auditsTotal} title="Auditorías" to="/audits" />
              </Grid>
              <Grid item xs={6} md={3}>
                <StatCard value={data.openFindings} title="No conformidades abiertas" />
              </Grid>
              <Grid item xs={6} md={3}>
                <StatCard value={data.overdueFindings} title="No conformidades vencidas" />
              </Grid>
            </Grid>

            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Auditorías por estado
                </Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  {Object.entries(data.auditsByStatus).map(([status, count]) => (
                    <Chip
                      key={status}
                      label={`${label(auditStatusLabels, status)}: ${count}`}
                      color="primary"
                      variant="outlined"
                    />
                  ))}
                  {Object.keys(data.auditsByStatus).length === 0 && (
                    <Typography variant="body2" color="text.secondary">
                      Todavía no hay auditorías registradas.
                    </Typography>
                  )}
                </Stack>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Auditorías recientes
                </Typography>
                <Stack divider={<Box sx={{ borderBottom: '1px solid #eee' }} />} spacing={1}>
                  {data.recentAudits.map((a) => (
                    <Box
                      key={a.id}
                      component={RouterLink}
                      to={`/audits/${a.id}`}
                      sx={{ textDecoration: 'none', color: 'inherit', py: 1 }}
                    >
                      <Stack direction="row" justifyContent="space-between" alignItems="center">
                        <Box>
                          <Typography fontWeight={600}>
                            {a.reference} · {a.farmName}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            {a.standardName} · {label(auditTypeLabels, a.auditType)}
                          </Typography>
                        </Box>
                        <Stack alignItems="flex-end">
                          <Chip size="small" label={label(auditStatusLabels, a.status)} />
                          <Typography variant="caption" color="text.secondary">
                            {a.progressPct}% evaluado
                          </Typography>
                        </Stack>
                      </Stack>
                    </Box>
                  ))}
                  {data.recentAudits.length === 0 && (
                    <Typography variant="body2" color="text.secondary">
                      Sin auditorías todavía.
                    </Typography>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Stack>
        )}
      </QueryBoundary>
    </Box>
  );
}
