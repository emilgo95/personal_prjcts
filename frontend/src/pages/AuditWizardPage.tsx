import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Grid,
  MenuItem,
  Stack,
  Step,
  StepLabel,
  Stepper,
  TextField,
  Typography,
} from '@mui/material';
import { AuditApi, CatalogApi, FarmApi } from '../api/endpoints';
import type { AuditRequest } from '../api/types';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import { useToast } from '../utils/toast';
import { extractErrorMessage } from '../api/client';
import { auditTypeLabels } from '../utils/labels';

const steps = ['Datos de la auditoría', 'Checklist', 'Hallazgos y CAPA', 'Documentos'];

export default function AuditWizardPage() {
  const navigate = useNavigate();
  const { notify } = useToast();

  const farms = useQuery({ queryKey: ['farms'], queryFn: FarmApi.list });
  const standards = useQuery({ queryKey: ['standards'], queryFn: CatalogApi.standards });

  const [form, setForm] = useState<AuditRequest>({
    farmId: 0,
    standardCode: '',
    auditType: 'SELF_ASSESSMENT',
    scopeDescription: '',
    plannedStartDate: '',
    plannedEndDate: '',
    actualDate: '',
    leadAuditor: '',
    auditTeam: '',
    siteAddress: '',
    productsInScope: '',
  });

  const create = useMutation({
    mutationFn: (body: AuditRequest) => AuditApi.create(body),
    onSuccess: (audit) => {
      notify(`Auditoría ${audit.reference} creada`, 'success');
      navigate(`/audits/${audit.id}`);
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const set =
    (key: keyof AuditRequest) =>
    (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
      setForm((f) => ({ ...f, [key]: e.target.value }));

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!form.farmId || !form.standardCode) {
      notify('Selecciona una explotación y un estándar', 'warning');
      return;
    }
    const payload: AuditRequest = {
      ...form,
      plannedStartDate: form.plannedStartDate || null,
      plannedEndDate: form.plannedEndDate || null,
      actualDate: form.actualDate || null,
    };
    create.mutate(payload);
  };

  return (
    <Box>
      <PageHeader title="Nueva auditoría" />
      <Stepper activeStep={0} sx={{ mb: 3 }}>
        {steps.map((s) => (
          <Step key={s}>
            <StepLabel>{s}</StepLabel>
          </Step>
        ))}
      </Stepper>

      <Alert severity="info" sx={{ mb: 2 }}>
        Tras crear la auditoría podrás rellenar el checklist, registrar no conformidades y generar los documentos
        desde la ficha de la auditoría.
      </Alert>

      <QueryBoundary isLoading={farms.isLoading || standards.isLoading} error={farms.error || standards.error}>
        <Card>
          <CardContent>
            <form onSubmit={onSubmit}>
              <Grid container spacing={2}>
                <Grid item xs={12} md={6}>
                  <TextField
                    select
                    required
                    fullWidth
                    label="Explotación / empresa"
                    value={form.farmId || ''}
                    onChange={(e) => setForm((f) => ({ ...f, farmId: Number(e.target.value) }))}
                  >
                    {(farms.data ?? []).map((farm) => (
                      <MenuItem key={farm.id} value={farm.id}>
                        {farm.legalName}
                      </MenuItem>
                    ))}
                    {farms.data?.length === 0 && <MenuItem disabled value="">Crea primero una explotación</MenuItem>}
                  </TextField>
                </Grid>
                <Grid item xs={12} md={6}>
                  <TextField
                    select
                    required
                    fullWidth
                    label="Estándar de auditoría"
                    value={form.standardCode}
                    onChange={(e) => setForm((f) => ({ ...f, standardCode: e.target.value }))}
                  >
                    {(standards.data ?? []).map((s) => (
                      <MenuItem key={s.code} value={s.code}>
                        {s.name} {s.version ? `(${s.version})` : ''}
                      </MenuItem>
                    ))}
                  </TextField>
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField
                    select
                    fullWidth
                    label="Tipo de auditoría"
                    value={form.auditType}
                    onChange={(e) => setForm((f) => ({ ...f, auditType: e.target.value }))}
                  >
                    {Object.entries(auditTypeLabels).map(([k, v]) => (
                      <MenuItem key={k} value={k}>
                        {v}
                      </MenuItem>
                    ))}
                  </TextField>
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField
                    label="Fecha prevista de inicio"
                    type="date"
                    fullWidth
                    InputLabelProps={{ shrink: true }}
                    value={form.plannedStartDate ?? ''}
                    onChange={set('plannedStartDate')}
                  />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField
                    label="Fecha de auditoría"
                    type="date"
                    fullWidth
                    InputLabelProps={{ shrink: true }}
                    value={form.actualDate ?? ''}
                    onChange={set('actualDate')}
                  />
                </Grid>
                <Grid item xs={12} md={6}>
                  <TextField label="Auditor responsable" fullWidth value={form.leadAuditor ?? ''} onChange={set('leadAuditor')} />
                </Grid>
                <Grid item xs={12} md={6}>
                  <TextField label="Equipo auditor" fullWidth value={form.auditTeam ?? ''} onChange={set('auditTeam')} />
                </Grid>
                <Grid item xs={12}>
                  <TextField label="Emplazamiento auditado" fullWidth value={form.siteAddress ?? ''} onChange={set('siteAddress')} />
                </Grid>
                <Grid item xs={12}>
                  <TextField label="Productos en alcance" fullWidth value={form.productsInScope ?? ''} onChange={set('productsInScope')} />
                </Grid>
                <Grid item xs={12}>
                  <TextField
                    label="Descripción del alcance"
                    fullWidth
                    multiline
                    minRows={2}
                    value={form.scopeDescription ?? ''}
                    onChange={set('scopeDescription')}
                  />
                </Grid>
              </Grid>

              <Stack direction="row" spacing={1} mt={3}>
                <Button type="submit" variant="contained" disabled={create.isPending}>
                  Crear auditoría
                </Button>
                <Button onClick={() => navigate('/audits')}>Cancelar</Button>
              </Stack>
            </form>
          </CardContent>
        </Card>
      </QueryBoundary>
    </Box>
  );
}
