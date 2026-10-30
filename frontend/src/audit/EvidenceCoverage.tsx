import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router-dom';
import { Alert, Box, Button, Chip, Link, Stack, Tooltip, Typography } from '@mui/material';
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined';
import LinkIcon from '@mui/icons-material/Link';
import LinkOffIcon from '@mui/icons-material/LinkOff';
import { EvidenceApi } from '../api/endpoints';
import type { EvidenceCoverageItem } from '../api/types';
import { extractErrorMessage } from '../api/client';
import EvidenceStatusChip from '../components/EvidenceStatusChip';
import { useToast } from '../utils/toast';
import { formatDate } from '../utils/dates';

/**
 * «Cubierto por…»: evidencias ya registradas para la explotación que pueden servir en este punto. Solo sugiere:
 * el auditor confirma el vínculo y nada se marca conforme por sí solo.
 */
export default function EvidenceCoverage({
  auditId,
  item,
  canEdit,
}: {
  auditId: number;
  item: EvidenceCoverageItem;
  canEdit: boolean;
}) {
  const queryClient = useQueryClient();
  const { notify } = useToast();

  const onDone = (message: string) => () => {
    notify(message, 'success');
    queryClient.invalidateQueries({ queryKey: ['evidence-coverage', auditId] });
    queryClient.invalidateQueries({ queryKey: ['evidence-map', auditId] });
    queryClient.invalidateQueries({ queryKey: ['evidence-records'] });
    queryClient.invalidateQueries({ queryKey: ['audit', auditId, 'trace'] });
  };
  const link = useMutation({
    mutationFn: (recordId: number) => EvidenceApi.link(auditId, item.controlPointId, recordId),
    onSuccess: onDone('Evidencia vinculada'),
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });
  const unlink = useMutation({
    mutationFn: (linkId: number) => EvidenceApi.unlink(auditId, linkId),
    onSuccess: onDone('Vínculo quitado'),
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  return (
    <Box
      sx={{ p: 1, borderRadius: 1, border: '1px dashed', borderColor: item.covered ? 'success.main' : 'divider', bgcolor: 'action.hover' }}
      data-testid={`coverage-${item.controlRef}`}
    >
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 0.5 }}>
        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
          Evidencias reutilizables
        </Typography>
        {item.covered && <Chip size="small" color="success" icon={<CheckCircleOutlinedIcon />} label="Cubierto" />}
      </Stack>
      {item.alert && (
        <Alert severity="warning" sx={{ mb: 1 }}>
          Una evidencia vinculada ya no es válida (vencida o con resultado desfavorable): revisa este punto.
        </Alert>
      )}
      <Stack spacing={1}>
        {item.types.map((t) => (
          <Box key={t.typeCode}>
            <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {t.typeName}
              </Typography>
              {t.matrixStatus === 'DRAFT' && (
                <Tooltip title="Esta equivalencia aún no la ha validado el responsable de calidad">
                  <Chip size="small" variant="outlined" color="warning" label="Equivalencia por validar" />
                </Tooltip>
              )}
            </Stack>
            {t.records.length === 0 ? (
              <Typography variant="caption" color="text.secondary">
                No hay ningún registro de este tipo para la explotación.{' '}
                <Link component={RouterLink} to="/audits/evidence">
                  Registrarlo
                </Link>
              </Typography>
            ) : (
              <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                {t.records.map((r) => (
                  <Stack
                    key={r.id}
                    direction={{ xs: 'column', sm: 'row' }}
                    spacing={1}
                    sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between' }}
                  >
                    <Box sx={{ minWidth: 0 }}>
                      <Stack direction="row" spacing={0.75} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                        <Typography variant="body2">{r.title}</Typography>
                        <EvidenceStatusChip status={r.status} />
                        {r.outcome === 'UNFAVORABLE' && <Chip size="small" color="error" variant="outlined" label="Desfavorable" />}
                      </Stack>
                      <Typography variant="caption" color="text.secondary">
                        {formatDate(r.issuedOn)} · {r.validUntil ? `hasta ${formatDate(r.validUntil)}` : 'sin caducidad'} · {r.documents}{' '}
                        documento(s)
                      </Typography>
                      {!r.linkable && r.linkId == null && (
                        <Typography variant="caption" color="warning.dark" sx={{ display: 'block' }}>
                          {r.notLinkableReason}
                        </Typography>
                      )}
                    </Box>
                    {canEdit && (
                      <Box sx={{ flexShrink: 0 }}>
                        {r.linkId != null ? (
                          <Button
                            size="small"
                            color="inherit"
                            startIcon={<LinkOffIcon />}
                            disabled={unlink.isPending}
                            onClick={() => unlink.mutate(r.linkId as number)}
                            aria-label={`Quitar vínculo de ${r.title}`}
                          >
                            Quitar vínculo
                          </Button>
                        ) : (
                          <Button
                            size="small"
                            variant="outlined"
                            startIcon={<LinkIcon />}
                            disabled={!r.linkable || link.isPending}
                            onClick={() => link.mutate(r.id)}
                            aria-label={`Vincular ${r.title}`}
                          >
                            Vincular
                          </Button>
                        )}
                      </Box>
                    )}
                  </Stack>
                ))}
              </Stack>
            )}
          </Box>
        ))}
      </Stack>
    </Box>
  );
}
