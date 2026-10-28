import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Box, Button, Stack, Typography } from '@mui/material';
import { TraceApi } from '../api/endpoints';
import QueryBoundary from '../components/QueryBoundary';
import TraceTimeline from '../components/TraceTimeline';

/** Historial de cambios de una auditoría (quién cambió qué y cuándo), de lo más reciente a lo más antiguo. */
export default function HistoryTab({ auditId }: { auditId: number }) {
  const [page, setPage] = useState(0);
  const history = useQuery({
    queryKey: ['audit', auditId, 'trace', page],
    queryFn: () => TraceApi.forAudit(auditId, page, 50),
  });
  const data = history.data;

  return (
    <Box>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Registro inalterable de los cambios de esta auditoría. Despliega cada entrada para ver el valor anterior y
        el nuevo.
      </Typography>
      <QueryBoundary isLoading={history.isLoading} error={history.error}>
        <TraceTimeline entries={data?.content ?? []} />
        {data && data.totalPages > 1 && (
          <Stack direction="row" spacing={1} sx={{ mt: 2, alignItems: 'center' }}>
            <Button size="small" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              Más recientes
            </Button>
            <Typography variant="caption">
              Página {page + 1} de {data.totalPages}
            </Typography>
            <Button size="small" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
              Más antiguos
            </Button>
          </Stack>
        )}
      </QueryBoundary>
    </Box>
  );
}
