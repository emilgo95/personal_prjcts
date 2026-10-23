import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Button, Chip, LinearProgress, Box } from '@mui/material';
import { DataGrid, type GridColDef } from '@mui/x-data-grid';
import AddIcon from '@mui/icons-material/Add';
import { AuditApi } from '../api/endpoints';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import { useAuth } from '../auth/AuthContext';
import { auditStatusColors, auditStatusLabels, auditTypeLabels, label, standardShort } from '../utils/labels';

export default function AuditsPage() {
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const [paging, setPaging] = useState({ page: 0, pageSize: 25 });
  const { data, isLoading, error } = useQuery({
    queryKey: ['audits', paging.page, paging.pageSize],
    queryFn: () => AuditApi.list(paging.page, paging.pageSize),
    placeholderData: keepPreviousData,
  });

  const columns: GridColDef[] = [
    { field: 'reference', headerName: 'Referencia', width: 140 },
    { field: 'farmName', headerName: 'Explotación', flex: 1, minWidth: 180 },
    {
      field: 'standardName',
      headerName: 'Estándar',
      flex: 1,
      minWidth: 170,
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
      renderCell: (params) => (
        <Chip
          size="small"
          color={auditStatusColors[params.value as string] ?? 'default'}
          label={label(auditStatusLabels, params.value as string)}
        />
      ),
    },
    {
      field: 'progressPct',
      headerName: 'Progreso',
      width: 150,
      renderCell: (params) => (
        <Box sx={{ width: '100%' }}>
          <LinearProgress variant="determinate" value={params.value as number} aria-label="Progreso de evaluación" />
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
          rows={data?.content ?? []}
          rowCount={data?.totalElements ?? 0}
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
