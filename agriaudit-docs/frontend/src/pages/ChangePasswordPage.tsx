import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  IconButton,
  InputAdornment,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import KeyIcon from '@mui/icons-material/Key';
import { useAuth } from '../auth/AuthContext';
import { extractErrorMessage } from '../api/client';
import { useToast } from '../utils/toast';
import PageHeader from '../components/PageHeader';

const MIN_LENGTH = 8;
/** BCrypt solo usa los primeros 72 bytes: el servidor rechaza las más largas. */
const MAX_BYTES = 72;

/** Motivo por el que la contraseña nueva no vale todavía, o null si vale. */
export function newPasswordProblem(current: string, next: string, repeat: string): string | null {
  if (next.length < MIN_LENGTH) return `Al menos ${MIN_LENGTH} caracteres`;
  if (new TextEncoder().encode(next).length > MAX_BYTES) return `Como máximo ${MAX_BYTES} caracteres`;
  if (next.trim() === '') return 'No puede ser solo espacios';
  if (next === current) return 'Debe ser distinta de la actual';
  if (repeat !== next) return 'Las dos contraseñas nuevas no coinciden';
  return null;
}

/**
 * Cambio de la contraseña propia. Con `forced`, es la única pantalla que ve quien entra con una contraseña temporal
 * (puesta por un administrador) hasta que la cambia.
 */
export default function ChangePasswordPage({ forced = false }: { forced?: boolean }) {
  const { user, changePassword, logout } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [repeat, setRepeat] = useState('');
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const problem = newPasswordProblem(current, next, repeat);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (problem || !current) return;
    setBusy(true);
    setError(null);
    try {
      await changePassword(current, next);
      notify(
        forced ? 'Contraseña cambiada. Ya puedes usar la aplicación.' : 'Contraseña cambiada. Se han cerrado tus otras sesiones.',
        'success',
      );
      if (!forced) navigate('/');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const visibility = (
    <InputAdornment position="end">
      <IconButton
        size="small"
        aria-label={show ? 'Ocultar las contraseñas' : 'Mostrar las contraseñas'}
        onClick={() => setShow((s) => !s)}
        edge="end"
      >
        {show ? <VisibilityOffIcon fontSize="small" /> : <VisibilityIcon fontSize="small" />}
      </IconButton>
    </InputAdornment>
  );

  const form = (
    <Box component="form" onSubmit={onSubmit} noValidate>
      <Stack spacing={2}>
        <TextField
          label={forced ? 'Contraseña temporal' : 'Contraseña actual'}
          type={show ? 'text' : 'password'}
          autoComplete="current-password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          required
          fullWidth
          autoFocus
          slotProps={{ input: { endAdornment: visibility } }}
        />
        <TextField
          label="Contraseña nueva"
          type={show ? 'text' : 'password'}
          autoComplete="new-password"
          value={next}
          onChange={(e) => setNext(e.target.value)}
          required
          fullWidth
          helperText={`Al menos ${MIN_LENGTH} caracteres y distinta de la ${forced ? 'temporal' : 'actual'}. Mejor una frase que solo tú recuerdes.`}
        />
        <TextField
          label="Repite la contraseña nueva"
          type={show ? 'text' : 'password'}
          autoComplete="new-password"
          value={repeat}
          onChange={(e) => setRepeat(e.target.value)}
          required
          fullWidth
          error={repeat !== '' && repeat !== next}
          helperText={repeat !== '' && repeat !== next ? 'No coincide con la contraseña nueva' : ' '}
        />
        {error && <Alert severity="error">{error}</Alert>}
        {next !== '' && problem && problem !== 'Las dos contraseñas nuevas no coinciden' && (
          <Typography variant="body2" color="warning.dark" role="status">
            {problem}
          </Typography>
        )}
        <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
          {forced ? (
            <Button onClick={logout}>Salir</Button>
          ) : (
            <Button onClick={() => navigate(-1)}>Cancelar</Button>
          )}
          <Button type="submit" variant="contained" disabled={busy || !current || problem !== null}>
            Cambiar contraseña
          </Button>
        </Stack>
      </Stack>
    </Box>
  );

  if (!forced) {
    return (
      <Box sx={{ maxWidth: 560 }}>
        <PageHeader title="Cambiar contraseña" subtitle="Al cambiarla se cierran tus sesiones abiertas en otros equipos" />
        <Card>
          <CardContent>{form}</CardContent>
        </Card>
      </Box>
    );
  }

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2, bgcolor: 'background.default' }}>
      <Card sx={{ width: '100%', maxWidth: 480 }}>
        <CardContent sx={{ p: { xs: 2.5, sm: 4 } }}>
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 2 }}>
            <Box
              aria-hidden
              sx={{
                width: 44,
                height: 44,
                borderRadius: '12px',
                display: 'grid',
                placeItems: 'center',
                color: 'primary.main',
                bgcolor: 'rgba(15, 107, 95, 0.1)',
              }}
            >
              <KeyIcon />
            </Box>
            <Box>
              <Typography component="h1" variant="h5">
                Crea tu contraseña
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {user?.fullName || user?.email}
                {user?.organizationName ? ` · ${user.organizationName}` : ''}
              </Typography>
            </Box>
          </Stack>
          <Alert severity="info" sx={{ mb: 3 }}>
            Has entrado con una contraseña temporal que te ha dado el administrador. Antes de empezar, cámbiala por una
            que solo conozcas tú.
          </Alert>
          {form}
        </CardContent>
      </Card>
    </Box>
  );
}
