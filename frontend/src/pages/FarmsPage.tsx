import { useDeferredValue, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Box, Button, IconButton, InputAdornment, TextField, Typography } from '@mui/material';
import { DataGrid, type GridColDef } from '@mui/x-data-grid';
import AddIcon from '@mui/icons-material/Add';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import { GridActionsCellItem } from '@mui/x-data-grid';
import { FarmApi } from '../api/endpoints';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../utils/toast';
import { useConfirm } from '../utils/confirm';
import { extractErrorMessage } from '../api/client';
import { filterFarms } from '../utils/search';

export default function FarmsPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { hasRole } = useAuth();
  const { notify } = useToast();
  const confirm = useConfirm();
  const { data, isLoading, error } = useQuery({ queryKey: ['farms'], queryFn: FarmApi.list });
  const [search, setSearch] = useState('');
  const deferredSearch = useDeferredValue(search);
  const farms = useMemo(() => data ?? [], [data]);
  const visibleFarms = useMemo(() => filterFarms(farms, deferredSearch), [farms, deferredSearch]);
  const searching = deferredSearch.trim() !== '';

  const remove = useMutation({
    mutationFn: (id: number) => FarmApi.remove(id),
    onSuccess: () => {
      notify('Explotación eliminada', 'success');
      queryClient.invalidateQueries({ queryKey: ['farms'] });
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const columns: GridColDef[] = [
    { field: 'legalName', headerName: 'Razón social', flex: 1.4, minWidth: 200 },
    { field: 'registrationNumber', headerName: 'GGN / Nº registro', width: 160 },
    { field: 'city', headerName: 'Localidad', width: 150 },
    { field: 'country', headerName: 'País', width: 130 },
    {
      field: 'totalHectares',
      headerName: 'Hectáreas',
      width: 110,
      type: 'number',
      valueFormatter: (value: number | null) => (value == null ? '' : value.toLocaleString('es-ES')),
    },
    { field: 'crops', headerName: 'Cultivos', flex: 1, minWidth: 180 },
    {
      field: 'actions',
      type: 'actions',
      width: 70,
      getActions: (params) =>
        hasRole('ADMIN')
          ? [
              <GridActionsCellItem
                key="del"
                icon={<DeleteOutlineIcon />}
                label="Eliminar"
                onClick={async () => {
                  const ok = await confirm({
                    title: '¿Eliminar esta explotación?',
                    message: `Se eliminará «${params.row.legalName}». No se puede deshacer.`,
                    confirmLabel: 'Eliminar',
                    tone: 'danger',
                  });
                  if (ok) remove.mutate(Number(params.id));
                }}
              />,
            ]
          : [],
    },
  ];

  return (
    <div>
      <PageHeader
        title="Explotaciones"
        subtitle="Empresas y fincas agrícolas auditadas"
        action={
          hasRole('ADMIN', 'AUDITOR') && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate('/farms/new')}>
              Nueva explotación
            </Button>
          )
        }
      />
      <QueryBoundary isLoading={isLoading} error={error}>
        <Box sx={{ mb: 2, display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
          <TextField
            size="small"
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Escape') setSearch('');
            }}
            placeholder="Buscar por nombre, GGN, localidad, región o cultivo…"
            sx={{ flex: '1 1 320px', maxWidth: 520, bgcolor: 'background.paper' }}
            slotProps={{
              htmlInput: { 'aria-label': 'Buscar explotaciones' },
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
          <Typography variant="body2" color="text.secondary" role="status" aria-live="polite">
            {searching
              ? `${visibleFarms.length} de ${farms.length} explotaciones`
              : `${farms.length} ${farms.length === 1 ? 'explotación' : 'explotaciones'}`}
          </Typography>
        </Box>
        <div style={{ width: '100%' }}>
          <DataGrid
            autoHeight
            rows={visibleFarms}
            localeText={{
              noRowsLabel: searching
                ? `Ninguna explotación coincide con «${deferredSearch.trim()}»`
                : 'Todavía no hay explotaciones',
            }}
            columns={columns}
            disableRowSelectionOnClick
            onRowClick={(p) => navigate(`/farms/${p.id}`)}
            pageSizeOptions={[10, 25, 50]}
            initialState={{ pagination: { paginationModel: { pageSize: 10 } } }}
            sx={{ bgcolor: 'background.paper', cursor: 'pointer' }}
          />
        </div>
      </QueryBoundary>
    </div>
  );
}
