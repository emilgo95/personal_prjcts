import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@mui/material';
import { DataGrid, type GridColDef } from '@mui/x-data-grid';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import { GridActionsCellItem } from '@mui/x-data-grid';
import { FarmApi } from '../api/endpoints';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../utils/toast';
import { extractErrorMessage } from '../api/client';

export default function FarmsPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { hasRole } = useAuth();
  const { notify } = useToast();
  const { data, isLoading, error } = useQuery({ queryKey: ['farms'], queryFn: FarmApi.list });

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
                onClick={() => {
                  if (window.confirm('¿Eliminar esta explotación?')) remove.mutate(Number(params.id));
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
        <div style={{ width: '100%' }}>
          <DataGrid
            autoHeight
            rows={data ?? []}
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
