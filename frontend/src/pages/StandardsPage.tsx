import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Card,
  CardActionArea,
  CardContent,
  Chip,
  FormControlLabel,
  Grid,
  Link,
  Stack,
  Switch,
  Typography,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { CatalogApi } from '../api/endpoints';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import StandardImportDialog from '../components/StandardImportDialog';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../utils/toast';
import { extractErrorMessage } from '../api/client';
import { label, levelLabels } from '../utils/labels';

export default function StandardsPage() {
  const { hasRole } = useAuth();
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const canManage = hasRole('ADMIN', 'QUALITY');
  const [showAll, setShowAll] = useState(false);
  const [importing, setImporting] = useState(false);
  const standards = useQuery({
    queryKey: ['standards', showAll],
    queryFn: () => CatalogApi.standards(canManage && showAll),
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['standards'] });
  const toggle = useMutation({
    mutationFn: (v: { code: string; custom: boolean; value: boolean }) =>
      v.custom ? CatalogApi.setArchived(v.code, v.value) : CatalogApi.setHidden(v.code, v.value),
    onSuccess: refresh,
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });
  const [selected, setSelected] = useState<string | null>(null);
  const detail = useQuery({
    queryKey: ['standard', selected],
    queryFn: () => CatalogApi.standard(selected!),
    enabled: Boolean(selected),
  });

  return (
    <Box>
      <PageHeader
        title="Estándares de auditoría"
        subtitle="Catálogo de estándares del sector agrícola y sus puntos de control (resumen divulgativo)"
        action={
          canManage && (
            <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
              <FormControlLabel
                control={<Switch checked={showAll} onChange={(e) => setShowAll(e.target.checked)} />}
                label="Mostrar ocultos y archivados"
              />
              <Button variant="contained" onClick={() => setImporting(true)}>
                Importar listado propio
              </Button>
            </Stack>
          )
        }
      />
      <StandardImportDialog
        open={importing}
        onClose={() => setImporting(false)}
        onImported={(code) => {
          setImporting(false);
          notify('Estándar importado', 'success');
          refresh();
          setSelected(code);
        }}
      />

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {(standards.data ?? []).map((s) => (
          <Grid size={{ xs: 12, md: 4 }} key={s.code}>
            <Card variant={selected === s.code ? 'elevation' : 'outlined'}>
              <CardActionArea onClick={() => setSelected(s.code)}>
                <CardContent>
                  <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                    {s.name}
                  </Typography>
                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    {s.ownerBody} · {s.version}
                  </Typography>
                  <Typography variant="body2">{s.description}</Typography>
                  <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: 'wrap' }} useFlexGap>
                    <Chip size="small" label={`${s.chapterCount} capítulos`} />
                    <Chip size="small" label={`${s.controlPointCount} puntos`} />
                    <Chip
                      size="small"
                      color={s.custom ? 'primary' : 'default'}
                      variant="outlined"
                      label={s.custom ? 'Propio' : 'Demostración'}
                    />
                    {s.hidden && <Chip size="small" color="warning" label="Oculto" />}
                    {s.archived && <Chip size="small" color="warning" label="Archivado" />}
                  </Stack>
                </CardContent>
              </CardActionArea>
              {canManage && (
                <Box sx={{ px: 2, pb: 1 }}>
                  <Button
                    size="small"
                    disabled={toggle.isPending}
                    onClick={() => {
                      const turnOn = !(s.custom ? s.archived : s.hidden);
                      toggle.mutate({ code: s.code, custom: s.custom, value: turnOn });
                    }}
                  >
                    {s.custom ? (s.archived ? 'Recuperar' : 'Archivar') : s.hidden ? 'Volver a mostrar' : 'Ocultar'}
                  </Button>
                </Box>
              )}
            </Card>
          </Grid>
        ))}
      </Grid>

      {selected && (
        <QueryBoundary isLoading={detail.isLoading} error={detail.error}>
          {detail.data && (
            <Box>
              <Stack direction="row" spacing={1} sx={{ alignItems: "center", mb: 1 }}>
                <Typography variant="h5">{detail.data.name}</Typography>
                {detail.data.officialUrl && (
                  <Link href={detail.data.officialUrl} target="_blank" rel="noopener">
                    Fuente oficial
                  </Link>
                )}
              </Stack>
              {detail.data.chapters.map((ch) => (
                <Accordion key={ch.id}>
                  <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                    <Typography sx={{ fontWeight: 600 }}>
                      {ch.code} · {ch.title}
                    </Typography>
                  </AccordionSummary>
                  <AccordionDetails>
                    {ch.description && (
                      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                        {ch.description}
                      </Typography>
                    )}
                    <Stack spacing={2}>
                      {ch.controlPoints.map((cp) => (
                        <Box key={cp.id}>
                          <Typography sx={{ fontWeight: 700 }}>
                            {cp.ref} · {cp.title}{' '}
                            <Chip size="small" variant="outlined" label={label(levelLabels, cp.level)} />
                          </Typography>
                          {cp.description && <Typography variant="body2">{cp.description}</Typography>}
                          {cp.guidance && (
                            <Typography variant="body2" color="text.secondary">
                              <b>Orientación:</b> {cp.guidance}
                            </Typography>
                          )}
                          {cp.evidenceExamples && (
                            <Typography variant="body2" color="text.secondary">
                              <b>Evidencias habituales:</b> {cp.evidenceExamples}
                            </Typography>
                          )}
                          {cp.regulations.length > 0 && (
                            <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 0.5, flexWrap: "wrap" }}>
                              {cp.regulations.map((r) => (
                                <Chip
                                  key={r.code}
                                  size="small"
                                  label={r.title}
                                  component={r.sourceUrl ? 'a' : 'div'}
                                  href={r.sourceUrl ?? undefined}
                                  target="_blank"
                                  clickable={Boolean(r.sourceUrl)}
                                />
                              ))}
                            </Stack>
                          )}
                        </Box>
                      ))}
                    </Stack>
                  </AccordionDetails>
                </Accordion>
              ))}
              <Alert severity="warning" sx={{ mt: 3 }}>
                Los textos son un resumen elaborado a partir de fuentes públicas y no reproducen literalmente los
                documentos oficiales de los estándares, que están sujetos a derechos de autor. Consulte siempre la
                versión oficial vigente.
              </Alert>
            </Box>
          )}
        </QueryBoundary>
      )}
    </Box>
  );
}
