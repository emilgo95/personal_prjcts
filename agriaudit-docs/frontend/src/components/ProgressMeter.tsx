import { alpha, Box, LinearProgress, Stack, Typography, useTheme } from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';

export type ProgressLevel = 'low' | 'mid' | 'done';

/** Menos del 40 % se considera poco avance, de ahí a 99 % en curso y el 100 % completo. */
export function progressLevel(pct: number): ProgressLevel {
  if (pct >= 100) return 'done';
  if (pct >= 40) return 'mid';
  return 'low';
}

const levelStyle: Record<ProgressLevel, { color: 'error' | 'warning' | 'success'; text: string }> = {
  low: { color: 'error', text: 'Poco avance' },
  mid: { color: 'warning', text: 'En curso' },
  done: { color: 'success', text: 'Completa' },
};

/**
 * Barra de progreso con semáforo: rojo si se lleva poco, naranja si va por la mitad y verde si está completa.
 * El color nunca va solo: acompaña un texto («Poco avance», «En curso», «Completa») o, en la versión compacta, el
 * porcentaje y los puntos evaluados, y el estado se anuncia a los lectores de pantalla.
 */
export default function ProgressMeter({
  value,
  assessed,
  total,
  compact,
  label = 'Progreso de evaluación',
}: {
  value: number;
  assessed?: number;
  total?: number;
  /** Versión para tablas y listados: una barra fina con porcentaje, sin texto de estado. */
  compact?: boolean;
  label?: string;
}) {
  const theme = useTheme();
  const pct = Math.max(0, Math.min(100, Math.round(Number.isFinite(value) ? value : 0)));
  const level = progressLevel(pct);
  const { color, text } = levelStyle[level];
  const hasCounts = assessed != null && total != null;
  const counts = hasCounts ? `${assessed} de ${total} puntos evaluados` : null;
  const valueText = `${pct} %, ${text.toLowerCase()}${counts ? `, ${counts}` : ''}`;

  // Fondo sombreado con el color del estado: rojo si se lleva poco, naranja a medias y verde si está completa.
  const main = theme.palette[color].main;
  const tint = {
    bgcolor: alpha(main, 0.16),
    borderLeft: `4px solid ${main}`,
    borderRadius: 1,
  };

  const bar = (
    <LinearProgress
      variant="determinate"
      value={pct}
      color={color}
      aria-label={label}
      aria-valuetext={valueText}
      sx={{
        width: '100%',
        height: compact ? 8 : 10,
        borderRadius: 5,
        bgcolor: 'rgba(255, 255, 255, 0.85)',
        '& .MuiLinearProgress-bar': { borderRadius: 5 },
      }}
    />
  );

  if (compact) {
    return (
      <Box
        sx={{ ...tint, width: '100%', px: 1, py: 0.5, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}
        title={valueText}
      >
        {bar}
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'baseline', mt: 0.25 }}>
          <Typography variant="caption" sx={{ color: 'text.secondary', lineHeight: 1.2 }}>
            {hasCounts ? `${assessed}/${total} puntos` : ''}
          </Typography>
          <Typography variant="caption" sx={{ color: `${color}.dark`, fontWeight: 700, lineHeight: 1.2 }}>
            {pct}%
          </Typography>
        </Stack>
      </Box>
    );
  }

  return (
    <Box sx={{ ...tint, width: '100%', px: 1.5, py: 1 }}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'baseline', mb: 0.5 }}>
        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', color: `${color}.dark` }}>
          {level === 'done' && <CheckCircleIcon sx={{ fontSize: 16 }} />}
          <Typography variant="body2" sx={{ fontWeight: 700 }}>
            {text}
          </Typography>
        </Stack>
        <Typography variant="body2" sx={{ fontWeight: 700 }}>
          {pct}%
        </Typography>
      </Stack>
      {bar}
      {counts && (
        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
          {counts}
        </Typography>
      )}
    </Box>
  );
}
