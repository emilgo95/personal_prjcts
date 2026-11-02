import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import type { SubmissionCheck } from '../api/types';
import { formatDate } from '../utils/dates';

const MAX_JUSTIFICATION = 2000;

/**
 * Lo que falta para enviar la auditoría. Con NC por revisar no se puede enviar: se listan para verificarlas. Si
 * solo quedan NC vencidas, se pide el motivo del retraso de cada una, que saldrá en los documentos.
 */
export default function SubmitAuditDialog({
  check,
  submitting,
  onClose,
  onSubmit,
}: {
  check: SubmissionCheck | null;
  submitting: boolean;
  onClose: () => void;
  onSubmit: (lateJustifications: Record<number, string>) => void;
}) {
  const [texts, setTexts] = useState<Record<number, string>>({});

  useEffect(() => {
    if (!check) return;
    setTexts(Object.fromEntries(check.overdue.map((f) => [f.id, f.lateJustification ?? ''])));
  }, [check]);

  const blocked = (check?.toReview.length ?? 0) > 0;
  const complete = check?.overdue.every((f) => (texts[f.id] ?? '').trim() !== '') ?? false;

  return (
    <Dialog open={check != null} onClose={onClose} maxWidth="sm" fullWidth slotProps={{ paper: { sx: { borderRadius: 3 } } }}>
      {check && (
        <>
          <DialogTitle>
            {blocked ? 'Todavía no se puede enviar la auditoría' : 'Justifica las no conformidades vencidas'}
          </DialogTitle>
          <DialogContent dividers>
            {blocked ? (
              <Stack spacing={1.5}>
                <Typography variant="body2">
                  {check.toReview.length === 1 ? 'Esta no conformidad tiene' : 'Estas no conformidades tienen'} la acción
                  correctiva completada, pero falta verificarla. Verifícala en <b>Hallazgos y CAPA</b> y vuelve a enviar.
                </Typography>
                <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
                  {check.toReview.map((f) => (
                    <li key={f.id}>
                      <Typography variant="body2">
                        <b>{f.reference}</b> · {f.title || 'Sin título'}
                      </Typography>
                    </li>
                  ))}
                </Box>
                {check.overdue.length > 0 && (
                  <Typography variant="body2" color="text.secondary">
                    Además, {check.overdue.length === 1 ? 'hay 1 no conformidad vencida' : `hay ${check.overdue.length} no conformidades vencidas`}:
                    al enviar te pediremos el motivo del retraso.
                  </Typography>
                )}
              </Stack>
            ) : (
              <Stack spacing={2}>
                <Alert severity="warning">
                  Se puede enviar con no conformidades fuera de plazo, pero hay que explicar por qué. El motivo saldrá en el
                  informe y en el plan de acciones correctivas, y quedará en el historial.
                </Alert>
                {check.overdue.map((f) => (
                  <Box key={f.id}>
                    <Typography variant="body2" sx={{ mb: 0.75 }}>
                      <b>{f.reference}</b> · {f.title || 'Sin título'} ·{' '}
                      <Box component="span" sx={{ color: 'error.main', fontWeight: 600 }}>
                        venció el {formatDate(f.dueDate)}
                      </Box>
                    </Typography>
                    <TextField
                      label={`Motivo del retraso de ${f.reference}`}
                      placeholder="P. ej.: pendiente del análisis del laboratorio; plan comunicado al organismo el 30/10"
                      multiline
                      minRows={2}
                      fullWidth
                      required
                      value={texts[f.id] ?? ''}
                      onChange={(e) => setTexts((t) => ({ ...t, [f.id]: e.target.value }))}
                      slotProps={{ htmlInput: { maxLength: MAX_JUSTIFICATION } }}
                    />
                  </Box>
                ))}
              </Stack>
            )}
          </DialogContent>
          <DialogActions sx={{ px: 3, py: 2 }}>
            <Button onClick={onClose}>{blocked ? 'Cerrar' : 'Cancelar'}</Button>
            {!blocked && (
              <Button
                variant="contained"
                disabled={!complete || submitting}
                onClick={() =>
                  onSubmit(Object.fromEntries(check.overdue.map((f) => [f.id, (texts[f.id] ?? '').trim()])))
                }
              >
                Enviar la auditoría
              </Button>
            )}
          </DialogActions>
        </>
      )}
    </Dialog>
  );
}
