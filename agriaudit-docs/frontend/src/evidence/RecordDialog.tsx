import { useEffect, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { EvidenceApi } from '../api/endpoints';
import type { EvidenceOutcome, EvidenceRecord, EvidenceRecordRequest, EvidenceType, Farm } from '../api/types';
import { extractErrorMessage } from '../api/client';
import { useToast } from '../utils/toast';
import { evidenceOutcomeLabels } from '../utils/labels';

export interface RecordPreset {
  farmId?: number;
  typeCode?: string;
}

const today = () => new Date().toISOString().slice(0, 10);

function emptyForm(preset?: RecordPreset): EvidenceRecordRequest {
  return {
    farmId: preset?.farmId ?? 0,
    typeCode: preset?.typeCode ?? '',
    title: '',
    issuedOn: today(),
    validUntil: null,
    outcome: 'UNRATED',
    notes: null,
    containsPersonalData: false,
  };
}

/** Alta o edición de un registro de evidencia reutilizable. */
export default function RecordDialog({
  open,
  onClose,
  farms,
  types,
  record,
  preset,
  onSaved,
}: {
  open: boolean;
  onClose: () => void;
  farms: Farm[];
  types: EvidenceType[];
  /** Si se indica, se edita; si no, se crea. */
  record?: EvidenceRecord | null;
  preset?: RecordPreset;
  onSaved: () => void;
}) {
  const { notify } = useToast();
  const [form, setForm] = useState<EvidenceRecordRequest>(emptyForm(preset));
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setError(null);
    setForm(
      record
        ? {
            farmId: record.farmId,
            typeCode: record.typeCode,
            title: record.title,
            issuedOn: record.issuedOn,
            validUntil: record.validUntil,
            outcome: record.outcome,
            notes: record.notes,
            containsPersonalData: record.containsPersonalData,
          }
        : emptyForm(preset),
    );
  }, [open, record, preset]);

  const selectedType = types.find((t) => t.code === form.typeCode);
  const lockedByUse = !!record && record.usedIn > 0;

  const save = useMutation({
    mutationFn: () => (record ? EvidenceApi.update(record.id, form) : EvidenceApi.create(form)),
    onSuccess: (change) => {
      notify(
        change.affectedAudits > 0
          ? `Evidencia guardada. El cambio afecta a ${change.affectedAudits} auditoría(s) donde se usa.`
          : 'Evidencia guardada',
        'success',
      );
      onSaved();
      onClose();
    },
    onError: (e) => setError(extractErrorMessage(e)),
  });

  const valid = form.farmId > 0 && form.typeCode !== '' && form.title.trim() !== '' && form.issuedOn !== '';

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{record ? 'Editar evidencia' : 'Nueva evidencia'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          {lockedByUse && (
            <Alert severity="info">
              Este registro se usa en {record?.usedIn} punto(s) de auditorías: la explotación y el tipo no se pueden cambiar, pero
              sí sus fechas y su resultado (se reflejarán en todas).
            </Alert>
          )}
          <TextField
            select
            required
            label="Explotación"
            value={form.farmId || ''}
            disabled={lockedByUse}
            onChange={(e) => setForm((f) => ({ ...f, farmId: Number(e.target.value) }))}
          >
            {farms.map((f) => (
              <MenuItem key={f.id} value={f.id}>
                {f.legalName}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            required
            label="Tipo de evidencia"
            value={form.typeCode}
            disabled={lockedByUse}
            onChange={(e) => setForm((f) => ({ ...f, typeCode: e.target.value }))}
            helperText={selectedType?.description ?? ' '}
          >
            {types.map((t) => (
              <MenuItem key={t.code} value={t.code}>
                {t.name}
              </MenuItem>
            ))}
          </TextField>
          {selectedType?.personalDataNotice && <Alert severity="warning">{selectedType.personalDataNotice}</Alert>}
          <TextField
            required
            label="Título (p. ej. «Análisis del agua de riego, pozo 2»)"
            value={form.title}
            slotProps={{ htmlInput: { maxLength: 200 } }}
            onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
          />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField
              required
              fullWidth
              type="date"
              label="Fecha del documento"
              value={form.issuedOn}
              onChange={(e) => setForm((f) => ({ ...f, issuedOn: e.target.value }))}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              fullWidth
              type="date"
              label="Válido hasta"
              value={form.validUntil ?? ''}
              helperText="Vacío = sin caducidad"
              onChange={(e) => setForm((f) => ({ ...f, validUntil: e.target.value || null }))}
              slotProps={{ inputLabel: { shrink: true } }}
            />
          </Stack>
          <TextField
            select
            label="Resultado"
            value={form.outcome}
            onChange={(e) => setForm((f) => ({ ...f, outcome: e.target.value as EvidenceOutcome }))}
            helperText="Un registro desfavorable o vencido no puede cubrir ningún punto de una auditoría"
          >
            {Object.entries(evidenceOutcomeLabels).map(([value, text]) => (
              <MenuItem key={value} value={value}>
                {text}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            multiline
            minRows={2}
            label="Notas"
            value={form.notes ?? ''}
            slotProps={{ htmlInput: { maxLength: 4000 } }}
            onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value || null }))}
          />
          <FormControlLabel
            control={
              <Checkbox
                checked={form.containsPersonalData}
                onChange={(e) => setForm((f) => ({ ...f, containsPersonalData: e.target.checked }))}
              />
            }
            label={<Typography variant="body2">El documento contiene datos personales (nombres, DNI…)</Typography>}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancelar</Button>
        <Button variant="contained" disabled={!valid || save.isPending} onClick={() => save.mutate()}>
          Guardar
        </Button>
      </DialogActions>
    </Dialog>
  );
}
