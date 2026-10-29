import { useMemo, useState, type CSSProperties, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router-dom';
import { keyframes } from '@emotion/react';
import { Alert, Box, Button, Card, CardContent, CircularProgress, Stack, Typography } from '@mui/material';
import AgricultureIcon from '@mui/icons-material/Agriculture';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import AssignmentLateIcon from '@mui/icons-material/AssignmentLate';
import ReportProblemIcon from '@mui/icons-material/ReportProblem';
import EventBusyIcon from '@mui/icons-material/EventBusy';
import DescriptionIcon from '@mui/icons-material/Description';
import TaskAltIcon from '@mui/icons-material/TaskAlt';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import { DashboardApi } from '../api/endpoints';
import { extractErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { greetingForDate, sceneForDate } from '../auth/loginScenes';
import ProgressMeter from '../components/ProgressMeter';
import AuditStatusChip from '../components/AuditStatusChip';
import DeadlineChip from '../components/DeadlineChip';
import { useCountUp, useDashboardScroll } from '../utils/motion';
import {
  auditStatusStyle,
  auditStatusLabels,
  auditTypeLabels,
  classificationLabels,
  label,
  standardShort,
} from '../utils/labels';

/** Misma animación lenta de la ilustración que en la pantalla de acceso, para que ambas se sientan una sola marca. */
const kenBurns = keyframes`
  from { transform: scale(1.06) translate3d(0, 0, 0); }
  to { transform: scale(1.14) translate3d(-1.5%, -1%, 0); }
`;
const bounce = keyframes`
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(7px); }
`;
const rise = keyframes`
  from { opacity: 0; transform: translateY(22px); }
  to { opacity: 1; transform: translateY(0); }
`;

const glass = {
  bgcolor: 'rgba(255, 255, 255, 0.13)',
  backdropFilter: 'blur(14px) saturate(140%)',
  border: '1px solid rgba(255, 255, 255, 0.26)',
  boxShadow: '0 18px 44px rgba(6, 24, 16, 0.32), inset 0 1px 0 rgba(255, 255, 255, 0.2)',
} as const;

type Tone = 'neutral' | 'info' | 'ok' | 'warn' | 'alert';
const toneAccent: Record<Tone, string> = {
  neutral: '#cdeea3',
  info: '#9bd7ff',
  ok: '#a5f0b5',
  warn: '#ffd180',
  alert: '#ff9e94',
};

function KpiTile({
  value,
  title,
  note,
  icon,
  tone = 'neutral',
  to,
  index,
}: {
  value: number;
  title: string;
  note?: string;
  icon: ReactNode;
  tone?: Tone;
  to?: string;
  index: number;
}) {
  const shown = useCountUp(value);
  const accent = toneAccent[tone];
  return (
    <Box
      {...(to ? { component: RouterLink, to } : {})}
      sx={{
        ...glass,
        position: 'relative',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        gap: 1,
        minHeight: { xs: 132, md: 168 },
        p: { xs: 2, md: 2.75 },
        borderRadius: 4,
        color: 'common.white',
        textDecoration: 'none',
        borderTop: `3px solid ${accent}`,
        animation: `${rise} 700ms cubic-bezier(0.22, 1, 0.36, 1) ${160 + index * 90}ms both`,
        transition: 'transform 240ms ease, background-color 240ms ease',
        '&:hover': to ? { transform: 'translateY(-4px)', bgcolor: 'rgba(255, 255, 255, 0.2)' } : undefined,
        '&:focus-visible': { outline: '3px solid #ffffff', outlineOffset: 2 },
        '@media (prefers-reduced-motion: reduce)': { animation: 'none', transition: 'none' },
      }}
    >
      <Box
        aria-hidden
        sx={{
          width: 38,
          height: 38,
          borderRadius: '12px',
          display: 'grid',
          placeItems: 'center',
          color: accent,
          bgcolor: 'rgba(255, 255, 255, 0.14)',
          border: '1px solid rgba(255, 255, 255, 0.22)',
        }}
      >
        {icon}
      </Box>
      <Box>
        <Typography
          component="p"
          aria-label={`${value} ${title}`}
          sx={{
            fontSize: { xs: '2.6rem', md: 'clamp(3rem, 5.4vw, 4.4rem)' },
            fontWeight: 800,
            lineHeight: 1,
            letterSpacing: '-0.03em',
            fontVariantNumeric: 'tabular-nums',
            textShadow: '0 2px 18px rgba(0,0,0,0.3)',
          }}
        >
          <span aria-hidden>{shown.toLocaleString('es-ES')}</span>
        </Typography>
        <Typography sx={{ mt: 0.75, fontWeight: 600, fontSize: { xs: '0.9rem', md: '1rem' }, lineHeight: 1.25 }}>
          {title}
        </Typography>
        {note && (
          <Typography variant="caption" sx={{ color: accent, fontWeight: 700, letterSpacing: '0.02em' }}>
            {note}
          </Typography>
        )}
      </Box>
    </Box>
  );
}

const statusOrder = ['DRAFT', 'IN_PROGRESS', 'CLOSING', 'SUBMITTED', 'CLOSED'];

function statusColor(status: string) {
  return (auditStatusStyle[status] ?? auditStatusStyle.DRAFT).solid;
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
      <Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: 'wrap' }}>
        {parts.map((s) => (
          <Stack key={s} direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
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

/** Aparición en cascada de las tarjetas del detalle cuando entran en pantalla. */
function revealStyle(revealed: boolean, index: number, reduced: boolean): CSSProperties {
  if (reduced) return {};
  return {
    opacity: revealed ? 1 : 0,
    transform: revealed ? 'none' : 'translateY(36px)',
    transition: `opacity 700ms ease ${index * 120}ms, transform 800ms cubic-bezier(0.22, 1, 0.36, 1) ${index * 120}ms`,
  };
}

export default function DashboardPage() {
  const { data, isLoading, error } = useQuery({ queryKey: ['dashboard'], queryFn: DashboardApi.load });
  const { user } = useAuth();
  const { rootRef, detailRef, goDetail, goTop, revealed, reduced } = useDashboardScroll();
  const [imageReady, setImageReady] = useState(false);

  const now = useMemo(() => new Date(), []);
  const scene = sceneForDate(now);
  const firstName = (user?.fullName || '').trim().split(/\s+/)[0];

  return (
    <Box
      ref={rootRef}
      sx={{
        // Anula el margen interior de la página para que el bloque de indicadores ocupe todo el ancho y alto.
        mx: { xs: -2, md: -3 },
        mt: { xs: -2, md: -3 },
        '--p': 0,
      }}
    >
      {/* ---------------------------------------------------------------- Indicadores */}
      <Box
        component="section"
        aria-label="Indicadores principales"
        sx={{
          position: 'relative',
          overflow: 'hidden',
          bgcolor: '#22402c',
          minHeight: { xs: 'calc(100svh - 56px)', sm: 'calc(100svh - 64px)' },
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <Box
          component="img"
          src={scene.image}
          alt=""
          aria-hidden
          data-testid="dashboard-scene"
          onLoad={() => setImageReady(true)}
          sx={{
            position: 'absolute',
            inset: 0,
            width: '100%',
            height: '100%',
            objectFit: 'cover',
            opacity: imageReady ? 1 : 0,
            transition: 'opacity 900ms ease',
            // Paralaje: el fondo baja más despacio que la página y se acerca un poco al avanzar hacia el detalle.
            transform: 'translate3d(0, calc(var(--p) * 90px), 0) scale(calc(1.06 + var(--p) * 0.06))',
            animation: `${kenBurns} 45s ease-in-out infinite alternate`,
            willChange: 'transform',
            '@media (prefers-reduced-motion: reduce)': { animation: 'none', transition: 'none', transform: 'none' },
          }}
        />
        <Box
          aria-hidden
          sx={{
            position: 'absolute',
            inset: 0,
            background:
              'linear-gradient(180deg, rgba(10,32,24,0.55) 0%, rgba(10,32,24,0.18) 38%, rgba(8,24,18,0.62) 100%),' +
              'linear-gradient(90deg, rgba(12,38,30,0.4) 0%, rgba(12,38,30,0) 60%)',
          }}
        />

        <Box
          sx={{
            position: 'relative',
            zIndex: 1,
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'center',
            gap: { xs: 2.5, md: 4 },
            px: { xs: 2, sm: 4, md: 6 },
            pt: { xs: 3, md: 4 },
            pb: { xs: 14, md: 12 },
            // Al bajar, el contenido se desvanece, se aleja y sube un poco, como en las páginas de producto de Apple.
            opacity: 'calc(1 - var(--p) * 1.25)',
            transform: 'translate3d(0, calc(var(--p) * -48px), 0) scale(calc(1 - var(--p) * 0.05))',
            transformOrigin: '50% 40%',
            willChange: 'transform, opacity',
            '@media (prefers-reduced-motion: reduce)': { transform: 'none', opacity: 1 },
          }}
        >
          <Box
            sx={{
              animation: `${rise} 700ms cubic-bezier(0.22, 1, 0.36, 1) both`,
              '@media (prefers-reduced-motion: reduce)': { animation: 'none' },
            }}
          >
            <Typography
              component="h1"
              sx={{
                color: 'common.white',
                fontSize: { xs: '2rem', md: '3rem', lg: '3.4rem' },
                fontWeight: 700,
                lineHeight: 1.08,
                letterSpacing: '-0.025em',
                textShadow: '0 2px 24px rgba(0,0,0,0.35)',
                textWrap: 'balance',
              }}
            >
              {greetingForDate(now)}
              {firstName ? `, ${firstName}` : ''}
            </Typography>
            <Typography
              sx={{
                color: 'rgba(255,255,255,0.88)',
                mt: 0.75,
                fontSize: { xs: '0.95rem', md: '1.1rem' },
                textShadow: '0 1px 10px rgba(0,0,0,0.4)',
              }}
            >
              Resumen de auditorías y cumplimiento{user?.organizationName ? ` · ${user.organizationName}` : ''}
            </Typography>
          </Box>

          {isLoading && (
            <Box sx={{ display: 'grid', placeItems: 'center', py: 8 }}>
              <CircularProgress sx={{ color: 'common.white' }} aria-label="Cargando indicadores" />
            </Box>
          )}
          {!isLoading && error != null && <Alert severity="error">{extractErrorMessage(error)}</Alert>}
          {data && (
            <Box
              sx={{
                display: 'grid',
                gap: { xs: 1.5, md: 2.25 },
                gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', md: 'repeat(3, minmax(0, 1fr))' },
              }}
            >
              <KpiTile index={0} value={data.farmsCount} title="Explotaciones" icon={<AgricultureIcon />} to="/farms" />
              <KpiTile index={1} value={data.auditsTotal} title="Auditorías" icon={<FactCheckIcon />} to="/audits" />
              <KpiTile
                index={2}
                value={data.openFindings}
                title="No conformidades abiertas"
                icon={<AssignmentLateIcon />}
                tone="info"
              />
              <KpiTile
                index={3}
                value={data.overdueFindings}
                title="No conformidades vencidas"
                note={data.overdueFindings > 0 ? 'Requieren atención' : 'Todo en plazo'}
                icon={<ReportProblemIcon />}
                tone={data.overdueFindings > 0 ? 'alert' : 'ok'}
              />
              <KpiTile
                index={4}
                value={data.dueSoonFindings}
                title="Vencen en 7 días"
                note={data.dueSoonFindings > 0 ? 'Prioridad esta semana' : 'Sin urgencias'}
                icon={<EventBusyIcon />}
                tone={data.dueSoonFindings > 0 ? 'warn' : 'ok'}
              />
              <KpiTile
                index={5}
                value={data.documentsGenerated}
                title="Documentos generados"
                icon={<DescriptionIcon />}
                to="/documents"
              />
            </Box>
          )}
        </Box>

        {/* Pie de la ilustración, como en la pantalla de acceso */}
        <Typography
          variant="caption"
          sx={{
            position: 'absolute',
            left: { xs: 16, sm: 32, md: 48 },
            bottom: 18,
            zIndex: 1,
            display: { xs: 'none', md: 'block' },
            color: 'rgba(255,255,255,0.82)',
            textShadow: '0 1px 8px rgba(0,0,0,0.5)',
            opacity: 'calc(1 - var(--p) * 2)',
          }}
        >
          {scene.dayLabel.toUpperCase()} · {scene.title} — {scene.place}
        </Typography>

        {/* Flecha para pasar al detalle (clic o rueda del ratón) */}
        <Box
          sx={{
            position: 'absolute',
            left: 0,
            right: 0,
            bottom: 14,
            zIndex: 2,
            display: 'flex',
            justifyContent: 'center',
            opacity: 'calc(1 - var(--p) * 3)',
          }}
        >
          <Button
            onClick={goDetail}
            aria-label="Ver el detalle: próximos vencimientos y auditorías recientes"
            sx={{
              ...glass,
              flexDirection: 'column',
              gap: 0.25,
              minWidth: 0,
              px: 2.5,
              py: 0.75,
              borderRadius: 6,
              color: 'common.white',
              textTransform: 'none',
              fontWeight: 600,
              fontSize: '0.8rem',
              '&:hover': { bgcolor: 'rgba(255,255,255,0.24)' },
              '&:focus-visible': { outline: '3px solid #ffffff', outlineOffset: 2 },
            }}
          >
            Ver detalle
            <KeyboardArrowDownIcon
              sx={{
                fontSize: 30,
                animation: reduced ? 'none' : `${bounce} 1.8s ease-in-out infinite`,
              }}
            />
          </Button>
        </Box>
      </Box>

      {/* ---------------------------------------------------------------- Detalle */}
      <Box
        ref={detailRef}
        component="section"
        aria-label="Detalle"
        sx={{ px: { xs: 2, md: 3 }, pt: { xs: 3, md: 5 }, pb: { xs: 3, md: 4 }, minHeight: 'calc(100svh - 64px)' }}
      >
        <Stack
          direction="row"
          sx={{ justifyContent: 'space-between', alignItems: 'flex-end', mb: 2.5 }}
          style={revealStyle(revealed, 0, reduced)}
        >
          <Box>
            <Typography component="h2" variant="h4">
              Detalle
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Próximos vencimientos y auditorías recientes
            </Typography>
          </Box>
          <Button startIcon={<KeyboardArrowUpIcon />} onClick={goTop} size="small">
            Volver a los indicadores
          </Button>
        </Stack>

        {data && (
          <Stack spacing={3}>
            <Card style={revealStyle(revealed, 1, reduced)}>
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

            <Box
              sx={{
                display: 'grid',
                gap: 3,
                gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'repeat(2, minmax(0, 1fr))' },
              }}
            >
              <Card sx={{ height: '100%' }} style={revealStyle(revealed, 2, reduced)}>
                <CardContent>
                  <Typography variant="h6">Próximos vencimientos</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                    No conformidades abiertas, de la más urgente a la menos
                  </Typography>
                  {data.upcomingDeadlines.length === 0 ? (
                    <Stack direction="row" spacing={1} color="success.dark" sx={{ alignItems: 'center', py: 2 }}>
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
                          <Stack direction="row" spacing={2} sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                            <Box sx={{ minWidth: 0 }}>
                              <Typography noWrap sx={{ fontWeight: 600 }}>
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

              <Card sx={{ height: '100%' }} style={revealStyle(revealed, 3, reduced)}>
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
                        <Stack direction="row" spacing={2} sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                          <Box sx={{ minWidth: 0 }}>
                            <Typography noWrap sx={{ fontWeight: 600 }}>
                              {a.reference} · {a.farmName}
                            </Typography>
                            <Typography variant="body2" color="text.secondary" noWrap>
                              {standardShort(a.standardCode, a.standardName)} · {label(auditTypeLabels, a.auditType)}
                            </Typography>
                          </Box>
                          <Stack spacing={0.5} sx={{ alignItems: 'flex-end', minWidth: 110 }}>
                            <AuditStatusChip status={a.status} />
                            <Box sx={{ width: 130 }}>
                              <ProgressMeter compact value={a.progressPct} label="Progreso de evaluación" />
                            </Box>
                          </Stack>
                        </Stack>
                      </Box>
                    ))}
                    {data.recentAudits.length === 0 && (
                      <Typography variant="body2" color="text.secondary" sx={{ py: 1 }}>
                        Sin auditorías todavía.
                      </Typography>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Box>
          </Stack>
        )}
      </Box>
    </Box>
  );
}
