import { useMutation, useQuery } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router-dom';
import { Box, Button, Chip, Link } from '@mui/material';
import { DataGrid, type GridColDef } from '@mui/x-data-grid';
import DownloadIcon from '@mui/icons-material/Download';
import { DocumentApi, downloadBlob } from '../api/endpoints';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import { useToast } from '../utils/toast';
import { formatDateTime } from '../utils/dates';
import { extractErrorMessage } from '../api/client';
import { standardShort } from '../utils/labels';
import type { GeneratedDocument } from '../api/types';

export default function DocumentCenterPage() {
  const { notify } = useToast();
  const { data, isLoading, error } = useQuery({ queryKey: ['documents'], queryFn: DocumentApi.listAll });

  const download = useMutation({
    mutationFn: async (doc: GeneratedDocument) => {
      const blob = await DocumentApi.download(doc.id);
      downloadBlob(blob, doc.fileName);
    },
    onError: (e) => notify(extractErrorMessage(e), 'error'),
  });

  const columns: GridColDef<GeneratedDocument>[] = [
    { field: 'documentTypeLabel', headerName: 'Documento', flex: 1, minWidth: 200 },
    {
      field: 'auditReference',
      headerName: 'Auditoría',
      width: 150,
      renderCell: (p) =>
        p.row.auditId ? (
          <Link component={RouterLink} to={`/audits/${p.row.auditId}`} underline="hover">
            {(p.value as string) ?? `Auditoría ${p.row.auditId}`}
          </Link>
        ) : (
          (p.value as string)
        ),
    },
    {
      field: 'standardName',
      headerName: 'Estándar',
      width: 190,
      valueGetter: (value, row) => standardShort(row.standardCode, value as string | null),
    },
    {
      field: 'format',
      headerName: 'Formato',
      width: 100,
      renderCell: (p) => <Chip size="small" label={p.value as string} />,
    },
    {
      field: 'generatedAt',
      headerName: 'Generado',
      width: 160,
      valueFormatter: (value) => formatDateTime(value as string),
    },
    { field: 'generatedBy', headerName: 'Autor', width: 180 },
    {
      field: 'actions',
      headerName: '',
      width: 140,
      sortable: false,
      renderCell: (p) => (
        <Button size="small" startIcon={<DownloadIcon />} onClick={() => download.mutate(p.row)}>
          Descargar
        </Button>
      ),
    },
  ];

  return (
    <Box>
      <PageHeader title="Centro de documentos" subtitle="Todos los documentos de auditoría generados" />
      <QueryBoundary isLoading={isLoading} error={error}>
        <DataGrid
          autoHeight
          rows={data ?? []}
          columns={columns}
          disableRowSelectionOnClick
          pageSizeOptions={[10, 25, 50]}
          initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
          sx={{ bgcolor: 'background.paper' }}
        />
      </QueryBoundary>
    </Box>
  );
}
