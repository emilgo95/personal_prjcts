import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Alert, Box, Chip, Stack, Tab, Tabs, Typography } from '@mui/material';
import { AuditApi } from '../api/endpoints';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import AuditStatusChip from '../components/AuditStatusChip';
import { auditTypeLabels, label } from '../utils/labels';
import SummaryTab from '../audit/SummaryTab';
import ChecklistTab from '../audit/ChecklistTab';
import FindingsTab from '../audit/FindingsTab';
import DocumentsTab from '../audit/DocumentsTab';
import HistoryTab from '../audit/HistoryTab';
import { useAuth } from '../auth/AuthContext';

export default function AuditDetailPage() {
  const { id } = useParams();
  const auditId = Number(id);
  const { hasRole } = useAuth();
  const canSeeHistory = hasRole('ADMIN', 'QUALITY', 'AUDITOR');
  const [tab, setTab] = useState(0);
  // Las pestañas ya visitadas siguen montadas (solo ocultas): así el checklist no pierde los cambios sin
  // guardar al cambiar de pestaña, y las que nadie abre no cargan datos.
  const [visited, setVisited] = useState<Set<number>>(() => new Set([0]));
  const selectTab = (value: number) => {
    setTab(value);
    setVisited((v) => (v.has(value) ? v : new Set(v).add(value)));
  };

  const { data, isLoading, error } = useQuery({
    queryKey: ['audit', auditId],
    queryFn: () => AuditApi.get(auditId),
  });

  const locked = data?.status === 'SUBMITTED' || data?.status === 'CLOSED';

  return (
    <Box>
      <QueryBoundary isLoading={isLoading} error={error}>
        {data && (
          <>
            <PageHeader
              title={`${data.reference} · ${data.farm.legalName}`}
              subtitle={`${data.standardName}${data.standardVersion ? ` (${data.standardVersion})` : ''}`}
              action={
                <Stack direction="row" spacing={1}>
                  <Chip label={label(auditTypeLabels, data.auditType)} variant="outlined" />
                  <AuditStatusChip status={data.status} size="medium" />
                </Stack>
              }
            />
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              {data.assessedCount}/{data.totalControlPoints} puntos evaluados · {data.progressPct}% · {data.findingsCount}{' '}
              no conformidades
            </Typography>

            <Tabs
              value={tab}
              onChange={(_, v) => selectTab(v)}
              variant="scrollable"
              scrollButtons="auto"
              allowScrollButtonsMobile
              sx={{ mb: 2 }}
            >
              <Tab label="Resumen" />
              <Tab label="Checklist" />
              <Tab label="Hallazgos y CAPA" />
              <Tab label="Documentos" />
              {canSeeHistory && <Tab label="Historial" />}
            </Tabs>

            {locked && (
              <Alert severity="info" sx={{ mb: 2 }}>
                {data.status === 'CLOSED'
                  ? 'La auditoría está cerrada: solo se puede consultar y generar documentos.'
                  : 'La auditoría está enviada y no admite cambios. Para modificarla, reábrela desde el resumen.'}
              </Alert>
            )}

            {[
              <SummaryTab key="summary" auditId={auditId} audit={data} />,
              <ChecklistTab key="checklist" auditId={auditId} standardCode={data.standardCode} locked={locked} />,
              <FindingsTab key="findings" auditId={auditId} standardCode={data.standardCode} locked={locked} />,
              <DocumentsTab key="documents" auditId={auditId} />,
              canSeeHistory ? <HistoryTab key="history" auditId={auditId} /> : <Box key="history" />,
            ].map(
              (content, i) =>
                visited.has(i) && (
                  <Box key={content.key} sx={{ display: tab === i ? 'block' : 'none' }}>
                    {content}
                  </Box>
                ),
            )}
          </>
        )}
      </QueryBoundary>
    </Box>
  );
}
