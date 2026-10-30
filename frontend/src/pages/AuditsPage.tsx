import { useEffect, useState } from 'react';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Box, Button, Chip, CircularProgress, IconButton, InputAdornment, TextField, Typography } from '@mui/material';
import { DataGrid, type GridColDef } from '@mui/x-data-grid';
import AddIcon from '@mui/icons-material/Add';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import AssignmentLateIcon from '@mui/icons-material/AssignmentLate';
import { AuditApi } from '../api/endpoints';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import ProgressMeter from '../components/ProgressMeter';
import { useAuth } from '../auth/AuthContext';
import AuditStatusChip from '../components/AuditStatusChip';
import { auditTypeLabels, label, standardShort } from '../utils/labels';
import { gridLocaleText } from '../utils/gridLocale';

export default function AuditsPage() {
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const [paging, setPaging] = useState({ page: 0, pageSize: 25 });
  // La búsqueda va en la dirección (?q=) para que se conserve al volver desde el detalle de una auditoría.
  const [params, setParams] = useSearchParams();
  const query = params.get('q') ?? '';
  const [search, setSearch] = useState(query);
  // Si la dirección cambia por fuera (atrás/adelante del navegador), el cuadro muestra la búsqueda de la dirección.
  useEffect(() => {
    setSearch((s) => (s.trim() === query ? s : query));
  }, [query]);
  useEffect(() => {
    const next = search.trim();
    if (next === query) return;
    const timer = setTimeout(() => {
      setParams(next ? { q: next } : {}, { replace: true });
      setPaging((p) => ({ ...p, page: 0 }));
    }, 300);
    return () => clearTimeout(timer);
  }, [search, query, setParams]);

  const { data, isLoading, isFetching, error } = useQuery({
    queryKey: ['audits', paging.page, paging.pageSize, query],
    queryFn: () => AuditApi.list(paging.page, paging.pageSize, query),
    placeholderData: keepPreviousData,
  });
  const total = data?.totalElements ?? 0;

  const columns: GridColDef[] = [
    { field: 'reference', headerName: 'Referencia', width: 140 },
    // Explotación con tope de ancho y Estándar fijo: así Tipo queda cerca y el sobrante va a Progreso.
    { field: 'farmName', headerName: 'Explotación', flex: 1, minWidth: 200, maxWidth: 290 },
    {
      field: 'standardName',
      headerName: 'Estándar',
      width: 165,
      valueGetter: (value, row) => standardShort(row.standardCode, value as string),
    },
    {
      field: 'auditType',
      headerName: 'Tipo',
      width: 150,
      valueGetter: (value) => label(auditTypeLabels, value as string),
    },
    {
      field: 'status',
      headerName: 'Estado',
      width: 130,
      renderCell: (params) => <AuditStatusChip status={params.value as string} />,
    },
    {
      field: 'progressPct',
      headerName: 'Progreso',
      flex: 1,
      minWidth: 190,
      renderCell: (params) => (
        <ProgressMeter
          compact
          value={params.value as number}
          assessed={params.row.assessedCount}
          total={params.row.totalControlPoints}
        />
      ),
    },
    {
      field: 'findingsCount',
      headerName: 'NC',
      width: 90,
      align: 'center',
      headerAlign: 'center',
      sortable: false,
      // Enlace a las NC de la auditoría (pestaña Hallazgos y CAPA); sin NC, solo el cero.
      renderCell: (p) => (
        <Box sx={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          {p.row.findingsCount > 0 ? (
            <Chip
              component={RouterLink}
              to={`/audits/${p.row.id}?tab=hallazgos`}
              onClick={(e: React.MouseEvent) => e.stopPropagation()}
              clickable
              size="small"
              icon={<AssignmentLateIcon />}
              label={p.row.findingsCount}
              aria-label={`Ver las ${p.row.findingsCount} no conformidades de ${p.row.reference}`}
              title="Ver las no conformidades"
              sx={{
                bgcolor: '#fdf1e2',
                color: '#8a4b05',
                fontWeight: 700,
                '& .MuiChip-icon': { color: '#b45f06' },
                '&:hover': { bgcolor: '#fbe3c5' },
              }}
            />
          ) : (
            <Typography variant="body2" color="text.disabled">
              0
            </Typography>
          )}
        </Box>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Auditorías"
        subtitle="Auditorías internacionales frente a estándares del sector agrícola"
        action={
          hasRole('ADMIN', 'AUDITOR') && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate('/audits/new')}>
              Nueva auditoría
            </Button>
          )
        }
      />
      <Box sx={{ mb: 2, display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
        <TextField
          size="small"
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Escape') setSearch('');
          }}
          placeholder="Buscar por referencia, explotación, localidad, región, estándar, auditor, tipo o estado…"
          sx={{
            flex: '1 1 320px',
            maxWidth: 560,
            bgcolor: 'background.paper',
            // Sin la «x» propia del navegador: ya hay un botón para borrar la búsqueda.
            '& input::-webkit-search-cancel-button': { display: 'none' },
          }}
          slotProps={{
            htmlInput: { 'aria-label': 'Buscar auditorías' },
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
            {query
              ? `${total} ${total === 1 ? 'auditoría encontrada' : 'auditorías encontradas'}`
              : `${total} ${total === 1 ? 'auditoría' : 'auditorías'}`}
          </Typography>
        )}
        {isFetching && !isLoading && <CircularProgress size={16} aria-label="Buscando" />}
      </Box>
      <QueryBoundary isLoading={isLoading} error={error}>
        <DataGrid
          autoHeight
          rows={data?.content ?? []}
          rowCount={total}
          localeText={gridLocaleText({
            noRowsLabel: query ? `Ninguna auditoría coincide con «${query}»` : 'Todavía no hay auditorías',
          })}
          paginationMode="server"
          paginationModel={paging}
          onPaginationModelChange={(m) => setPaging({ page: m.page, pageSize: m.pageSize })}
          columns={columns}
          disableRowSelectionOnClick
          onRowClick={(p) => navigate(`/audits/${p.id}`)}
          pageSizeOptions={[10, 25, 50]}
          sx={{ bgcolor: 'background.paper', cursor: 'pointer' }}
        />
      </QueryBoundary>
    </div>
  );
}
