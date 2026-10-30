import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Grid,
  IconButton,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import { AttachmentApi, AuditApi, FindingApi, TraceApi } from '../api/endpoints';
import TraceTimeline from '../components/TraceTimeline';
import { useAuth } from '../auth/AuthContext';
import type { CorrectiveAction, CorrectiveActionRequest, Finding, FindingRequest } from '../api/types';
import QueryBoundary from '../components/QueryBoundary';
import AttachmentPanel from '../components/AttachmentPanel';
import DeadlineChip from '../components/DeadlineChip';
import { formatDate } from '../utils/dates';
import { useToast } from '../utils/toast';
import { useConfirm } from '../utils/confirm';
import { extractErrorMessage } from '../api/client';
import {
  caStatusLabels,
  classificationColors,
  classificationLabels,
  findingStatusColors,
  findingStatusLabels,
  label,
} from '../utils/labels';

const emptyFinding: FindingRequest = {
  controlPointId: null,
  classification: 'MINOR',
  title: '',
  description: '',
  area: '',
  dueDate: '',
  status: 'OPEN',
};

const emptyCa: CorrectiveActionRequest = {
  rootCause: '',
  correction: '',
  preventiveAction: '',
  responsiblePerson: '',
  targetDate: '',
  completedDate: '',
  verificationNotes: '',
  verifiedBy: '',
  status: 'PENDING',
};

type ControlOption = { controlPointId: number; ref: string; title: string };

