import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Box, Chip, Stack, Tab, Tabs, Typography } from '@mui/material';
import { AuditApi } from '../api/endpoints';
import PageHeader from '../components/PageHeader';
import QueryBoundary from '../components/QueryBoundary';
import { auditStatusColors, auditStatusLabels, auditTypeLabels, label } from '../utils/labels';
import SummaryTab from '../audit/SummaryTab';
import ChecklistTab from '../audit/ChecklistTab';
import FindingsTab from '../audit/FindingsTab';
import DocumentsTab from '../audit/DocumentsTab';

export default function AuditDetailPage() {
  const { id } = useParams();
  const auditId = Number(id);
  const [tab, setTab] = useState(0);

  const { data, isLoading, error } = useQuery({
    queryKey: ['audit', auditId],
    queryFn: () => AuditApi.get(auditId),
  });

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
                  <Chip color={auditStatusColors[data.status] ?? 'default'} label={label(auditStatusLabels, data.status)} />
                </Stack>
              }
            />
            <Typography variant="body2" color="text.secondary" mb={2}>
              {data.assessedCount}/{data.totalControlPoints} puntos evaluados · {data.progressPct}% · {data.findingsCount}{' '}
              no conformidades
            </Typography>

            <Tabs
              value={tab}
              onChange={(_, v) => setTab(v)}
              variant="scrollable"
              scrollButtons="auto"
              allowScrollButtonsMobile
              sx={{ mb: 2 }}
            >
              <Tab label="Resumen" />
              <Tab label="Checklist" />
              <Tab label="Hallazgos y CAPA" />
              <Tab label="Documentos" />
            </Tabs>

            {tab === 0 && <SummaryTab auditId={auditId} audit={data} />}
            {tab === 1 && <ChecklistTab auditId={auditId} standardCode={data.standardCode} />}
            {tab === 2 && <FindingsTab auditId={auditId} standardCode={data.standardCode} />}
            {tab === 3 && <DocumentsTab auditId={auditId} />}
          </>
        )}
      </QueryBoundary>
    </Box>
  );
}
