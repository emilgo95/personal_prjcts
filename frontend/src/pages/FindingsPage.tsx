import { useDeferredValue, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Box, Chip, IconButton, InputAdornment, Stack, TextField, Tooltip, Typography } from '@mui/material';
import { DataGrid, type GridColDef } from '@mui/x-data-grid';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import { FindingApi } from '../api/endpoints';
import type { FindingRow, FindingView } from '../api/types';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import DeadlineChip from '../components/DeadlineChip';
import { classificationColors, classificationLabels, findingStatusColors, findingStatusLabels, label } from '../utils/labels';
import { normalizeText } from '../utils/search';
import { gridLocaleText } from '../utils/gridLocale';
import { findingLink } from '../utils/links';

/** Vistas en la dirección (?vista=) con su nombre en pantalla; el panel enlaza a ellas. */
export const findingViews: Array<{ param: string; view: FindingView; label: string }> = [
  { param: 'abiertas', view: 'OPEN', label: 'Abiertas' },
  { param: 'vencidas', view: 'OVERDUE', label: 'Vencidas' },
  { param: '2-dias', view: 'DUE_2', label: 'Vencen en 2 días' },
  { param: '7-dias', view: 'DUE_7', label: 'Vencen en 7 días' },
  { param: '14-dias', view: 'DUE_14', label: 'Vencen en 14 días' },
  { param: 'cerradas', view: 'CLOSED_12M', label: 'Cerradas (12 meses)' },
  { param: 'cerradas-tarde', view: 'CLOSED_LATE', label: 'Cerradas tarde' },
  { param: 'todas', view: 'ALL', label: 'Todas' },
];

function rowText(r: FindingRow) {
  return normalizeText(
    [r.reference, r.title, r.auditReference, r.farmName, r.controlPointRef, r.standardName].filter(Boolean).join(' '),
  );
}

/**
 * Auditorías › No conformidades: todas las NC de la organización. Cada vista cuenta lo mismo que la cifra del panel
 * que enlaza aquí; al abrir una NC se va a su auditoría, con la NC desplegada.
 */
export default function FindingsPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const current = findingViews.find((v) => v.param === params.get('vista')) ?? findingViews[0];
  const [search, setSearch] = useState('');
  const deferredSearch = useDeferredValue(search);

  const { data, isLoading, error } = useQuery({
    queryKey: ['findings-list', current.view],
    queryFn: () => FindingApi.listAll(current.view),
    placeholderData: keepPreviousData,
  });

  const rows = useMemo(() => {
    const all = data?.rows ?? [];
    const terms = normalizeText(deferredSearch).split(/\s+/).filter(Boolean);
    return terms.length === 0 ? all : all.filter((r) => terms.every((t) => rowText(r).includes(t)));
  }, [data, deferredSearch]);
  const searching = deferredSearch.trim() !== '';

  const columns: GridColDef<FindingRow>[] = [
    { field: 'reference', headerName: 'NC', width: 95 },
    {
      field: 'title',
      headerName: 'No conformidad',
      flex: 1.4,
      minWidth: 220,
      valueGetter: (_v, r) => [r.controlPointRef, r.title || 'Sin título'].filter(Boolean).join(' · '),
    },
    { field: 'auditReference', headerName: 'Auditoría', width: 135 },
    { field: 'farmName', headerName: 'Explotación', flex: 1, minWidth: 170 },
    {
      field: 'classification',
      headerName: 'Gravedad',
      width: 130,
      renderCell: (p) => (
        <Chip size="small" label={label(classificationLabels, p.row.classification)} color={classificationColors[p.row.classification] ?? 'default'} />
      ),
    },
    {
      field: 'dueDate',
      headerName: 'Plazo',
      width: 200,
      renderCell: (p) =>
        p.row.dueDate ? (
          <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', height: '100%' }}>
            <DeadlineChip
              dueDate={p.row.dueDate}
              closed={p.row.status === 'CLOSED' || p.row.status === 'VERIFIED'}
              closedAt={p.row.closedAt}
            />
            {p.row.lateJustification && (
              <Tooltip title={`Retraso justificado: ${p.row.lateJustification}`}>
                <Chip size="small" variant="outlined" color="warning" label="Justificada" />
              </Tooltip>
            )}
          </Stack>
        ) : (
          'Sin plazo'
        ),
    },
    {
      field: 'status',
      headerName: 'Estado',
      width: 130,
      renderCell: (p) => (
        <Chip size="small" variant="outlined" label={label(findingStatusLabels, p.row.status)} color={findingStatusColors[p.row.status] ?? 'default'} />
      ),
    },
  ];

  return (
    <Box>
      <PageHeader
        title="No conformidades"
        subtitle="Todas las de la organización: abre una para ir a su auditoría con la no conformidad desplegada"
      />
      <Stack
        direction="row"
        spacing={1}
        useFlexGap
        sx={{ flexWrap: 'wrap', mb: 2 }}
        role="group"
        aria-label="Vista de no conformidades"
      >
        {findingViews.map((v) => {
          const selected = v.view === current.view;
          const n = data?.counts[v.view];
          return (
            <Chip
              key={v.view}
              label={n == null ? v.label : `${v.label} (${n})`}
              clickable
              color={selected ? 'primary' : 'default'}
              variant={selected ? 'filled' : 'outlined'}
              aria-pressed={selected}
              onClick={() => setParams(v.view === 'OPEN' ? {} : { vista: v.param }, { replace: true })}
            />
          );
        })}
      </Stack>
      <Box sx={{ mb: 2, display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
        <TextField
          size="small"
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Escape') setSearch('');
          }}
          placeholder="Buscar por NC, título, auditoría, explotación o punto…"
          sx={{
            flex: '1 1 320px',
            maxWidth: 520,
            bgcolor: 'background.paper',
            // Sin la «x» propia del navegador: ya hay un botón para borrar la búsqueda.
            '& input::-webkit-search-cancel-button': { display: 'none' },
          }}
          slotProps={{
            htmlInput: { 'aria-label': 'Buscar no conformidades' },
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
              endAdornment: search ? (
                <InputAdornment position="end">
                  <IconButton size="small" aria-label="Borrar búsqueda" onClick={() => setSearch('')}>
                    <ClearIcon fontSize="small" />
                  </IconButton>
                </InputAdornment>
              ) : undefined,
            },
          }}
        />
        {data && (
          <Typography variant="body2" color="text.secondary" role="status" aria-live="polite">
            {searching ? `${rows.length} de ${data.rows.length}` : data.rows.length}{' '}
            {data.rows.length === 1 ? 'no conformidad' : 'no conformidades'} · {current.label.toLowerCase()}
          </Typography>
        )}
      </Box>
      <QueryBoundary isLoading={isLoading} error={error}>
        <DataGrid
          autoHeight
          rows={rows}
          columns={columns}
          localeText={gridLocaleText({
            noRowsLabel: searching
              ? `Ninguna no conformidad coincide con «${deferredSearch.trim()}»`
              : `No hay no conformidades en «${current.label}»`,
          })}
          disableRowSelectionOnClick
          onRowClick={(p) => navigate(findingLink(p.row.auditId, p.row.id))}
          pageSizeOptions={[25, 50, 100]}
          initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
          sx={{ bgcolor: 'background.paper', cursor: 'pointer' }}
        />
      </QueryBoundary>
    </Box>
  );
}
