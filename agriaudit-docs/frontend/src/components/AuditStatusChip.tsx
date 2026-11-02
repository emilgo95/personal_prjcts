import { Chip } from '@mui/material';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import { auditStatusLabels, auditStatusStyle, label } from '../utils/labels';

/** Estado de la auditoría en tonos fríos y neutros; la cerrada lleva candado. El semáforo es solo del progreso. */
export default function AuditStatusChip({ status, size = 'small' }: { status: string; size?: 'small' | 'medium' }) {
  const style = auditStatusStyle[status] ?? auditStatusStyle.DRAFT;
  return (
    <Chip
      size={size}
      label={label(auditStatusLabels, status)}
      icon={status === 'CLOSED' ? <LockOutlinedIcon sx={{ fontSize: 14, color: 'inherit !important' }} /> : undefined}
      sx={{ bgcolor: style.bg, color: style.fg, fontWeight: 600 }}
    />
  );
}
