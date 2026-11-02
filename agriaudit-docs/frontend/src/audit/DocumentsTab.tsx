import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
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
import DownloadIcon from '@mui/icons-material/Download';
import { DocumentApi, downloadBlob } from '../api/endpoints';
import QueryBoundary from '../components/QueryBoundary';
import { useToast } from '../utils/toast';
import { formatDateTime } from '../utils/dates';
import { extractErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';

export default function DocumentsTab({ auditId }: { auditId: number }) {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const { hasRole } = useAuth();
  const canGenerate = hasRole('ADMIN', 'AUDITOR', 'QUALITY');

  const options = useQuery({
    queryKey: ['audit', auditId, 'doc-options'],
    queryFn: () => DocumentApi.options(auditId),
  });
  const docs = useQuery({
    queryKey: ['audit', auditId, 'documents'],
    queryFn: () => DocumentApi.listForAudit(auditId),
  });

  const [type, setType] = useState('');
  const [format, setFormat] = useState('');

  const selectedOption = options.data?.find((o) => o.documentType === type);

  const generate = useMutation({
    mutationFn: () => DocumentApi.generate(auditId, type, format),
    onSuccess: () => {
      notify('Documento generado', 'success');
      queryClient.invalidateQueries({ queryKey: ['audit', auditId, 'documents'] });
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const download = useMutation({
    mutationFn: async (id: number) => {
      const blob = await DocumentApi.download(id);
      const doc = docs.data?.find((d) => d.id === id);
      downloadBlob(blob, doc?.fileName ?? `documento-${id}`);
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  return (
    <Box>
      {canGenerate && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Generar documento
            </Typography>
            <QueryBoundary isLoading={options.isLoading} error={options.error}>
              <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ alignItems: { md: 'center' } }}>
                <TextField
                  select
                  label="Tipo de documento"
                  sx={{ minWidth: 280 }}
                  value={type}
                  onChange={(e) => {
                    setType(e.target.value);
                    setFormat('');
                  }}
                >
                  {(options.data ?? []).map((o) => (
                    <MenuItem key={o.documentType} value={o.documentType}>
                      {o.label}
                    </MenuItem>
                  ))}
                </TextField>
                <TextField
                  select
                  label="Formato"
                  sx={{ minWidth: 140 }}
                  value={format}
                  disabled={!selectedOption}
                  onChange={(e) => setFormat(e.target.value)}
                >
                  {(selectedOption?.formats ?? []).map((f) => (
                    <MenuItem key={f} value={f}>
                      {f}
                    </MenuItem>
                  ))}
                </TextField>
                <Button
                  variant="contained"
                  disabled={!type || !format || generate.isPending}
                  onClick={() => generate.mutate()}
                >
                  {generate.isPending ? 'Generando…' : 'Generar'}
                </Button>
              </Stack>
            </QueryBoundary>
          </CardContent>
        </Card>
      )}

      <QueryBoundary isLoading={docs.isLoading} error={docs.error}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Documentos generados
            </Typography>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Documento</TableCell>
                  <TableCell>Formato</TableCell>
                  <TableCell>Generado</TableCell>
                  <TableCell>Tamaño</TableCell>
                  <TableCell align="right">Descargar</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(docs.data ?? []).map((d) => (
                  <TableRow key={d.id} hover>
                    <TableCell>{d.documentTypeLabel}</TableCell>
                    <TableCell>
                      <Chip size="small" label={d.format} />
                    </TableCell>
                    <TableCell>
                      {formatDateTime(d.generatedAt)} · {d.generatedBy}
                    </TableCell>
                    <TableCell>{d.sizeBytes ? `${Math.round(d.sizeBytes / 1024)} KB` : '—'}</TableCell>
                    <TableCell align="right">
                      <Button
                        size="small"
                        startIcon={<DownloadIcon />}
                        onClick={() => download.mutate(d.id)}
                      >
                        Descargar
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
                {docs.data?.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5}>
                      <Typography color="text.secondary" variant="body2">
                        Todavía no se ha generado ningún documento para esta auditoría.
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </QueryBoundary>
    </Box>
  );
}
