import { useState } from 'react';
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
  Grid,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { CatalogApi } from '../api/endpoints';
import type { ImportPreview } from '../api/types';
import { extractErrorMessage } from '../api/client';
import { levelLabels, label } from '../utils/labels';
import { isWebLink } from '../utils/links';

const scoringTypes = [
  { value: 'MAJOR_MINOR', label: 'Obligaciones mayores y menores (porcentaje mínimo de cada una)' },
  { value: 'LEVEL_COMPLETION', label: 'Nivel de cumplimiento (informe, sin aprobado/suspenso)' },
  { value: 'PILLAR', label: 'Por pilares (informe)' },
  { value: 'REPORT_ONLY', label: 'Solo informe' },
];

const categories = [
  { value: 'GAP', label: 'Buenas prácticas agrícolas' },
  { value: 'FOOD_SAFETY', label: 'Seguridad alimentaria' },
  { value: 'SOCIAL', label: 'Social / laboral' },
  { value: 'SUSTAINABILITY', label: 'Sostenibilidad' },
  { value: 'ORGANIC', label: 'Ecológico' },
];

const empty = {
  code: '',
  name: '',
  version: '',
  ownerBody: '',
  category: 'GAP',
  description: '',
  officialUrl: '',
  scoringType: 'MAJOR_MINOR',
  majorMustMinPct: '100',
  minorMustMinPct: '95',
};

