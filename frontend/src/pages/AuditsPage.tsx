import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Button } from '@mui/material';
import { DataGrid, type GridColDef } from '@mui/x-data-grid';
import AddIcon from '@mui/icons-material/Add';
import { AuditApi } from '../api/endpoints';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import ProgressMeter from '../components/ProgressMeter';
import { useAuth } from '../auth/AuthContext';
import AuditStatusChip from '../components/AuditStatusChip';
import { auditTypeLabels, label, standardShort } from '../utils/labels';

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
      renderCell: (params) => <AuditStatusChip status={params.value as string} />,
    },
    {
      field: 'progressPct',
      headerName: 'Progreso',
      width: 190,
      renderCell: (params) => (
        <ProgressMeter
          compact
          value={params.value as number}
          assessed={params.row.assessedCount}
          total={params.row.totalControlPoints}
        />
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
