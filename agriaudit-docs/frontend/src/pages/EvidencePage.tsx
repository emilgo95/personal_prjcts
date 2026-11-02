import { useState } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Box, Link, Tab, Tabs } from '@mui/material';
import { EvidenceApi, FarmApi } from '../api/endpoints';
import type { EvidenceRecord } from '../api/types';
import PageHeader from '../components/PageHeader';
import RecordsTab from '../evidence/RecordsTab';
import HealthTab from '../evidence/HealthTab';
import RecordDialog, { type RecordPreset } from '../evidence/RecordDialog';
import RecordDetailDialog from '../evidence/RecordDetailDialog';

/**
 * Auditorías › Evidencias: los registros de evidencia de todas las explotaciones y la salud del sistema. Se registran
 * una vez por explotación y sirven para varias auditorías y normas. Con ?farm=ID se abre filtrada por esa explotación.
 */
export default function EvidencePage() {
  const queryClient = useQueryClient();
  const [params] = useSearchParams();
  const [tab, setTab] = useState(0);
  const [farmFilter, setFarmFilter] = useState<number | ''>(() => {
    const farm = Number(params.get('farm'));
    return Number.isInteger(farm) && farm > 0 ? farm : '';
  });
  const [typeFilter, setTypeFilter] = useState('');
  const [dialog, setDialog] = useState<{ record: EvidenceRecord | null; preset?: RecordPreset } | null>(null);
  const [detailId, setDetailId] = useState<number | null>(null);

  const farms = useQuery({ queryKey: ['farms'], queryFn: FarmApi.list });
  const types = useQuery({ queryKey: ['evidence-types'], queryFn: EvidenceApi.types });

  const onSaved = () => {
    queryClient.invalidateQueries({ queryKey: ['evidence-records'] });
    queryClient.invalidateQueries({ queryKey: ['evidence-health'] });
    queryClient.invalidateQueries({ queryKey: ['evidence-coverage'] });
    queryClient.invalidateQueries({ queryKey: ['evidence-map'] });
    queryClient.invalidateQueries({ queryKey: ['evidence-record'] });
  };

  return (
    <Box>
      <PageHeader
        title="Evidencias"
        subtitle="Registra una vez cada evidencia (análisis de agua, evaluación de riesgos…) y reutilízala en todas las normas"
        action={
          <Link component={RouterLink} to="/audits/equivalences" variant="body2">
            Ver las equivalencias entre normas
          </Link>
        }
      />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }} aria-label="Secciones de evidencias">
        <Tab label="Registros" />
        <Tab label="Salud del sistema" />
      </Tabs>

      {tab === 0 && (
        <RecordsTab
          farms={farms.data ?? []}
          types={types.data ?? []}
          farmFilter={farmFilter}
          onFarmFilter={setFarmFilter}
          typeFilter={typeFilter}
          onTypeFilter={setTypeFilter}
          onCreate={() => setDialog({ record: null, preset: farmFilter === '' ? undefined : { farmId: farmFilter } })}
          onEdit={(record) => setDialog({ record })}
          onOpen={(record) => setDetailId(record.id)}
        />
      )}
      {tab === 1 && <HealthTab onRegister={(preset) => setDialog({ record: null, preset })} />}

      <RecordDialog
        open={dialog != null}
        onClose={() => setDialog(null)}
        farms={farms.data ?? []}
        types={types.data ?? []}
        record={dialog?.record}
        preset={dialog?.preset}
        onSaved={onSaved}
      />
      <RecordDetailDialog recordId={detailId} onClose={() => setDetailId(null)} />
    </Box>
  );
}
