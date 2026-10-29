import { useEffect, useRef, useState } from 'react';
import {
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  FormControlLabel,
  IconButton,
  Link,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import AttachFileIcon from '@mui/icons-material/AttachFile';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import PersonOutlineIcon from '@mui/icons-material/PersonOutlined';
import { AttachmentApi } from '../api/endpoints';
import type { Attachment, ComplianceValue, SuggestedFinding } from '../api/types';
import { useToast } from '../utils/toast';
import { extractErrorMessage } from '../api/client';
import { useFeatures } from '../utils/useFeatures';
import EvidenceAnalysisDialog from './EvidenceAnalysisDialog';

export interface AnalyzeContext {
  auditId: number;
  controlPointId?: number;
  onApplyNote?: (text: string) => void | Promise<unknown>;
  onApplyCompliance?: (value: ComplianceValue) => void | Promise<unknown>;
  onCreateFinding?: (draft: SuggestedFinding) => void | Promise<unknown>;
}

function humanSize(bytes: number | null) {
  if (!bytes) return '';
  return bytes < 1024 * 1024
    ? `${Math.max(1, Math.round(bytes / 1024))} KB`
    : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function ImageThumb({ id, name }: { id: number; name: string }) {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    let revoked: string | null = null;
    AttachmentApi.download(id)
      .then((blob) => {
        const objectUrl = URL.createObjectURL(blob);
        revoked = objectUrl;
        setUrl(objectUrl);
      })
      .catch(() => setUrl(null));
    return () => {
      if (revoked) URL.revokeObjectURL(revoked);
    };
  }, [id]);

  if (!url) {
    return (
      <Box
        sx={{
          width: 88,
          height: 88,
          display: 'grid',
          placeItems: 'center',
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 1,
        }}
      >
        <CircularProgress size={18} />
      </Box>
    );
  }
  return (
    <a href={url} target="_blank" rel="noopener" title={name}>
      <Box
        component="img"
        src={url}
        alt={name}
        sx={{
          width: 88,
          height: 88,
          objectFit: 'cover',
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 1,
          display: 'block',
        }}
      />
    </a>
  );
}

