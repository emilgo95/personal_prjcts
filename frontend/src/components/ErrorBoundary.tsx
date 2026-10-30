import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Alert, AlertTitle, Box, Button, Stack } from '@mui/material';

interface Props {
  children: ReactNode;
  /** Al cambiar (p. ej. la ruta), se descarta el error y se vuelve a intentar pintar el contenido. */
  resetKey?: string;
  /** Ocupa toda la ventana: para errores fuera del menú lateral. */
  fullPage?: boolean;
}

interface State {
  failed: boolean;
}

/** Muestra un aviso con salida en lugar de dejar la pantalla en blanco si una página falla al pintarse. */
export default class ErrorBoundary extends Component<Props, State> {
  state: State = { failed: false };

  static getDerivedStateFromError(): State {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Error al mostrar la página', error, info.componentStack);
  }

  componentDidUpdate(prev: Props) {
    if (this.state.failed && prev.resetKey !== this.props.resetKey) {
      this.setState({ failed: false });
    }
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <Box sx={this.props.fullPage ? { display: 'grid', placeItems: 'center', minHeight: '100vh', p: 2 } : { py: 4 }}>
        <Alert severity="error" sx={{ maxWidth: 560 }}>
          <AlertTitle>Algo ha fallado al mostrar esta pantalla</AlertTitle>
          Tus datos no se han perdido. Vuelve a cargar la página o ve al panel; si se repite, avisa al administrador.
          <Stack direction="row" spacing={1} sx={{ mt: 2 }}>
            <Button variant="contained" size="small" onClick={() => window.location.reload()}>
              Volver a cargar
            </Button>
            <Button size="small" onClick={() => window.location.assign('/')}>
              Ir al panel
            </Button>
          </Stack>
        </Alert>
      </Box>
    );
  }
}