export default function FindingsTab({
  auditId,
  locked = false,
}: {
  auditId: number;
  standardCode: string;
  /** Auditoría enviada o cerrada: las no conformidades son de solo lectura. */
  locked?: boolean;
}) {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const { hasRole } = useAuth();
  const canEdit = hasRole('ADMIN', 'AUDITOR') && !locked;
  const canVerify = hasRole('ADMIN', 'QUALITY') && !locked;

  const findings = useQuery({ queryKey: ['audit', auditId, 'findings'], queryFn: () => FindingApi.list(auditId) });
  const controls = useQuery({ queryKey: ['audit', auditId, 'results'], queryFn: () => AuditApi.results(auditId) });
  const options: ControlOption[] = (controls.data ?? []).map((c) => ({
    controlPointId: c.controlPointId,
    ref: c.ref,
    title: c.title,
  }));

  const [creating, setCreating] = useState(false);

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['audit', auditId, 'findings'] });
    queryClient.invalidateQueries({ queryKey: ['audit', auditId] });
    queryClient.invalidateQueries({ queryKey: ['audit', auditId, 'score'] });
  };

  const createFinding = useMutation({
    mutationFn: (body: FindingRequest) => FindingApi.create(auditId, body),
    onSuccess: () => {
      notify('No conformidad registrada', 'success');
      setCreating(false);
      invalidate();
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  return (
    <Box>
      {canEdit && !creating && (
        <Button variant="contained" sx={{ mb: 2 }} onClick={() => setCreating(true)}>
          Nueva no conformidad
        </Button>
      )}

      {canEdit && creating && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Nueva no conformidad
            </Typography>
            <FindingForm
              value={emptyFinding}
              options={options}
              submitting={createFinding.isPending}
              onCancel={() => setCreating(false)}
              onSubmit={(body) => createFinding.mutate(body)}
            />
          </CardContent>
        </Card>
      )}

      <QueryBoundary isLoading={findings.isLoading} error={findings.error}>
        <Stack spacing={2}>
          {(findings.data ?? []).map((f) => (
            <FindingCard
              key={f.id}
              auditId={auditId}
              finding={f}
              options={options}
              canEdit={canEdit}
              canVerify={canVerify}
              onChanged={invalidate}
            />
          ))}
          {findings.data?.length === 0 && (
            <Typography color="text.secondary">No se han registrado no conformidades.</Typography>
          )}
        </Stack>
      </QueryBoundary>
    </Box>
  );
}

// ---------------------------------------------------------------------------

function FindingForm({
  value,
  options,
  submitting,
  onSubmit,
  onCancel,
}: {
  value: FindingRequest;
  options: ControlOption[];
  submitting: boolean;
  onSubmit: (body: FindingRequest) => void;
  onCancel: () => void;
}) {
  const [form, setForm] = useState<FindingRequest>(value);

  return (
    <>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 5 }}>
          <TextField
            select
            fullWidth
            label="Punto de control"
            value={form.controlPointId ?? ''}
            onChange={(e) =>
              setForm((f) => ({
                ...f,
                controlPointId: e.target.value === '' ? null : Number(e.target.value),
              }))
            }
          >
            <MenuItem value="">(Sin punto asociado)</MenuItem>
            {options.map((c) => (
              <MenuItem key={c.controlPointId} value={c.controlPointId}>
                {c.ref} · {c.title}
              </MenuItem>
            ))}
          </TextField>
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <TextField
            select
            fullWidth
            label="Clasificación"
            value={form.classification}
            onChange={(e) => setForm((f) => ({ ...f, classification: e.target.value }))}
          >
            {Object.entries(classificationLabels).map(([k, v]) => (
              <MenuItem key={k} value={k}>
                {v}
              </MenuItem>
            ))}
          </TextField>
        </Grid>
        <Grid size={{ xs: 6, md: 4 }}>
          <TextField
            select
            fullWidth
            label="Estado"
            value={form.status ?? 'OPEN'}
            onChange={(e) => setForm((f) => ({ ...f, status: e.target.value }))}
          >
            {Object.entries(findingStatusLabels).map(([k, v]) => (
              <MenuItem key={k} value={k}>
                {v}
              </MenuItem>
            ))}
          </TextField>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <TextField
            fullWidth
            label="Título"
            value={form.title ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
          />
        </Grid>
        <Grid size={{ xs: 6, md: 4 }}>
          <TextField
            fullWidth
            label="Área"
            value={form.area ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, area: e.target.value }))}
          />
        </Grid>
        <Grid size={{ xs: 6, md: 4 }}>
          <TextField
            fullWidth
            type="date"
            label="Fecha límite"
            helperText="Vacía: plazo de la norma (28 días desde la auditoría; las observaciones no tienen plazo)"
            slotProps={{ inputLabel: { shrink: true } }}
            value={form.dueDate ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, dueDate: e.target.value }))}
          />
        </Grid>
        <Grid size={{ xs: 12 }}>
          <TextField
            fullWidth
            multiline
            minRows={2}
            label="Descripción de la no conformidad"
            value={form.description ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
          />
        </Grid>
      </Grid>
      <Stack direction="row" spacing={1} sx={{ mt: 2 }}>
        <Button
          variant="contained"
          disabled={submitting}
          onClick={() => onSubmit({ ...form, dueDate: form.dueDate || null })}
        >
          Guardar
        </Button>
        <Button onClick={onCancel}>Cancelar</Button>
      </Stack>
    </>
  );
}

// ---------------------------------------------------------------------------