export default function AttachmentPanel({
  attachments,
  canEdit,
  onUpload,
  onDelete,
  onMarkPersonalData,
  compact,
  analyze,
}: {
  attachments: Attachment[];
  canEdit: boolean;
  onUpload: (file: File, containsPersonalData: boolean) => Promise<unknown>;
  onDelete: (id: number) => Promise<unknown>;
  /** Cambia la marca de datos personales; si no se indica, la marca es solo de lectura. */
  onMarkPersonalData?: (id: number, value: boolean) => Promise<unknown>;
  compact?: boolean;
  analyze?: AnalyzeContext;
}) {
  const { notify } = useToast();
  const features = useFeatures();
  const inputRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [personalData, setPersonalData] = useState(false);
  const [analyzing, setAnalyzing] = useState<Attachment | null>(null);
  const ocrEnabled = features.evidenceOcr && !!analyze;
  const canAnalyze = (a: Attachment) =>
    ocrEnabled && (a.image || (a.contentType ?? '').includes('pdf'));

  const handleFiles = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    setBusy(true);
    try {
      for (const file of Array.from(files)) {
        await onUpload(file, personalData);
      }
      notify('Evidencia subida', 'success');
      setPersonalData(false);
    } catch (e) {
      notify(extractErrorMessage(e), 'error');
    } finally {
      setBusy(false);
      if (inputRef.current) inputRef.current.value = '';
    }
  };

  const openFile = async (att: Attachment) => {
    try {
      const blob = await AttachmentApi.download(att.id);
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener');
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (e) {
      notify(extractErrorMessage(e), 'error');
    }
  };

  const remove = async (id: number) => {
    if (!window.confirm('¿Eliminar esta evidencia?')) return;
    try {
      await onDelete(id);
      notify('Evidencia eliminada', 'success');
    } catch (e) {
      notify(extractErrorMessage(e), 'error');
    }
  };

  const toggleMark = async (att: Attachment) => {
    if (!onMarkPersonalData) return;
    try {
      await onMarkPersonalData(att.id, !att.containsPersonalData);
      notify(att.containsPersonalData ? 'Marca de datos personales quitada' : 'Marcada como con datos personales', 'success');
    } catch (e) {
      notify(extractErrorMessage(e), 'error');
    }
  };

  // La marca solo llega a administración, calidad y a quien subió el adjunto; para el resto es null y no se muestra.
  const personalMark = (a: Attachment) =>
    a.containsPersonalData === null || a.containsPersonalData === undefined ? null : (
      <Tooltip
        title={
          a.containsPersonalData
            ? 'Contiene datos personales (visible solo para administración, calidad y quien lo subió)'
            : 'Sin marca de datos personales'
        }
      >
        <span>
          <Chip
            size="small"
            icon={<PersonOutlineIcon />}
            label={a.containsPersonalData ? 'Datos personales' : 'Sin marca'}
            color={a.containsPersonalData ? 'warning' : 'default'}
            variant={a.containsPersonalData ? 'filled' : 'outlined'}
            onClick={onMarkPersonalData ? () => toggleMark(a) : undefined}
            sx={{ height: 20, fontSize: 11 }}
          />
        </span>
      </Tooltip>
    );

  const images = attachments.filter((a) => a.image);
  const files = attachments.filter((a) => !a.image);

  return (
    <Box
      sx={{
        mt: compact ? 1 : 1.5,
        p: 1,
        borderRadius: 1,
        border: '1px dashed',
        borderColor: 'divider',
        bgcolor: 'action.hover',
      }}
    >
      <Stack direction="row" spacing={1.5} useFlexGap sx={{ alignItems: "center", flexWrap: "wrap" }}>
        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
          Evidencias
          <Typography component="span" variant="body2" color="text.secondary" sx={{ ml: 0.5 }}>
            ({attachments.length})
          </Typography>
        </Typography>
        {canEdit && (
          <>
            <input
              ref={inputRef}
              type="file"
              hidden
              multiple
              accept="image/*,application/pdf"
              onChange={(e) => handleFiles(e.target.files)}
            />
            <Button
              variant="contained"
              size="small"
              disableElevation
              startIcon={busy ? <CircularProgress size={14} color="inherit" /> : <AttachFileIcon />}
              onClick={() => inputRef.current?.click()}
              disabled={busy}
            >
              {busy ? 'Subiendo…' : 'Adjuntar foto o PDF'}
            </Button>
            {attachments.length === 0 && (
              <Typography variant="caption" color="text.secondary">
                Añade fotos, escaneados o PDF como prueba objetiva.
              </Typography>
            )}
          </>
        )}
      </Stack>
      {canEdit && (
        <>
          <FormControlLabel
            sx={{ mt: 0.5, mb: -0.5 }}
            control={
              <Checkbox size="small" checked={personalData} onChange={(e) => setPersonalData(e.target.checked)} />
            }
            label={
              <Typography variant="caption">
                La evidencia que voy a subir contiene datos personales (personas identificables, nombres, DNI…)
              </Typography>
            }
          />
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
            Aviso: suba solo lo necesario como prueba y evite fotografiar a personas o documentos de identidad si no es
            imprescindible. La marca solo la ven administración, calidad y quien la sube.
          </Typography>
        </>
      )}

      {images.length > 0 && (
        <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 0.5, flexWrap: "wrap" }}>
          {images.map((a) => (
            <Box key={a.id} sx={{ position: 'relative' }}>
              <ImageThumb id={a.id} name={a.fileName} />
              {personalMark(a) && <Box sx={{ mt: 0.5 }}>{personalMark(a)}</Box>}
              {canEdit && (
                <IconButton
                  size="small"
                  onClick={() => remove(a.id)}
                  sx={{
                    position: 'absolute',
                    top: -8,
                    right: -8,
                    bgcolor: 'background.paper',
                    border: '1px solid',
                    borderColor: 'divider',
                  }}
                >
                  <DeleteOutlineIcon sx={{ fontSize: 14 }} />
                </IconButton>
              )}
              {canAnalyze(a) && (
                <Button
                  size="small"
                  variant="contained"
                  color="secondary"
                  disableElevation
                  startIcon={<AutoAwesomeIcon sx={{ fontSize: 14 }} />}
                  onClick={() => setAnalyzing(a)}
                  sx={{
                    position: 'absolute',
                    bottom: 4,
                    left: 4,
                    minWidth: 0,
                    px: 0.75,
                    py: 0.1,
                    fontSize: 11,
                    lineHeight: 1.6,
                  }}
                >
                  Analizar
                </Button>
              )}
            </Box>
          ))}
        </Stack>
      )}

      {files.length > 0 && (
        <Stack spacing={0.25} sx={{ mt: 0.5 }}>
          {files.map((a) => (
            <Stack key={a.id} direction="row" spacing={0.5} sx={{ alignItems: "center" }}>
              <PictureAsPdfIcon sx={{ fontSize: 16, color: 'error.main' }} />
              <Link component="button" type="button" variant="body2" onClick={() => openFile(a)}>
                {a.fileName}
              </Link>
              <Typography variant="caption" color="text.secondary">
                {humanSize(a.sizeBytes)}
              </Typography>
              {personalMark(a)}
              {canAnalyze(a) && (
                <Button
                  size="small"
                  variant="outlined"
                  color="secondary"
                  startIcon={<AutoAwesomeIcon sx={{ fontSize: 14 }} />}
                  onClick={() => setAnalyzing(a)}
                >
                  Analizar texto
                </Button>
              )}
              {canEdit && (
                <IconButton size="small" onClick={() => remove(a.id)}>
                  <DeleteOutlineIcon sx={{ fontSize: 14 }} />
                </IconButton>
              )}
            </Stack>
          ))}
        </Stack>
      )}

      {analyzing && analyze && (
        <EvidenceAnalysisDialog
          open
          onClose={() => setAnalyzing(null)}
          auditId={analyze.auditId}
          attachmentId={analyzing.id}
          fileName={analyzing.fileName}
          controlPointId={analyze.controlPointId}
          onApplyNote={analyze.onApplyNote}
          onApplyCompliance={analyze.onApplyCompliance}
          onCreateFinding={analyze.onCreateFinding}
        />
      )}
    </Box>
  );
}
