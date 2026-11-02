import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
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
import { CatalogApi, EvidenceApi } from '../api/endpoints';
import type { EvidenceMatrixEntry } from '../api/types';
import { extractErrorMessage } from '../api/client';
import QueryBoundary from '../components/QueryBoundary';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../utils/toast';
import { useConfirm } from '../utils/confirm';

/** Matriz de equivalencias: qué tipo de evidencia sirve para qué punto de cada norma. Borrador hasta que se valida. */
export default function MatrixTab() {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const confirm = useConfirm();
  const { hasRole } = useAuth();
  const canEdit = hasRole('ADMIN', 'QUALITY');
  const { data, isLoading, error } = useQuery({ queryKey: ['evidence-matrix'], queryFn: EvidenceApi.matrix });
  const [adding, setAdding] = useState(false);

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['evidence-matrix'] });
    queryClient.invalidateQueries({ queryKey: ['evidence-coverage'] });
    queryClient.invalidateQueries({ queryKey: ['evidence-health'] });
  };

  const validate = useMutation({
    mutationFn: (v: { id: number; validated: boolean }) => EvidenceApi.validateEquivalence(v.id, v.validated),
    onSuccess: refresh,
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });
  const validateAll = useMutation({
    mutationFn: async (ids: number[]) => {
      for (const id of ids) await EvidenceApi.validateEquivalence(id, true);
    },
    onSuccess: () => {
      notify('Equivalencias validadas', 'success');
      refresh();
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });
  const remove = useMutation({
    mutationFn: (id: number) => EvidenceApi.removeEquivalence(id),
    onSuccess: refresh,
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const grouped = useMemo(() => {
    const map = new Map<string, EvidenceMatrixEntry[]>();
    for (const e of data?.entries ?? []) {
      map.set(e.typeCode, [...(map.get(e.typeCode) ?? []), e]);
    }
    return map;
  }, [data]);
  const drafts = (data?.entries ?? []).filter((e) => e.status === 'DRAFT');

  const askRemove = async (e: EvidenceMatrixEntry) => {
    const ok = await confirm({
      title: 'Quitar equivalencia',
      message: `¿Quitar «${e.typeName}» del punto ${e.controlRef} de ${e.standardName ?? e.standardCode}?`,
      confirmLabel: 'Quitar',
      tone: 'warning',
    });
    if (ok) remove.mutate(e.id);
  };

  return (
    <QueryBoundary isLoading={isLoading} error={error}>
      {data && (
        <Stack spacing={2}>
          {drafts.length > 0 ? (
            <Alert
              severity="warning"
              action={
                canEdit ? (
                  <Button color="inherit" size="small" disabled={validateAll.isPending} onClick={() => validateAll.mutate(drafts.map((d) => d.id))}>
                    Validar todas
                  </Button>
                ) : undefined
              }
            >
              {drafts.length} equivalencia(s) son un <strong>borrador a validar</strong>: se dedujeron de las «evidencias de ejemplo» de
              cada punto y deben ser revisadas por el responsable de calidad antes de fiarse de ellas.
            </Alert>
          ) : (
            <Alert severity="success">Todas las equivalencias están validadas.</Alert>
          )}
          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography variant="body2" color="text.secondary">
              Cada tipo de evidencia sirve para los puntos listados. La aplicación solo sugiere: una persona confirma cada vínculo.
            </Typography>
            {canEdit && (
              <Button startIcon={<AddIcon />} onClick={() => setAdding(true)}>
                Añadir equivalencia
              </Button>
            )}
          </Stack>

          {data.types
            .filter((t) => grouped.has(t.code))
            .map((t) => (
              <Box key={t.code}>
                <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                  {t.name}
                </Typography>
                <Table size="small" aria-label={`Equivalencias de ${t.name}`}>
                  <TableHead>
                    <TableRow>
                      <TableCell>Norma</TableCell>
                      <TableCell>Punto</TableCell>
                      <TableCell>Estado</TableCell>
                      <TableCell align="right" />
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(grouped.get(t.code) ?? []).map((e) => (
                      <TableRow key={e.id}>
                        <TableCell>{e.standardName ?? e.standardCode}</TableCell>
                        <TableCell>
                          <strong>{e.controlRef}</strong> {e.controlTitle}
                        </TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            color={e.status === 'VALIDATED' ? 'success' : 'warning'}
                            variant="outlined"
                            label={e.status === 'VALIDATED' ? 'Validada' : 'Borrador'}
                          />
                        </TableCell>
                        <TableCell align="right">
                          {canEdit && (
                            <>
                              <Button size="small" onClick={() => validate.mutate({ id: e.id, validated: e.status !== 'VALIDATED' })}>
                                {e.status === 'VALIDATED' ? 'Volver a borrador' : 'Validar'}
                              </Button>
                              <Tooltip title="Quitar">
                                <IconButton size="small" aria-label={`Quitar ${e.controlRef}`} onClick={() => askRemove(e)}>
                                  <DeleteOutlineIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            </>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            ))}
        </Stack>
      )}
      <AddEquivalenceDialog
        open={adding}
        onClose={() => setAdding(false)}
        types={data?.types ?? []}
        onAdded={() => {
          notify('Equivalencia añadida', 'success');
          refresh();
        }}
      />
    </QueryBoundary>
  );
}

function AddEquivalenceDialog({
  open,
  onClose,
  types,
  onAdded,
}: {
  open: boolean;
  onClose: () => void;
  types: { code: string; name: string }[];
  onAdded: () => void;
}) {
  const { notify } = useToast();
  const [typeCode, setTypeCode] = useState('');
  const [standardCode, setStandardCode] = useState('');
  const [controlRef, setControlRef] = useState('');
  const standards = useQuery({ queryKey: ['standards'], queryFn: () => CatalogApi.standards(), enabled: open });
  const detail = useQuery({
    queryKey: ['standard', standardCode],
    queryFn: () => CatalogApi.standard(standardCode),
    enabled: open && standardCode !== '',
  });
  const controls = (detail.data?.chapters ?? []).flatMap((c) => c.controlPoints);

  const add = useMutation({
    mutationFn: () => EvidenceApi.addEquivalence({ typeCode, standardCode, controlRef }),
    onSuccess: () => {
      onAdded();
      onClose();
      setControlRef('');
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Añadir equivalencia</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField select label="Tipo de evidencia" value={typeCode} onChange={(e) => setTypeCode(e.target.value)}>
            {types.map((t) => (
              <MenuItem key={t.code} value={t.code}>
                {t.name}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Norma"
            value={standardCode}
            onChange={(e) => {
              setStandardCode(e.target.value);
              setControlRef('');
            }}
          >
            {(standards.data ?? []).map((s) => (
              <MenuItem key={s.code} value={s.code}>
                {s.name}
              </MenuItem>
            ))}
          </TextField>
          <TextField select label="Punto de control" value={controlRef} disabled={!standardCode} onChange={(e) => setControlRef(e.target.value)}>
            {controls.map((c) => (
              <MenuItem key={c.id} value={c.ref}>
                {c.ref} · {c.title}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancelar</Button>
        <Button variant="contained" disabled={!typeCode || !standardCode || !controlRef || add.isPending} onClick={() => add.mutate()}>
          Añadir
        </Button>
      </DialogActions>
    </Dialog>
  );
}
