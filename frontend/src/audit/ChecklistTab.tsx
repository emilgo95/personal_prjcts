import { useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Button,
  Chip,
  Divider,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import SaveIcon from '@mui/icons-material/Save';
import { AttachmentApi, AuditApi, FindingApi } from '../api/endpoints';
import type { AuditItemResult, ComplianceValue, ResultUpdate } from '../api/types';
import QueryBoundary from '../components/QueryBoundary';
import AttachmentPanel from '../components/AttachmentPanel';
import { useToast } from '../utils/toast';
import { extractErrorMessage } from '../api/client';
import { complianceLabels, levelLabels, label } from '../utils/labels';
import { useAuth } from '../auth/AuthContext';

type Draft = Record<number, { compliance: ComplianceValue; evidenceNotes: string; auditorComment: string }>;

const OPTIONS: ComplianceValue[] = ['COMPLIANT', 'NON_COMPLIANT', 'NOT_APPLICABLE', 'NOT_ASSESSED'];
const COLORS: Record<ComplianceValue, 'success' | 'error' | 'standard' | 'warning'> = {
  COMPLIANT: 'success',
  NON_COMPLIANT: 'error',
  NOT_APPLICABLE: 'standard',
  NOT_ASSESSED: 'warning',
};

export default function ChecklistTab({ auditId }: { auditId: number; standardCode: string }) {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const { hasRole } = useAuth();
  const readOnly = !hasRole('ADMIN', 'AUDITOR');

  const results = useQuery({
    queryKey: ['audit', auditId, 'results'],
    queryFn: () => AuditApi.results(auditId),
  });

  const [draft, setDraft] = useState<Draft>({});
  // snapshot del servidor por punto de control, para saber qué se ha editado en local
  const serverSnapshot = useRef<Draft>({});

  useEffect(() => {
    if (!results.data) return;
    setDraft((current) => {
      const next: Draft = {};
      const snapshot: Draft = {};
      for (const r of results.data!) {
        const fromServer = {
          compliance: r.compliance,
          evidenceNotes: r.evidenceNotes ?? '',
          auditorComment: r.auditorComment ?? '',
        };
        snapshot[r.controlPointId] = fromServer;
        const local = current[r.controlPointId];
        const prevServer = serverSnapshot.current[r.controlPointId];
        const editedLocally =
          local &&
          prevServer &&
          (local.compliance !== prevServer.compliance ||
            local.evidenceNotes !== prevServer.evidenceNotes ||
            local.auditorComment !== prevServer.auditorComment);
        next[r.controlPointId] = editedLocally ? local : fromServer;
      }
      serverSnapshot.current = snapshot;
      return next;
    });
  }, [results.data]);

  const grouped = useMemo(() => {
    const map = new Map<string, { title: string; items: AuditItemResult[] }>();
    for (const r of results.data ?? []) {
      if (!map.has(r.chapterCode)) map.set(r.chapterCode, { title: r.chapterTitle, items: [] });
      map.get(r.chapterCode)!.items.push(r);
    }
    return [...map.entries()];
  }, [results.data]);

  const dirty = useMemo(() => {
    if (!results.data) return [] as ResultUpdate[];
    const changes: ResultUpdate[] = [];
    for (const r of results.data) {
      const d = draft[r.controlPointId];
      if (!d) continue;
      if (
        d.compliance !== r.compliance ||
        d.evidenceNotes !== (r.evidenceNotes ?? '') ||
        d.auditorComment !== (r.auditorComment ?? '')
      ) {
        changes.push({
          controlPointId: r.controlPointId,
          compliance: d.compliance,
          evidenceNotes: d.evidenceNotes || null,
          auditorComment: d.auditorComment || null,
        });
      }
    }
    return changes;
  }, [draft, results.data]);

  const save = useMutation({
    mutationFn: (changes: ResultUpdate[]) => AuditApi.updateResults(auditId, changes),
    onSuccess: () => {
      notify('Checklist guardado', 'success');
      queryClient.invalidateQueries({ queryKey: ['audit', auditId] });
      queryClient.invalidateQueries({ queryKey: ['audit', auditId, 'results'] });
      queryClient.invalidateQueries({ queryKey: ['audit', auditId, 'score'] });
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const update = (id: number, patch: Partial<Draft[number]>) =>
    setDraft((d) => ({ ...d, [id]: { ...d[id], ...patch } }));

  const refreshResults = () =>
    queryClient.invalidateQueries({ queryKey: ['audit', auditId, 'results'] });

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="body2" color="text.secondary">
          {dirty.length > 0 ? `${dirty.length} cambios sin guardar` : 'Sin cambios pendientes'}
        </Typography>
        {!readOnly && (
          <Button
            variant="contained"
            startIcon={<SaveIcon />}
            disabled={dirty.length === 0 || save.isPending}
            onClick={() => save.mutate(dirty)}
          >
            Guardar cambios
          </Button>
        )}
      </Stack>

      <QueryBoundary isLoading={results.isLoading} error={results.error}>
        {grouped.map(([code, group]) => {
          const done = group.items.filter(
            (i) => (draft[i.controlPointId]?.compliance ?? i.compliance) !== 'NOT_ASSESSED',
          ).length;
          return (
            <Accordion key={code} defaultExpanded={grouped.length <= 3}>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Typography sx={{ flexGrow: 1 }} fontWeight={600}>
                  {code} · {group.title}
                </Typography>
                <Chip size="small" label={`${done}/${group.items.length}`} />
              </AccordionSummary>
              <AccordionDetails>
                <Stack divider={<Divider />} spacing={2}>
                  {group.items.map((item) => {
                    const d = draft[item.controlPointId];
                    return (
                      <Box key={item.controlPointId}>
                        <Stack direction="row" spacing={1} alignItems="flex-start">
                          <Typography fontWeight={700} sx={{ minWidth: 64 }}>
                            {item.ref}
                          </Typography>
                          <Box sx={{ flexGrow: 1 }}>
                            <Typography fontWeight={600}>
                              {item.title}{' '}
                              <Chip
                                size="small"
                                label={label(levelLabels, item.level)}
                                sx={{ ml: 0.5 }}
                                variant="outlined"
                              />
                              {item.guidance && (
                                <Tooltip title={item.guidance}>
                                  <InfoOutlinedIcon fontSize="small" sx={{ ml: 0.5, verticalAlign: 'middle' }} />
                                </Tooltip>
                              )}
                            </Typography>
                            {item.description && (
                              <Typography variant="body2" color="text.secondary">
                                {item.description}
                              </Typography>
                            )}
                          </Box>
                        </Stack>

                        <Stack spacing={1} mt={1} sx={{ pl: { sm: 9 } }}>
                          <ToggleButtonGroup
                            exclusive
                            size="small"
                            value={d?.compliance ?? 'NOT_ASSESSED'}
                            onChange={(_, v: ComplianceValue | null) =>
                              v && !readOnly && update(item.controlPointId, { compliance: v })
                            }
                          >
                            {OPTIONS.map((o) => (
                              <ToggleButton key={o} value={o} color={COLORS[o]} disabled={readOnly}>
                                {complianceLabels[o]}
                              </ToggleButton>
                            ))}
                          </ToggleButtonGroup>
                          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
                            <TextField
                              label="Evidencias / hallazgos"
                              size="small"
                              fullWidth
                              multiline
                              disabled={readOnly}
                              value={d?.evidenceNotes ?? ''}
                              onChange={(e) => update(item.controlPointId, { evidenceNotes: e.target.value })}
                            />
                            <TextField
                              label="Comentario del auditor"
                              size="small"
                              fullWidth
                              multiline
                              disabled={readOnly}
                              value={d?.auditorComment ?? ''}
                              onChange={(e) => update(item.controlPointId, { auditorComment: e.target.value })}
                            />
                          </Stack>
                          <AttachmentPanel
                            attachments={item.attachments}
                            canEdit={!readOnly}
                            compact
                            onUpload={(file) =>
                              AttachmentApi.uploadForControlPoint(auditId, item.controlPointId, file).then(
                                () => refreshResults(),
                              )
                            }
                            onDelete={(id) => AttachmentApi.remove(id).then(() => refreshResults())}
                            analyze={
                              readOnly
                                ? undefined
                                : {
                                    auditId,
                                    controlPointId: item.controlPointId,
                                    onApplyNote: (text) =>
                                      update(item.controlPointId, { evidenceNotes: text }),
                                    onApplyCompliance: (v) =>
                                      update(item.controlPointId, { compliance: v }),
                                    onCreateFinding: (draft) =>
                                      FindingApi.create(auditId, {
                                        controlPointId: item.controlPointId,
                                        classification: draft.classification,
                                        title: draft.title,
                                        description: draft.description,
                                        status: 'OPEN',
                                      }).then(() => {
                                        notify('No conformidad creada', 'success');
                                        queryClient.invalidateQueries({ queryKey: ['audit', auditId] });
                                        queryClient.invalidateQueries({
                                          queryKey: ['audit', auditId, 'findings'],
                                        });
                                      }),
                                  }
                            }
                          />
                        </Stack>
                      </Box>
                    );
                  })}
                </Stack>
              </AccordionDetails>
            </Accordion>
          );
        })}
      </QueryBoundary>
    </Box>
  );
}
