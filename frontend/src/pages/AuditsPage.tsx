import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Button, Chip, LinearProgress, Box } from '@mui/material';
import { DataGrid, type GridColDef } from '@mui/x-data-grid';
import AddIcon from '@mui/icons-material/Add';
import { AuditApi } from '../api/endpoints';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import { useAuth } from '../auth/AuthContext';
import { auditStatusLabels, auditTypeLabels, label } from '../utils/labels';

export default function AuditsPage() {
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const { data, isLoading, error } = useQuery({ queryKey: ['audits'], queryFn: AuditApi.list });

  const columns: GridColDef[] = [
    { field: 'reference', headerName: 'Referencia', width: 140 },
    { field: 'farmName', headerName: 'Explotación', flex: 1, minWidth: 180 },
    { field: 'standardName', headerName: 'Estándar', flex: 1, minWidth: 180 },
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
      renderCell: (params) => <Chip size="small" label={label(auditStatusLabels, params.value as string)} />,
    },
    {
      field: 'progressPct',
      headerName: 'Progreso',
      width: 150,
      renderCell: (params) => (
        <Box sx={{ width: '100%' }}>
          <LinearProgress variant="determinate" value={params.value as number} />
          <span style={{ fontSize: 11 }}>{params.value as number}%</span>
        </Box>
      ),
    },
    { field: 'findingsCount', headerName: 'NC', width: 70, type: 'number' },
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
      <QueryBoundary isLoading={isLoading} error={error}>
        <DataGrid
          autoHeight
          rows={data ?? []}
          columns={columns}
          disableRowSelectionOnClick
          onRowClick={(p) => navigate(`/audits/${p.id}`)}
          pageSizeOptions={[10, 25, 50]}
          initialState={{ pagination: { paginationModel: { pageSize: 10 } } }}
          sx={{ bgcolor: 'background.paper', cursor: 'pointer' }}
        />
      </QueryBoundary>
    </div>
  );
}
