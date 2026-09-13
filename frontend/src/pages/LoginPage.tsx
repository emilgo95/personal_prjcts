import { useMemo, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { keyframes } from '@emotion/react';
import {
  Alert,
  Avatar,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import SpaIcon from '@mui/icons-material/Spa';
import EnergySavingsLeafIcon from '@mui/icons-material/EnergySavingsLeaf';
import VerifiedIcon from '@mui/icons-material/Verified';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import LoginIcon from '@mui/icons-material/Login';
import { useAuth } from '../auth/AuthContext';
import { extractErrorMessage } from '../api/client';
import { greetingForDate, sceneForDate } from '../auth/loginScenes';

const kenBurns = keyframes`
  from { transform: scale(1.02) translate3d(0, 0, 0); }
  to { transform: scale(1.1) translate3d(-1.5%, -1%, 0); }
`;

const glassChip = {
  color: 'common.white',
  bgcolor: 'rgba(15, 46, 43, 0.62)',
  border: '1px solid rgba(255, 255, 255, 0.28)',
  backdropFilter: 'blur(6px)',
  fontWeight: 600,
  '& .MuiChip-icon': { color: '#b9e28c' },
} as const;

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('admin@demo.test');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [imageReady, setImageReady] = useState(false);

  const now = useMemo(() => new Date(), []);
  const scene = sceneForDate(now);
  const greeting = greetingForDate(now);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email, password);
      navigate('/');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Box sx={{ position: 'relative', minHeight: '100vh', overflow: 'hidden', bgcolor: '#22402c' }}>
      {/* Ilustración del día (decorativa): cambia según el día de la semana */}
      <Box
        component="img"
        src={scene.image}
        alt=""
        aria-hidden
        data-testid="login-scene"
        onLoad={() => setImageReady(true)}
        sx={{
          position: 'absolute',
          inset: 0,
          width: '100%',
          height: '100%',
          objectFit: 'cover',
          opacity: imageReady ? 1 : 0,
          transition: 'opacity 900ms ease',
          animation: `${kenBurns} 45s ease-in-out infinite alternate`,
          '@media (prefers-reduced-motion: reduce)': { animation: 'none', transition: 'none' },
        }}
      />
      <Box
        aria-hidden
        sx={{
          position: 'absolute',
          inset: 0,
          background:
            'linear-gradient(90deg, rgba(12,38,30,0.62) 0%, rgba(12,38,30,0.18) 48%, rgba(12,38,30,0.38) 100%),' +
            'linear-gradient(0deg, rgba(8,24,18,0.45) 0%, rgba(8,24,18,0) 45%)',
        }}
      />

      <Box
        sx={{
          position: 'relative',
          zIndex: 1,
          minHeight: '100vh',
          display: 'grid',
          gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'minmax(0, 1fr) minmax(360px, 440px)' },
          alignItems: 'center',
          gap: { xs: 3, md: 6 },
          px: { xs: 2, sm: 4, md: 8 },
          py: 4,
        }}
      >
        <Box
          sx={{
            display: { xs: 'none', md: 'block' },
            color: 'common.white',
            maxWidth: 580,
            textShadow: '0 2px 14px rgba(0,0,0,0.45)',
          }}
        >
          <Chip icon={<SpaIcon />} label={`${scene.dayLabel} · ${scene.title} · ${scene.place}`} sx={glassChip} />
          <Typography component="p" variant="h3" fontWeight={800} mt={3} lineHeight={1.1}>
            Del campo al expediente de auditoría
          </Typography>
          <Typography component="p" variant="h6" fontWeight={400} mt={2} sx={{ opacity: 0.95 }}>
            Checklist, evidencias, no conformidades y documentos de cada auditoría GLOBALG.A.P., GRASP y SMETA,
            en un único lugar.
          </Typography>
          <Stack direction="row" spacing={1} mt={3} flexWrap="wrap" useFlexGap>
            <Chip icon={<EnergySavingsLeafIcon />} label="Agricultura sostenible" sx={glassChip} />
            <Chip icon={<VerifiedIcon />} label="Trazabilidad completa" sx={glassChip} />
            <Chip icon={<FactCheckIcon />} label="Listo para auditar" sx={glassChip} />
          </Stack>
        </Box>

        <Card
          elevation={0}
          sx={{
            width: '100%',
            maxWidth: 440,
            justifySelf: { xs: 'center', md: 'end' },
            borderRadius: 4,
            bgcolor: 'rgba(255, 255, 255, 0.9)',
            backdropFilter: 'blur(16px) saturate(140%)',
            border: '1px solid rgba(255, 255, 255, 0.7)',
            boxShadow: '0 24px 60px rgba(8, 30, 20, 0.35)',
          }}
        >
          <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
            <Stack direction="row" spacing={1.5} alignItems="center">
              <Avatar sx={{ bgcolor: 'primary.main', width: 52, height: 52 }}>
                <SpaIcon />
              </Avatar>
              <Box>
                <Typography component="h1" variant="h5" color="primary.dark" fontWeight={800}>
                  AgriAudit Docs
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Gestión de documentación de auditorías agrícolas internacionales
                </Typography>
              </Box>
            </Stack>

            <Typography variant="body1" fontWeight={600} mt={3}>
              {greeting}
            </Typography>
            <Typography variant="body2" color="text.secondary" mb={2.5}>
              Inicia sesión para continuar con tus auditorías.
            </Typography>

            <form onSubmit={onSubmit}>
              <Stack spacing={2}>
                {error && <Alert severity="error">{error}</Alert>}
                <TextField
                  label="Correo electrónico"
                  type="email"
                  autoComplete="username"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  autoFocus
                  fullWidth
                />
                <TextField
                  label="Contraseña"
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  fullWidth
                />
                <Button
                  type="submit"
                  variant="contained"
                  size="large"
                  disabled={busy}
                  startIcon={<LoginIcon />}
                  sx={{ py: 1.4, borderRadius: 2.5, fontWeight: 700 }}
                >
                  {busy ? 'Entrando…' : 'Iniciar sesión'}
                </Button>
              </Stack>
            </form>

            <Typography
              variant="caption"
              color="text.secondary"
              display={{ xs: 'flex', md: 'none' }}
              alignItems="center"
              gap={0.5}
              mt={3}
            >
              <SpaIcon sx={{ fontSize: 14, color: 'primary.main' }} />
              {scene.dayLabel} · {scene.title} · {scene.place}
            </Typography>
          </CardContent>
        </Card>
      </Box>
    </Box>
  );
}
