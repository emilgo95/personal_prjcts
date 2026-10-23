import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Card,
  CardActionArea,
  CardContent,
  Chip,
  Grid,
  Link,
  Stack,
  Typography,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { CatalogApi } from '../api/endpoints';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import { label, levelLabels } from '../utils/labels';

export default function StandardsPage() {
  const standards = useQuery({ queryKey: ['standards'], queryFn: CatalogApi.standards });
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
                  <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
                    <Chip size="small" label={`${s.chapterCount} capítulos`} />
                    <Chip size="small" label={`${s.controlPointCount} puntos`} />
                  </Stack>
                </CardContent>
              </CardActionArea>
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
