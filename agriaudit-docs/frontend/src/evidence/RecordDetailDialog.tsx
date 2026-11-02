import { useRef } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router-dom';
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
  IconButton,
  Link,
  Stack,
  Typography,
} from '@mui/material';
import AttachFileIcon from '@mui/icons-material/AttachFile';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import { AttachmentApi, EvidenceApi, downloadBlob } from '../api/endpoints';
import { extractErrorMessage } from '../api/client';
import EvidenceStatusChip from '../components/EvidenceStatusChip';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../utils/toast';
import { useConfirm } from '../utils/confirm';
import { formatDate } from '../utils/dates';
import { auditStatusLabels, evidenceOutcomeLabels, label } from '../utils/labels';

/** Ficha de un registro: datos, documentos (una sola vez) y dónde se usa. */
export default function RecordDetailDialog({ recordId, onClose }: { recordId: number | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const confirm = useConfirm();
  const { hasRole } = useAuth();
  const inputRef = useRef<HTMLInputElement>(null);
  const canUpload = hasRole('ADMIN', 'AUDITOR', 'QUALITY');
  const canDeleteDoc = hasRole('ADMIN', 'AUDITOR');

  const detail = useQuery({
    queryKey: ['evidence-record', recordId],
    queryFn: () => EvidenceApi.record(recordId as number),
    enabled: recordId != null,
  });

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['evidence-record', recordId] });
    queryClient.invalidateQueries({ queryKey: ['evidence-records'] });
  };

  const upload = useMutation({
    mutationFn: async (files: File[]) => {
      for (const f of files) await EvidenceApi.upload(recordId as number, f);
    },
    onSuccess: () => {
      notify('Documento subido', 'success');
      refresh();
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
    onSettled: () => {
      if (inputRef.current) inputRef.current.value = '';
    },
  });

  const removeDoc = async (id: number, name: string) => {
    const ok = await confirm({
      title: 'Eliminar documento',
      message: `¿Eliminar «${name}»? Esta acción no se puede deshacer.`,
      confirmLabel: 'Eliminar',
      tone: 'danger',
    });
    if (!ok) return;
    try {
      await AttachmentApi.remove(id);
      notify('Documento eliminado', 'success');
      refresh();
    } catch (e) {
      notify(extractErrorMessage(e), 'error');
    }
  };

  const open = async (id: number, name: string) => {
    try {
      downloadBlob(await AttachmentApi.download(id), name);
    } catch (e) {
      notify(extractErrorMessage(e), 'error');
    }
  };

  const r = detail.data?.record;

  return (
    <Dialog open={recordId != null} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{r?.title ?? 'Evidencia'}</DialogTitle>
      <DialogContent>
        {detail.isLoading && (
          <Box sx={{ display: 'grid', placeItems: 'center', py: 4 }}>
            <CircularProgress />
          </Box>
        )}
        {detail.error != null && <Alert severity="error">{extractErrorMessage(detail.error)}</Alert>}
        {r && detail.data && (
          <Stack spacing={2} sx={{ mt: 0.5 }}>
            <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'center' }}>
              <EvidenceStatusChip status={r.status} />
              {r.outcome !== 'UNRATED' && (
                <Chip
                  size="small"
                  color={r.outcome === 'FAVORABLE' ? 'success' : 'error'}
                  variant="outlined"
                  label={`Resultado: ${evidenceOutcomeLabels[r.outcome]}`}
                />
              )}
              {r.containsPersonalData && <Chip size="small" color="warning" label="Datos personales" />}
            </Stack>
            <Typography variant="body2">
              <strong>{r.typeName}</strong> · {r.farmName}
            </Typography>
            <Typography variant="body2">
              Documento del {formatDate(r.issuedOn)} · {r.validUntil ? `válido hasta el ${formatDate(r.validUntil)}` : 'sin caducidad'}
            </Typography>
            {r.notes && <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{r.notes}</Typography>}

            <Box>
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <Typography variant="subtitle2">Documentos ({detail.data.attachments.length})</Typography>
                {canUpload && (
                  <>
                    <input
                      ref={inputRef}
                      type="file"
                      hidden
                      multiple
                      accept="image/*,application/pdf"
                      onChange={(e) => e.target.files && upload.mutate(Array.from(e.target.files))}
                    />
                    <Button
                      size="small"
                      startIcon={<AttachFileIcon />}
                      disabled={upload.isPending}
                      onClick={() => inputRef.current?.click()}
                    >
                      {upload.isPending ? 'Subiendo…' : 'Subir documento'}
                    </Button>
                  </>
                )}
              </Stack>
              {detail.data.attachments.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  Todavía no hay documentos. Súbelo aquí una sola vez y servirá para todas las auditorías.
                </Typography>
              ) : (
                <Stack spacing={0.25}>
                  {detail.data.attachments.map((a) => (
                    <Stack key={a.id} direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                      <Link component="button" type="button" variant="body2" onClick={() => open(a.id, a.fileName)}>
                        {a.fileName}
                      </Link>
                      {canDeleteDoc && (
                        <IconButton size="small" aria-label={`Eliminar ${a.fileName}`} onClick={() => removeDoc(a.id, a.fileName)}>
                          <DeleteOutlineIcon sx={{ fontSize: 16 }} />
                        </IconButton>
                      )}
                    </Stack>
                  ))}
                </Stack>
              )}
            </Box>

            <Box>
              <Typography variant="subtitle2">Dónde se usa ({detail.data.uses.length})</Typography>
              {detail.data.uses.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  Todavía no está vinculada a ninguna auditoría. Se sugiere desde el checklist de cada auditoría.
                </Typography>
              ) : (
                <Stack spacing={0.25}>
                  {detail.data.uses.map((u) => (
                    <Typography key={u.linkId} variant="body2">
                      <Link component={RouterLink} to={`/audits/${u.auditId}`} onClick={onClose}>
                        {u.auditReference}
                      </Link>{' '}
                      · punto {u.controlRef ?? '—'} ({u.standardCode}) · {label(auditStatusLabels, u.auditStatus)}
                    </Typography>
                  ))}
                </Stack>
              )}
            </Box>
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cerrar</Button>
      </DialogActions>
    </Dialog>
  );
}
