import { useMemo, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { keyframes } from '@emotion/react';
import { Alert, Avatar, Box, Button, Card, CardContent, Stack, TextField, Typography } from '@mui/material';
import SpaIcon from '@mui/icons-material/Spa';
import LoginIcon from '@mui/icons-material/Login';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import TaskAltOutlinedIcon from '@mui/icons-material/TaskAltOutlined';
import VerifiedOutlinedIcon from '@mui/icons-material/VerifiedOutlined';
import { useAuth } from '../auth/AuthContext';
import { extractErrorMessage } from '../api/client';
import { greetingForDate, sceneForDate } from '../auth/loginScenes';

/** Tres ideas clave bajo la propuesta de valor: breves, paralelas y centradas en el usuario. */
const KEY_POINTS = [
  { icon: DescriptionOutlinedIcon, label: 'Informes en un clic' },
  { icon: TaskAltOutlinedIcon, label: 'No conformidades al día' },
  { icon: VerifiedOutlinedIcon, label: 'Trazabilidad completa' },
] as const;

const kenBurns = keyframes`
  from { transform: scale(1.02) translate3d(0, 0, 0); }
  to { transform: scale(1.1) translate3d(-1.5%, -1%, 0); }
`;

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState(import.meta.env.VITE_DEMO_PREFILL === 'true' ? 'ana.auditora@demo.test' : '');
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
          pt: 4,
          pb: { xs: 12, md: 4 },
        }}
      >
        {/* Propuesta de valor: una sola frase, centrada en el beneficio para el cliente */}
        <Box sx={{ display: { xs: 'none', md: 'block' } }}>
          <Typography
            component="p"
            sx={{
              color: 'common.white',
              maxWidth: 620,
              fontSize: { md: '3.4rem', lg: '4.2rem' },
              fontWeight: 700,
              lineHeight: 1.05,
              letterSpacing: '-0.025em',
              textShadow: '0 2px 24px rgba(0,0,0,0.35)',
              textWrap: 'balance',
            }}
          >
            Tus auditorías, listas sin papeleo.
          </Typography>

          <Box
            component="ul"
            sx={{
              listStyle: 'none',
              p: 0,
              m: 0,
              mt: 4,
              display: 'flex',
              flexDirection: { md: 'column', lg: 'row' },
              columnGap: 3,
              rowGap: 1.75,
            }}
          >
            {KEY_POINTS.map(({ icon: Icon, label }) => (
              <Box component="li" key={label} sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}>
                <Box
                  aria-hidden
                  sx={{
                    width: 38,
                    height: 38,
                    borderRadius: '12px',
                    display: 'grid',
                    placeItems: 'center',
                    color: '#cdeea3',
                    bgcolor: 'rgba(255, 255, 255, 0.14)',
                    border: '1px solid rgba(255, 255, 255, 0.24)',
                    backdropFilter: 'blur(8px)',
                    boxShadow: 'inset 0 1px 0 rgba(255, 255, 255, 0.18)',
                  }}
                >
                  <Icon sx={{ fontSize: 20 }} />
                </Box>
                <Typography
                  component="span"
                  sx={{
                    color: 'common.white',
                    fontSize: '0.98rem',
                    fontWeight: 600,
                    letterSpacing: '0.005em',
                    textShadow: '0 1px 10px rgba(0,0,0,0.45)',
                  }}
                >
                  {label}
                </Typography>
              </Box>
            ))}
          </Box>
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
            <Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
              <Avatar sx={{ bgcolor: 'primary.main', width: 52, height: 52 }}>
                <SpaIcon />
              </Avatar>
              <Box>
                <Typography component="h1" variant="h5" color="primary.dark" sx={{ fontWeight: 800 }}>
                  AgriAudit Docs
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Gestión de documentación de auditorías agrícolas internacionales
                </Typography>
              </Box>
            </Stack>

            <Typography variant="body1" sx={{ fontWeight: 600, mt: 3 }}>
              {greeting}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2.5 }}>
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
          </CardContent>
        </Card>
      </Box>

      {/* Pie de la ilustración del día, como en una fotografía editorial */}
      <Box
        data-testid="login-scene-caption"
        sx={{
          position: 'absolute',
          zIndex: 1,
          left: { xs: 20, md: 64 },
          bottom: { xs: 24, md: 40 },
          display: 'flex',
          alignItems: 'center',
          gap: 2,
          color: 'common.white',
          textShadow: '0 1px 10px rgba(0,0,0,0.55)',
        }}
      >
        <Box aria-hidden sx={{ width: 36, height: '1px', bgcolor: 'rgba(255,255,255,0.75)' }} />
        <Box>
          <Typography
            component="p"
            sx={{ fontSize: '0.68rem', fontWeight: 600, letterSpacing: '0.24em', textTransform: 'uppercase', opacity: 0.85 }}
          >
            {scene.dayLabel}
          </Typography>
          <Typography component="p" sx={{ fontSize: '0.9rem', fontWeight: 500, letterSpacing: '0.01em' }}>
            {scene.title}
            <Box component="span" sx={{ opacity: 0.75, fontWeight: 400 }}>
              {' — '}
              {scene.place}
            </Box>
          </Typography>
        </Box>
      </Box>
    </Box>
  );
}
