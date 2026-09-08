import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import { AnalysisApi } from '../api/endpoints';
import type { ComplianceValue, EvidenceAnalysis, SuggestedFinding } from '../api/types';
import { extractErrorMessage } from '../api/client';
import { classificationLabels, complianceLabels } from '../utils/labels';

interface Props {
  open: boolean;
  onClose: () => void;
  auditId: number;
  attachmentId: number;
  fileName: string;
  controlPointId?: number;
  /** Aplica la nota de evidencia confirmada por el usuario. */
  onApplyNote?: (text: string) => void | Promise<unknown>;
  /** Aplica el resultado de cumplimiento confirmado. */
  onApplyCompliance?: (value: ComplianceValue) => void | Promise<unknown>;
  /** Crea una no conformidad a partir del borrador confirmado. */
  onCreateFinding?: (draft: SuggestedFinding) => void | Promise<unknown>;
}

export default function EvidenceAnalysisDialog({
  open,
  onClose,
  auditId,
  attachmentId,
  fileName,
  controlPointId,
  onApplyNote,
  onApplyCompliance,
  onCreateFinding,
}: Props) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<EvidenceAnalysis | null>(null);
  const [note, setNote] = useState('');
  const [applied, setApplied] = useState<string[]>([]);

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    setError(null);
    setResult(null);
    setApplied([]);
    AnalysisApi.analyzeEvidence(auditId, attachmentId, controlPointId)
      .then((r) => {
        setResult(r);
        setNote(r.suggestedEvidenceNote || r.extractedText || '');
      })
      .catch((e) => setError(extractErrorMessage(e)))
      .finally(() => setLoading(false));
  }, [open, auditId, attachmentId, controlPointId]);

  const mark = (key: string) => setApplied((a) => [...a, key]);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        <Stack direction="row" spacing={1} alignItems="center">
          <AutoAwesomeIcon color="primary" />
          <span>Reconocimiento de la evidencia «{fileName}»</span>
        </Stack>
        <Typography variant="body2" color="text.secondary">
          Revisa y confirma cada propuesta antes de guardar. Nada se aplica automáticamente.
        </Typography>
      </DialogTitle>

      <DialogContent dividers>
        {loading && (
          <Box sx={{ display: 'grid', placeItems: 'center', py: 5 }}>
            <CircularProgress />
            <Typography variant="body2" color="text.secondary" mt={2}>
              Analizando el documento…
            </Typography>
          </Box>
        )}
        {error && <Alert severity="error">{error}</Alert>}

        {result && (
          <Stack spacing={2}>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip size="small" label={`Motor: ${result.engine}`} />
              <Chip size="small" label={`${result.pages} pág.`} />
              <Chip
                size="small"
                color={result.meanConfidence >= 0.7 ? 'success' : result.meanConfidence >= 0.5 ? 'warning' : 'error'}
                label={`Confianza OCR ${(result.meanConfidence * 100).toFixed(0)}%`}
              />
            </Stack>

            {result.warnings.map((w, i) => (
              <Alert key={i} severity="warning" variant="outlined">
                {w}
              </Alert>
            ))}

            <Box>
              <Typography variant="subtitle2" gutterBottom>
                Texto reconocido
              </Typography>
              <TextField
                value={result.extractedText}
                multiline
                minRows={4}
                maxRows={10}
                fullWidth
                InputProps={{ readOnly: true, sx: { fontFamily: 'monospace', fontSize: 12 } }}
              />
            </Box>

            <Divider />

            <Box>
              <Typography variant="subtitle2" gutterBottom>
                Nota de evidencia sugerida (editable)
              </Typography>
              <TextField value={note} onChange={(e) => setNote(e.target.value)} multiline minRows={3} fullWidth />
              {onApplyNote && (
                <Button
                  size="small"
                  variant="contained"
                  sx={{ mt: 1 }}
                  disabled={!note.trim() || applied.includes('note')}
                  onClick={async () => {
                    await onApplyNote(note);
                    mark('note');
                  }}
                >
                  {applied.includes('note') ? 'Nota aplicada ✓' : 'Aplicar esta nota'}
                </Button>
              )}
            </Box>

            {result.suggestedCompliance && onApplyCompliance && (
              <>
                <Divider />
                <Box>
                  <Typography variant="subtitle2" gutterBottom>
                    Resultado de cumplimiento sugerido
                  </Typography>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Chip
                      color={
                        result.suggestedCompliance === 'COMPLIANT'
                          ? 'success'
                          : result.suggestedCompliance === 'NON_COMPLIANT'
                            ? 'error'
                            : 'default'
                      }
                      label={complianceLabels[result.suggestedCompliance] ?? result.suggestedCompliance}
                    />
                    <Button
                      size="small"
                      variant="contained"
                      disabled={applied.includes('compliance')}
                      onClick={async () => {
                        await onApplyCompliance(result.suggestedCompliance as ComplianceValue);
                        mark('compliance');
                      }}
                    >
                      {applied.includes('compliance') ? 'Aplicado ✓' : 'Aplicar cumplimiento'}
                    </Button>
                  </Stack>
                  {result.complianceRationale && (
                    <Typography variant="caption" color="text.secondary">
                      {result.complianceRationale}
                    </Typography>
                  )}
                </Box>
              </>
            )}

            {result.suggestedFindings.length > 0 && onCreateFinding && (
              <>
                <Divider />
                <Box>
                  <Typography variant="subtitle2" gutterBottom>
                    Posibles no conformidades
                  </Typography>
                  <Stack spacing={1}>
                    {result.suggestedFindings.map((f, i) => (
                      <FindingDraft
                        key={i}
                        draft={f}
                        applied={applied.includes('finding-' + i)}
                        onCreate={async (edited) => {
                          await onCreateFinding(edited);
                          mark('finding-' + i);
                        }}
                      />
                    ))}
                  </Stack>
                </Box>
              </>
            )}
          </Stack>
        )}
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose}>Cerrar</Button>
      </DialogActions>
    </Dialog>
  );
}

function FindingDraft({
  draft,
  applied,
  onCreate,
}: {
  draft: SuggestedFinding;
  applied: boolean;
  onCreate: (edited: SuggestedFinding) => Promise<void>;
}) {
  const [edited, setEdited] = useState<SuggestedFinding>(draft);
  return (
    <Box sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 1.5 }}>
      <Stack spacing={1}>
        <TextField
          label="Título"
          size="small"
          fullWidth
          value={edited.title}
          onChange={(e) => setEdited((d) => ({ ...d, title: e.target.value }))}
        />
        <TextField
          label="Descripción"
          size="small"
          fullWidth
          multiline
          minRows={2}
          value={edited.description}
          onChange={(e) => setEdited((d) => ({ ...d, description: e.target.value }))}
        />
        <Stack direction="row" spacing={1}>
          <TextField
            select
            size="small"
            label="Clasificación"
            sx={{ minWidth: 160 }}
            value={edited.classification}
            onChange={(e) => setEdited((d) => ({ ...d, classification: e.target.value }))}
          >
            {Object.entries(classificationLabels).map(([k, v]) => (
              <MenuItem key={k} value={k}>
                {v}
              </MenuItem>
            ))}
          </TextField>
          <Button
            variant="contained"
            size="small"
            disabled={applied}
            onClick={() => onCreate(edited)}
          >
            {applied ? 'Creada ✓' : 'Crear no conformidad'}
          </Button>
        </Stack>
      </Stack>
    </Box>
  );
}
