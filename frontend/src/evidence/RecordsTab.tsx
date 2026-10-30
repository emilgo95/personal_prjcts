import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Box,
  Button,
  Chip,
  IconButton,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import { EvidenceApi } from '../api/endpoints';
import type { EvidenceRecord, EvidenceType, Farm } from '../api/types';
import { extractErrorMessage } from '../api/client';
import QueryBoundary from '../components/QueryBoundary';
import EvidenceStatusChip from '../components/EvidenceStatusChip';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../utils/toast';
import { useConfirm } from '../utils/confirm';
import { formatDate } from '../utils/dates';
import { evidenceOutcomeLabels } from '../utils/labels';

export default function RecordsTab({
  farms,
  types,
  onCreate,
  onEdit,
  onOpen,
  farmFilter,
  onFarmFilter,
  typeFilter,
  onTypeFilter,
}: {
  farms: Farm[];
  types: EvidenceType[];
  onCreate: () => void;
  onEdit: (record: EvidenceRecord) => void;
  onOpen: (record: EvidenceRecord) => void;
  farmFilter: number | '';
  onFarmFilter: (id: number | '') => void;
  typeFilter: string;
  onTypeFilter: (code: string) => void;
}) {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const confirm = useConfirm();
  const { hasRole } = useAuth();
  const canEdit = hasRole('ADMIN', 'AUDITOR', 'QUALITY');
  const canDelete = hasRole('ADMIN', 'QUALITY');

  const { data, isLoading, error } = useQuery({
    queryKey: ['evidence-records', farmFilter, typeFilter],
    queryFn: () =>
      EvidenceApi.records({ farmId: farmFilter === '' ? undefined : farmFilter, typeCode: typeFilter || undefined }),
  });

  const remove = useMutation({
    mutationFn: (id: number) => EvidenceApi.remove(id),
    onSuccess: () => {
      notify('Evidencia eliminada', 'success');
      queryClient.invalidateQueries({ queryKey: ['evidence-records'] });
      queryClient.invalidateQueries({ queryKey: ['evidence-health'] });
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const askRemove = async (r: EvidenceRecord) => {
    const ok = await confirm({
      title: 'Eliminar evidencia',
      message: `¿Eliminar «${r.title}»? También se borrarán sus documentos. Esta acción no se puede deshacer.`,
      confirmLabel: 'Eliminar',
      tone: 'danger',
    });
    if (ok) remove.mutate(r.id);
  };

  return (
    <Box>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 2, alignItems: { md: 'center' } }}>
        <TextField
          select
          size="small"
          label="Explotación"
          value={farmFilter}
          onChange={(e) => onFarmFilter(e.target.value === '' ? '' : Number(e.target.value))}
          sx={{ minWidth: 240 }}
        >
          <MenuItem value="">Todas</MenuItem>
          {farms.map((f) => (
            <MenuItem key={f.id} value={f.id}>
              {f.legalName}
            </MenuItem>
          ))}
        </TextField>
        <TextField select size="small" label="Tipo" value={typeFilter} onChange={(e) => onTypeFilter(e.target.value)} sx={{ minWidth: 260 }}>
          <MenuItem value="">Todos</MenuItem>
          {types.map((t) => (
            <MenuItem key={t.code} value={t.code}>
              {t.name}
            </MenuItem>
          ))}
        </TextField>
        <Box sx={{ flex: 1 }} />
        {canEdit && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={onCreate}>
            Nueva evidencia
          </Button>
        )}
      </Stack>

      <QueryBoundary isLoading={isLoading} error={error}>
        {(data ?? []).length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 3 }}>
            No hay evidencias registradas con este filtro. Regístralas una sola vez y se sugerirán en el checklist de cada auditoría.
          </Typography>
        ) : (
          <Box sx={{ overflowX: 'auto' }}>
            <Table size="small" aria-label="Evidencias registradas">
              <TableHead>
                <TableRow>
                  <TableCell>Evidencia</TableCell>
                  <TableCell>Explotación</TableCell>
                  <TableCell>Documento</TableCell>
                  <TableCell>Vigencia</TableCell>
                  <TableCell align="right">Docs</TableCell>
                  <TableCell align="right">Usos</TableCell>
                  <TableCell align="right" />
                </TableRow>
              </TableHead>
              <TableBody>
                {(data ?? []).map((r) => (
                  <TableRow key={r.id} hover sx={{ cursor: 'pointer' }} onClick={() => onOpen(r)}>
                    <TableCell>
                      <Typography sx={{ fontWeight: 600 }}>{r.title}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {r.typeName}
                      </Typography>
                    </TableCell>
                    <TableCell>{r.farmName ?? '—'}</TableCell>
                    <TableCell>{formatDate(r.issuedOn)}</TableCell>
                    <TableCell>
                      <Stack direction="row" spacing={0.75} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                        <EvidenceStatusChip status={r.status} />
                        <Typography variant="caption" color="text.secondary">
                          {r.validUntil ? `hasta ${formatDate(r.validUntil)}` : 'sin caducidad'}
                        </Typography>
                        {r.outcome === 'UNFAVORABLE' && (
                          <Chip size="small" color="error" variant="outlined" label={evidenceOutcomeLabels.UNFAVORABLE} />
                        )}
                      </Stack>
                    </TableCell>
                    <TableCell align="right">{r.documents}</TableCell>
                    <TableCell align="right">{r.usedIn}</TableCell>
                    <TableCell align="right" onClick={(e) => e.stopPropagation()}>
                      {canEdit && (
                        <Tooltip title="Editar">
                          <IconButton size="small" aria-label={`Editar ${r.title}`} onClick={() => onEdit(r)}>
                            <EditOutlinedIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                      {canDelete && (
                        <Tooltip title={r.usedIn > 0 ? 'Se usa en auditorías: quita antes los vínculos' : 'Eliminar'}>
                          <span>
                            <IconButton
                              size="small"
                              aria-label={`Eliminar ${r.title}`}
                              disabled={r.usedIn > 0}
                              onClick={() => askRemove(r)}
                            >
                              <DeleteOutlineIcon fontSize="small" />
                            </IconButton>
                          </span>
                        </Tooltip>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
        )}
      </QueryBoundary>
    </Box>
  );
}
