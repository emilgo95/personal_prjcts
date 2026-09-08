import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Box, Button, Card, CardContent, Grid, Stack, TextField } from '@mui/material';
import { FarmApi } from '../api/endpoints';
import type { FarmRequest } from '../api/types';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import { useToast } from '../utils/toast';
import { extractErrorMessage } from '../api/client';

const empty: FarmRequest = {
  legalName: '',
  tradeName: '',
  registrationNumber: '',
  address: '',
  city: '',
  region: '',
  country: '',
  latitude: null,
  longitude: null,
  totalHectares: null,
  crops: '',
  productionAreas: '',
  contactName: '',
  contactEmail: '',
  contactPhone: '',
  notes: '',
};

export default function FarmFormPage() {
  const { id } = useParams();
  const editing = Boolean(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const [form, setForm] = useState<FarmRequest>(empty);

  const query = useQuery({
    queryKey: ['farm', id],
    queryFn: () => FarmApi.get(Number(id)),
    enabled: editing,
  });

  useEffect(() => {
    if (query.data) {
      const { id: _omit, ...rest } = query.data;
      setForm({ ...empty, ...rest });
    }
  }, [query.data]);

  const save = useMutation({
    mutationFn: (body: FarmRequest) =>
      editing ? FarmApi.update(Number(id), body) : FarmApi.create(body),
    onSuccess: () => {
      notify('Explotación guardada', 'success');
      queryClient.invalidateQueries({ queryKey: ['farms'] });
      navigate('/farms');
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const set = (key: keyof FarmRequest) => (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value;
    const numeric = ['latitude', 'longitude', 'totalHectares'].includes(key);
    setForm((f) => ({ ...f, [key]: numeric ? (raw === '' ? null : Number(raw)) : raw }));
  };

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate(form);
  };

  return (
    <Box>
      <PageHeader title={editing ? 'Editar explotación' : 'Nueva explotación'} />
      <QueryBoundary isLoading={editing && query.isLoading} error={query.error}>
        <Card>
          <CardContent>
            <form onSubmit={onSubmit}>
              <Grid container spacing={2}>
                <Grid item xs={12} md={6}>
                  <TextField label="Razón social" required fullWidth value={form.legalName} onChange={set('legalName')} />
                </Grid>
                <Grid item xs={12} md={6}>
                  <TextField label="Nombre comercial" fullWidth value={form.tradeName ?? ''} onChange={set('tradeName')} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField label="GGN / Nº de registro" fullWidth value={form.registrationNumber ?? ''} onChange={set('registrationNumber')} />
                </Grid>
                <Grid item xs={12} md={8}>
                  <TextField label="Dirección" fullWidth value={form.address ?? ''} onChange={set('address')} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField label="Localidad" fullWidth value={form.city ?? ''} onChange={set('city')} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField label="Región / Provincia" fullWidth value={form.region ?? ''} onChange={set('region')} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField label="País" fullWidth value={form.country ?? ''} onChange={set('country')} />
                </Grid>
                <Grid item xs={6} md={3}>
                  <TextField label="Latitud" type="number" fullWidth value={form.latitude ?? ''} onChange={set('latitude')} />
                </Grid>
                <Grid item xs={6} md={3}>
                  <TextField label="Longitud" type="number" fullWidth value={form.longitude ?? ''} onChange={set('longitude')} />
                </Grid>
                <Grid item xs={12} md={3}>
                  <TextField label="Hectáreas totales" type="number" fullWidth value={form.totalHectares ?? ''} onChange={set('totalHectares')} />
                </Grid>
                <Grid item xs={12} md={6}>
                  <TextField label="Cultivos (separados por comas)" fullWidth value={form.crops ?? ''} onChange={set('crops')} />
                </Grid>
                <Grid item xs={12} md={6}>
                  <TextField label="Áreas de producción" fullWidth value={form.productionAreas ?? ''} onChange={set('productionAreas')} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField label="Persona de contacto" fullWidth value={form.contactName ?? ''} onChange={set('contactName')} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField label="Email de contacto" fullWidth value={form.contactEmail ?? ''} onChange={set('contactEmail')} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField label="Teléfono de contacto" fullWidth value={form.contactPhone ?? ''} onChange={set('contactPhone')} />
                </Grid>
                <Grid item xs={12}>
                  <TextField label="Notas" fullWidth multiline minRows={2} value={form.notes ?? ''} onChange={set('notes')} />
                </Grid>
              </Grid>
              <Stack direction="row" spacing={1} mt={3}>
                <Button type="submit" variant="contained" disabled={save.isPending}>
                  Guardar
                </Button>
                <Button onClick={() => navigate('/farms')}>Cancelar</Button>
              </Stack>
            </form>
          </CardContent>
        </Card>
      </QueryBoundary>
    </Box>
  );
}
