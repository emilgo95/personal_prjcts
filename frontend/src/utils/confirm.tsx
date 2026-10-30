import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from 'react';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import HelpOutlineIcon from '@mui/icons-material/HelpOutlineOutlined';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';

/** Gravedad de la acción: decide el color del botón y el icono. */
type Tone = 'default' | 'warning' | 'danger';

export interface ConfirmOptions {
  title: string;
  message?: ReactNode;
  /** Texto del botón que confirma (p. ej. «Bloquear datos»). */
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: Tone;
}

interface ConfirmContextValue {
  /** Abre el cuadro de confirmación y resuelve true si se confirma, false si se cancela o se cierra. */
  confirm: (options: ConfirmOptions) => Promise<boolean>;
}

const ConfirmContext = createContext<ConfirmContextValue | undefined>(undefined);

const toneStyle: Record<Tone, { color: 'primary' | 'warning' | 'error'; icon: ReactNode }> = {
  default: { color: 'primary', icon: <HelpOutlineIcon /> },
  warning: { color: 'warning', icon: <WarningAmberIcon /> },
  danger: { color: 'error', icon: <DeleteOutlineIcon /> },
};

/** Cuadro de confirmación de la aplicación, en lugar del aviso del navegador (window.confirm). */
export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [options, setOptions] = useState<ConfirmOptions | null>(null);
  const resolver = useRef<((ok: boolean) => void) | null>(null);

  const confirm = useCallback((next: ConfirmOptions) => {
    resolver.current?.(false);
    setOptions(next);
    return new Promise<boolean>((resolve) => {
      resolver.current = resolve;
    });
  }, []);

  const close = (ok: boolean) => {
    resolver.current?.(ok);
    resolver.current = null;
    setOptions(null);
  };

  const value = useMemo(() => ({ confirm }), [confirm]);
  const tone = toneStyle[options?.tone ?? 'default'];

  return (
    <ConfirmContext.Provider value={value}>
      {children}
      <Dialog
        open={options !== null}
        onClose={() => close(false)}
        maxWidth="xs"
        fullWidth
        aria-labelledby="confirm-dialog-title"
        slotProps={{ paper: { sx: { borderRadius: 3 } } }}
      >
        {options && (
          <>
            <DialogTitle id="confirm-dialog-title" sx={{ pb: 1 }}>
              <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                <Box
                  aria-hidden
                  sx={{
                    width: 40,
                    height: 40,
                    flexShrink: 0,
                    borderRadius: '12px',
                    display: 'grid',
                    placeItems: 'center',
                    color: `${tone.color}.main`,
                    bgcolor: (t) => alpha(t.palette[tone.color].main, 0.12),
                  }}
                >
                  {tone.icon}
                </Box>
                <Typography component="span" variant="h6" sx={{ lineHeight: 1.3 }}>
                  {options.title}
                </Typography>
              </Stack>
            </DialogTitle>
            {options.message && (
              <DialogContent sx={{ color: 'text.secondary', typography: 'body2' }}>{options.message}</DialogContent>
            )}
            <DialogActions sx={{ px: 3, pb: 2.5, pt: options.message ? 0 : 1 }}>
              <Button onClick={() => close(false)} autoFocus>
                {options.cancelLabel ?? 'Cancelar'}
              </Button>
              <Button variant="contained" color={tone.color} onClick={() => close(true)}>
                {options.confirmLabel ?? 'Aceptar'}
              </Button>
            </DialogActions>
          </>
        )}
      </Dialog>
    </ConfirmContext.Provider>
  );
}

export function useConfirm() {
  const ctx = useContext(ConfirmContext);
  if (!ctx) throw new Error('useConfirm debe usarse dentro de ConfirmProvider');
  return ctx.confirm;
}
