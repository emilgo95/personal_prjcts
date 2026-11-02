import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Chip,
  Link,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { CatalogApi } from '../api/endpoints';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';

export default function RegulationsPage() {
  const { data, isLoading, error } = useQuery({ queryKey: ['regulations'], queryFn: CatalogApi.regulations });
  const [q, setQ] = useState('');

  const filtered = useMemo(() => {
    const term = q.trim().toLowerCase();
    if (!term) return data ?? [];
    return (data ?? []).filter((r) =>
      [r.title, r.code, r.jurisdiction, r.scopeArea, r.summary].some((v) =>
        (v ?? '').toLowerCase().includes(term),
      ),
    );
  }, [data, q]);

  return (
    <Box>
      <PageHeader
        title="Normativa aplicable"
        subtitle="Reglamentos, directivas y convenios que deben cumplir las auditorías del sector agrícola"
      />
      <TextField
        fullWidth
        placeholder="Buscar por título, jurisdicción o ámbito…"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        sx={{ mb: 2 }}
      />
      <QueryBoundary isLoading={isLoading} error={error}>
        {filtered.map((r) => (
          <Accordion key={r.code}>
            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
              <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexGrow: 1 }}>
                <Typography sx={{ fontWeight: 600 }}>{r.title}</Typography>
              </Stack>
              <Stack direction="row" spacing={1}>
                {r.jurisdiction && <Chip size="small" label={r.jurisdiction} />}
                {r.scopeArea && <Chip size="small" variant="outlined" label={r.scopeArea} />}
              </Stack>
            </AccordionSummary>
            <AccordionDetails>
              {r.summary && <Typography variant="body2" sx={{ mb: 2 }}>{r.summary}</Typography>}
              {r.keyObligations && (
                <Typography variant="body2" sx={{ mb: 2 }}>
                  <b>Obligaciones clave:</b> {r.keyObligations}
                </Typography>
              )}
              {r.sourceUrl && (
                <Link href={r.sourceUrl} target="_blank" rel="noopener">
                  Fuente oficial
                </Link>
              )}
            </AccordionDetails>
          </Accordion>
        ))}
      </QueryBoundary>
    </Box>
  );
}
