import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Box, Button, Card, CardContent, Stack, TextField, Typography } from '@mui/material';
import { PrivacyApi, downloadBlob } from '../api/endpoints';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import { useToast } from '../utils/toast';
import { extractErrorMessage } from '../api/client';
import { formatDateTime } from '../utils/dates';

export default function PrivacyPage() {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const { data, isLoading, error } = useQuery({ queryKey: ['privacy'], queryFn: PrivacyApi.get });
  const [months, setMonths] = useState('');

  useEffect(() => {
    if (data) setMonths(data.organizationValue != null ? String(data.organizationValue) : '');
  }, [data]);

  const save = useMutation({
    mutationFn: () => PrivacyApi.setRetention(months.trim() === '' ? null : Number(months)),
    onSuccess: () => {
      notify('Plazo de conservación guardado', 'success');
      queryClient.invalidateQueries({ queryKey: ['privacy'] });
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const exportAll = useMutation({
    mutationFn: PrivacyApi.exportAll,
    onSuccess: (blob) => downloadBlob(blob, `datos-organizacion-${new Date().toISOString().slice(0, 10)}.zip`),
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate();
  };

  return (
    <Box>
      <PageHeader
        title="Privacidad"
        subtitle="Conservación de datos bloqueados y exportación de los datos de la organización"
      />
      <QueryBoundary isLoading={isLoading} error={error}>
        {data && (
          <Stack spacing={3}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Plazo de conservación de datos bloqueados
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  Al bloquear a un usuario su nombre y correo dejan de mostrarse, pero se reservan sin tratarse hasta
                  que venza este plazo; después se destruyen automáticamente. El plazo lo decide la organización
                  (responsable del tratamiento); déjelo vacío para usar el valor por defecto ({data.defaultMonths}{' '}
                  meses).
                </Typography>
                <form onSubmit={onSubmit}>
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: 'flex-start' }}>
                    <TextField
                      label="Meses de conservación"
                      type="number"
                      value={months}
                      onChange={(e) => setMonths(e.target.value)}
                      helperText={`Entre ${data.minMonths} y ${data.maxMonths}. En vigor: ${data.blockedRetentionMonths} meses`}
                      slotProps={{ htmlInput: { min: data.minMonths, max: data.maxMonths } }}
                    />
                    <Button type="submit" variant="contained" disabled={save.isPending} sx={{ mt: 1 }}>
                      Guardar
                    </Button>
                  </Stack>
                </form>
                <Typography variant="body2" sx={{ mt: 2 }}>
                  Usuarios con datos bloqueados: <strong>{data.blockedCount}</strong>
                  {data.nextDestruction && <> · Próxima destrucción: {formatDateTime(data.nextDestruction)}</>}
                </Typography>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Exportar todos los datos de la organización
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  Descarga un ZIP con los datos (JSON), los adjuntos, los documentos generados y la traza de cambios
                  con sus huellas. No incluye contraseñas ni los datos de usuarios bloqueados. Queda constancia de la
                  descarga en la traza.
                </Typography>
                <Button variant="outlined" onClick={() => exportAll.mutate()} disabled={exportAll.isPending}>
                  {exportAll.isPending ? 'Preparando…' : 'Descargar exportación (ZIP)'}
                </Button>
              </CardContent>
            </Card>

            <Alert severity="info">
              Los plazos de conservación y su encaje legal deben validarse con un especialista en protección de datos.
            </Alert>
          </Stack>
        )}
      </QueryBoundary>
    </Box>
  );
}