function FindingCard({
  auditId,
  finding,
  options,
  canEdit,
  canVerify,
  onChanged,
}: {
  auditId: number;
  finding: Finding;
  options: ControlOption[];
  canEdit: boolean;
  canVerify: boolean;
  onChanged: () => void;
}) {
  const { notify } = useToast();
  const confirm = useConfirm();
  const [editing, setEditing] = useState(false);
  const [addingCa, setAddingCa] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const { hasRole } = useAuth();
  const canSeeHistory = hasRole('ADMIN', 'QUALITY', 'AUDITOR');
  const history = useQuery({
    queryKey: ['audit', auditId, 'finding', finding.id, 'trace'],
    queryFn: () => TraceApi.forFinding(auditId, finding.id),
    enabled: historyOpen,
  });

  const closeFinding = useMutation({
    mutationFn: () => FindingApi.close(auditId, finding.id),
    onSuccess: () => {
      notify('No conformidad cerrada', 'success');
      onChanged();
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const update = useMutation({
    mutationFn: (body: FindingRequest) => FindingApi.update(auditId, finding.id, body),
    onSuccess: () => {
      notify('No conformidad actualizada', 'success');
      setEditing(false);
      onChanged();
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const remove = useMutation({
    mutationFn: () => FindingApi.remove(auditId, finding.id),
    onSuccess: () => {
      notify('No conformidad eliminada', 'success');
      onChanged();
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const addAction = useMutation({
    mutationFn: (body: CorrectiveActionRequest) => FindingApi.addAction(auditId, finding.id, body),
    onSuccess: () => {
      notify('Acción correctiva añadida', 'success');
      setAddingCa(false);
      onChanged();
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  return (
    <Card variant="outlined">
      <CardContent>
        {editing ? (
          <FindingForm
            value={{
              controlPointId: finding.controlPointId,
              classification: finding.classification,
              title: finding.title ?? '',
              description: finding.description ?? '',
              area: finding.area ?? '',
              dueDate: finding.dueDate ?? '',
              status: finding.status,
            }}
            options={options}
            submitting={update.isPending}
            onCancel={() => setEditing(false)}
            onSubmit={(body) => update.mutate(body)}
          />
        ) : (
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} sx={{ justifyContent: "space-between", alignItems: { xs: 'flex-start', md: 'flex-start' } }}>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="overline" color="text.secondary" sx={{ lineHeight: 1.5 }}>
                {finding.reference} · {finding.controlPointRef ?? finding.area ?? 'General'}
              </Typography>
              {finding.title && <Typography sx={{ fontWeight: 700 }}>{finding.title}</Typography>}
              {finding.description && (
                <Typography variant="body2" color="text.secondary">
                  {finding.description}
                </Typography>
              )}
            </Box>
            <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: "center", flexWrap: "wrap", flexShrink: 0 }}>
              <Chip
                size="small"
                label={label(classificationLabels, finding.classification)}
                color={classificationColors[finding.classification] ?? 'default'}
              />
              <Chip
                size="small"
                label={label(findingStatusLabels, finding.status)}
                color={findingStatusColors[finding.status] ?? 'default'}
                variant="outlined"
              />
              {finding.dueDate && (
                <DeadlineChip
                  dueDate={finding.dueDate}
                  closed={finding.status === 'CLOSED' || finding.status === 'VERIFIED'}
                  closedAt={finding.closedAt}
                />
              )}
              {canVerify && finding.status !== 'CLOSED' && (
                <Button size="small" variant="outlined" disabled={closeFinding.isPending} onClick={() => closeFinding.mutate()}>
                  Cerrar NC
                </Button>
              )}
              {canEdit && (
                <>
                  <IconButton size="small" onClick={() => setEditing(true)}>
                    <EditOutlinedIcon fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    aria-label="Eliminar la no conformidad"
                    onClick={async () => {
                      const ok = await confirm({
                        title: '¿Eliminar esta no conformidad?',
                        message: `Se eliminará ${finding.reference}. No se puede deshacer.`,
                        confirmLabel: 'Eliminar',
                        tone: 'danger',
                      });
                      if (ok) remove.mutate();
                    }}
                  >
                    <DeleteOutlineIcon fontSize="small" />
                  </IconButton>
                </>
              )}
            </Stack>
          </Stack>
        )}

        {!editing && (
          <AttachmentPanel
            attachments={finding.attachments}
            canEdit={canEdit}
            onUpload={(file, personalData) =>
              AttachmentApi.uploadForFinding(auditId, finding.id, file, personalData).then(() => onChanged())
            }
            onDelete={(id) => AttachmentApi.remove(id).then(() => onChanged())}
            onMarkPersonalData={(id, value) => AttachmentApi.markPersonalData(id, value).then(() => onChanged())}
            analyze={
              canEdit
                ? {
                    auditId,
                    controlPointId: finding.controlPointId ?? undefined,
                    onApplyNote: async (text) => {
                      await FindingApi.update(auditId, finding.id, {
                        controlPointId: finding.controlPointId,
                        classification: finding.classification,
                        title: finding.title,
                        description: (finding.description ? finding.description + '\n\n' : '') + text,
                        area: finding.area,
                        dueDate: finding.dueDate,
                        status: finding.status,
                      });
                      onChanged();
                    },
                  }
                : undefined
            }
          />
        )}

        <Accordion sx={{ mt: 1 }} disableGutters>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Typography variant="body2">
              Acciones correctivas ({finding.correctiveActions.length})
            </Typography>
          </AccordionSummary>
          <AccordionDetails>
            <Stack divider={<Divider />} spacing={1}>
              {finding.correctiveActions.map((a) => (
                <CorrectiveActionRow
                  key={a.id}
                  auditId={auditId}
                  findingId={finding.id}
                  action={a}
                  canEdit={canEdit}
                  canVerify={canVerify}
                  onChanged={onChanged}
                />
              ))}
              {finding.correctiveActions.length === 0 && (
                <Typography variant="body2" color="text.secondary">
                  Sin acciones correctivas.
                </Typography>
              )}
            </Stack>

            {canEdit && !addingCa && (
              <Button size="small" sx={{ mt: 1 }} onClick={() => setAddingCa(true)}>
                Añadir acción correctiva
              </Button>
            )}
            {canEdit && addingCa && (
              <Box sx={{ mt: 2 }}>
                <CorrectiveActionForm
                  value={emptyCa}
                  submitting={addAction.isPending}
                  onCancel={() => setAddingCa(false)}
                  onSubmit={(body) => addAction.mutate(body)}
                />
              </Box>
            )}
          </AccordionDetails>
        </Accordion>

        {canSeeHistory && (
          <Accordion sx={{ mt: 1 }} disableGutters expanded={historyOpen} onChange={(_, v) => setHistoryOpen(v)}>
            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
              <Typography variant="body2">Historial de cambios</Typography>
            </AccordionSummary>
            <AccordionDetails>
              <QueryBoundary isLoading={history.isLoading} error={history.error}>
                <TraceTimeline entries={history.data?.content ?? []} />
              </QueryBoundary>
            </AccordionDetails>
          </Accordion>
        )}
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------

function CorrectiveActionRow({
  auditId,
  findingId,
  action,
  canEdit,
  canVerify,
  onChanged,
}: {
  auditId: number;
  findingId: number;
  action: CorrectiveAction;
  canEdit: boolean;
  canVerify: boolean;
  onChanged: () => void;
}) {
  const { notify } = useToast();
  const confirm = useConfirm();
  const [editing, setEditing] = useState(false);

  const update = useMutation({
    mutationFn: (body: CorrectiveActionRequest) =>
      FindingApi.updateAction(auditId, findingId, action.id, body),
    onSuccess: () => {
      notify('Acción correctiva actualizada', 'success');
      setEditing(false);
      onChanged();
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const verify = useMutation({
    mutationFn: () => FindingApi.verifyAction(auditId, findingId, action.id),
    onSuccess: () => {
      notify('Acción correctiva verificada', 'success');
      onChanged();
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const remove = useMutation({
    mutationFn: () => FindingApi.deleteAction(auditId, findingId, action.id),
    onSuccess: () => {
      notify('Acción correctiva eliminada', 'success');
      onChanged();
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  if (editing) {
    return (
      <CorrectiveActionForm
        value={{
          rootCause: action.rootCause ?? '',
          correction: action.correction ?? '',
          preventiveAction: action.preventiveAction ?? '',
          responsiblePerson: action.responsiblePerson ?? '',
          targetDate: action.targetDate ?? '',
          completedDate: action.completedDate ?? '',
          verificationNotes: action.verificationNotes ?? '',
          verifiedBy: action.verifiedBy ?? '',
          status: action.status,
        }}
        submitting={update.isPending}
        onCancel={() => setEditing(false)}
        onSubmit={(body) => update.mutate(body)}
      />
    );
  }

  return (
    <Box>
      <Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "flex-start" }}>
        <Box>
          <Typography variant="body2">
            <b>Causa raíz:</b> {action.rootCause || '—'}
          </Typography>
          <Typography variant="body2">
            <b>Corrección:</b> {action.correction || '—'}
          </Typography>
          <Typography variant="body2">
            <b>Acción preventiva:</b> {action.preventiveAction || '—'}
          </Typography>
          {action.verificationNotes && (
            <Typography variant="body2">
              <b>Verificación:</b> {action.verificationNotes}
            </Typography>
          )}
          <Typography variant="caption" color="text.secondary">
            {action.responsiblePerson || 'Sin responsable'} · objetivo {formatDate(action.targetDate)}
            {action.completedDate ? ` · completada ${formatDate(action.completedDate)}` : ''} ·{' '}
            {label(caStatusLabels, action.status)}
          </Typography>
        </Box>
        {canVerify && action.status !== 'VERIFIED' && (
          <Button size="small" variant="outlined" disabled={verify.isPending} onClick={() => verify.mutate()}>
            Verificar
          </Button>
        )}
        {canEdit && (
          <Stack direction="row">
            <IconButton size="small" onClick={() => setEditing(true)}>
              <EditOutlinedIcon fontSize="small" />
            </IconButton>
            <IconButton
              size="small"
              aria-label="Eliminar la acción correctiva"
              onClick={async () => {
                const ok = await confirm({
                  title: '¿Eliminar esta acción correctiva?',
                  message: 'No se puede deshacer.',
                  confirmLabel: 'Eliminar',
                  tone: 'danger',
                });
                if (ok) remove.mutate();
              }}
            >
              <DeleteOutlineIcon fontSize="small" />
            </IconButton>
          </Stack>
        )}
      </Stack>
    </Box>
  );
}

// ---------------------------------------------------------------------------

function CorrectiveActionForm({
  value,
  submitting,
  onSubmit,
  onCancel,
}: {
  value: CorrectiveActionRequest;
  submitting: boolean;
  onSubmit: (body: CorrectiveActionRequest) => void;
  onCancel: () => void;
}) {
  const [ca, setCa] = useState<CorrectiveActionRequest>(value);
  const set =
    (key: keyof CorrectiveActionRequest) =>
    (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
      setCa((c) => ({ ...c, [key]: e.target.value }));

  return (
    <Box>
      <Grid container spacing={1}>
        <Grid size={{ xs: 12, md: 6 }}>
          <TextField size="small" fullWidth label="Causa raíz" value={ca.rootCause ?? ''} onChange={set('rootCause')} />
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <TextField size="small" fullWidth label="Corrección" value={ca.correction ?? ''} onChange={set('correction')} />
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <TextField
            size="small"
            fullWidth
            label="Acción preventiva"
            value={ca.preventiveAction ?? ''}
            onChange={set('preventiveAction')}
          />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <TextField
            size="small"
            fullWidth
            label="Responsable"
            value={ca.responsiblePerson ?? ''}
            onChange={set('responsiblePerson')}
          />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <TextField
            select
            size="small"
            fullWidth
            label="Estado"
            value={ca.status ?? 'PENDING'}
            onChange={set('status')}
          >
            {Object.entries(caStatusLabels).map(([k, v]) => (
              <MenuItem key={k} value={k}>
                {v}
              </MenuItem>
            ))}
          </TextField>
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <TextField
            size="small"
            fullWidth
            type="date"
            label="Fecha objetivo"
            slotProps={{ inputLabel: { shrink: true } }}
            value={ca.targetDate ?? ''}
            onChange={set('targetDate')}
          />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <TextField
            size="small"
            fullWidth
            type="date"
            label="Fecha de cierre"
            slotProps={{ inputLabel: { shrink: true } }}
            value={ca.completedDate ?? ''}
            onChange={set('completedDate')}
          />
        </Grid>
        <Grid size={{ xs: 12, md: 8 }}>
          <TextField
            size="small"
            fullWidth
            label="Notas de verificación"
            value={ca.verificationNotes ?? ''}
            onChange={set('verificationNotes')}
          />
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <TextField
            size="small"
            fullWidth
            label="Verificado por"
            value={ca.verifiedBy ?? ''}
            onChange={set('verifiedBy')}
          />
        </Grid>
      </Grid>
      <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
        <Button
          size="small"
          variant="contained"
          disabled={submitting}
          onClick={() =>
            onSubmit({
              ...ca,
              targetDate: ca.targetDate || null,
              completedDate: ca.completedDate || null,
            })
          }
        >
          Guardar
        </Button>
        <Button size="small" onClick={onCancel}>
          Cancelar
        </Button>
      </Stack>
    </Box>
  );
}