/** Importa el listado de puntos de control de un estándar propio: datos, fichero, validación y alta. */
export default function StandardImportDialog({
  open,
  onClose,
  onImported,
}: {
  open: boolean;
  onClose: () => void;
  onImported: (code: string) => void;
}) {
  const [form, setForm] = useState(empty);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const set = (key: keyof typeof empty) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((f) => ({ ...f, [key]: e.target.value }));
    setPreview(null);
  };

  const body = () => {
    const data = new FormData();
    data.append('file', file as File);
    Object.entries(form).forEach(([k, v]) => {
      if (v !== '' && !(form.scoringType !== 'MAJOR_MINOR' && (k === 'majorMustMinPct' || k === 'minorMustMinPct'))) {
        data.append(k, v);
      }
    });
    return data;
  };

  const validate = async () => {
    setBusy(true);
    setError(null);
    try {
      setPreview(await CatalogApi.previewImport(body()));
    } catch (e) {
      setError(extractErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const doImport = async () => {
    setBusy(true);
    setError(null);
    try {
      const created = await CatalogApi.importStandard(body());
      setForm(empty);
      setFile(null);
      setPreview(null);
      onImported(created.code);
    } catch (e) {
      setError(extractErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const close = () => {
    setPreview(null);
    setError(null);
    onClose();
  };

  const badUrl = form.officialUrl.trim() !== '' && !isWebLink(form.officialUrl);
  const canValidate = Boolean(file && form.code.trim() && form.name.trim() && form.version.trim() && !badUrl);

  return (
    <Dialog open={open} onClose={close} maxWidth="md" fullWidth>
      <DialogTitle>Importar el listado de un estándar propio</DialogTitle>
      <DialogContent dividers>
        <Alert severity="info" sx={{ mb: 2 }}>
          Importa solo listados de los que tengas derecho de uso (por ejemplo, los que recibes de tu organismo de
          certificación). Quedan privados de tu organización. Una versión nueva se importa como estándar nuevo y no
          altera las auditorías ya hechas.{' '}
          <Button size="small" onClick={() => CatalogApi.downloadTemplate()}>
            Descargar la plantilla de Excel
          </Button>
        </Alert>

        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField label="Código" required fullWidth size="small" value={form.code} onChange={set('code')}
              helperText="Letras, números, punto o guion (p. ej. COOP-IFA)" />
          </Grid>
          <Grid size={{ xs: 12, md: 5 }}>
            <TextField label="Nombre" required fullWidth size="small" value={form.name} onChange={set('name')} />
          </Grid>
          <Grid size={{ xs: 12, md: 3 }}>
            <TextField label="Versión" required fullWidth size="small" value={form.version} onChange={set('version')} />
          </Grid>
          <Grid size={{ xs: 12, md: 5 }}>
            <TextField label="Organismo propietario" fullWidth size="small" value={form.ownerBody}
              onChange={set('ownerBody')} />
          </Grid>
          <Grid size={{ xs: 12, md: 3 }}>
            <TextField select label="Categoría" fullWidth size="small" value={form.category} onChange={set('category')}>
              {categories.map((c) => (
                <MenuItem key={c.value} value={c.value}>
                  {c.label}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField label="Enlace oficial" fullWidth size="small" value={form.officialUrl}
              onChange={set('officialUrl')} error={badUrl}
              helperText={badUrl ? 'Debe empezar por http:// o https://' : undefined} />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <TextField label="Descripción" fullWidth size="small" multiline minRows={2} value={form.description}
              onChange={set('description')} />
          </Grid>
          <Grid size={{ xs: 12, md: form.scoringType === 'MAJOR_MINOR' ? 6 : 12 }}>
            <TextField select label="Regla de puntuación" fullWidth size="small" value={form.scoringType}
              onChange={set('scoringType')}>
              {scoringTypes.map((c) => (
                <MenuItem key={c.value} value={c.value}>
                  {c.label}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          {form.scoringType === 'MAJOR_MINOR' && (
            <>
              <Grid size={{ xs: 6, md: 3 }}>
                <TextField label="% mínimo mayores" type="number" fullWidth size="small" value={form.majorMustMinPct}
                  onChange={set('majorMustMinPct')} />
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <TextField label="% mínimo menores" type="number" fullWidth size="small" value={form.minorMustMinPct}
                  onChange={set('minorMustMinPct')} />
              </Grid>
            </>
          )}
          <Grid size={{ xs: 12 }}>
            <Button variant="outlined" component="label">
              {file ? `Fichero: ${file.name}` : 'Elegir fichero (.xlsx o .csv)'}
              <input
                hidden
                type="file"
                accept=".xlsx,.csv,text/csv"
                onChange={(e) => {
                  setFile(e.target.files?.[0] ?? null);
                  setPreview(null);
                }}
              />
            </Button>
          </Grid>
        </Grid>

        {error && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {error}
          </Alert>
        )}

        {preview && (
          <Box sx={{ mt: 2 }}>
            {preview.valid ? (
              <Alert severity="success">
                El listado es válido: {preview.pointCount} puntos de control en {preview.chapterCount} capítulos. Se
                guardará con el código <b>{preview.storedCode}</b>.
              </Alert>
            ) : (
              <Alert severity="error">
                El listado tiene {preview.totalIssues} problema{preview.totalIssues === 1 ? '' : 's'}. Corrígelos y
                vuelve a validar.
              </Alert>
            )}
            {preview.valid && (
              <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: 'wrap' }} useFlexGap>
                {Object.entries(preview.levelCounts).map(([level, n]) => (
                  <Chip key={level} size="small" label={`${label(levelLabels, level)}: ${n}`} />
                ))}
              </Stack>
            )}
            {preview.issues.length > 0 && (
              <Table size="small" sx={{ mt: 1 }}>
                <TableHead>
                  <TableRow>
                    <TableCell>Fila</TableCell>
                    <TableCell>Columna</TableCell>
                    <TableCell>Problema</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {preview.issues.map((i, idx) => (
                    <TableRow key={idx}>
                      <TableCell>{i.row === 0 ? '—' : i.row}</TableCell>
                      <TableCell>{i.column}</TableCell>
                      <TableCell>{i.message}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
            {preview.valid && preview.sample.length > 0 && (
              <Box sx={{ mt: 1 }}>
                <Typography variant="caption" color="text.secondary">
                  Primeros puntos:
                </Typography>
                {preview.sample.map((p) => (
                  <Typography key={p.row} variant="body2">
                    {p.chapterCode} · {p.ref} — {p.title} ({label(levelLabels, p.level)})
                  </Typography>
                ))}
              </Box>
            )}
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        {busy && <CircularProgress size={20} />}
        <Button onClick={close}>Cancelar</Button>
        <Button variant="outlined" disabled={!canValidate || busy} onClick={validate}>
          Validar
        </Button>
        <Button variant="contained" disabled={!preview?.valid || busy} onClick={doImport}>
          Importar
        </Button>
      </DialogActions>
    </Dialog>
  );
}
