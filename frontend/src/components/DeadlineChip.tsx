import { Chip } from '@mui/material';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined';
import { deadlineInfo, type DeadlineTone } from '../utils/dates';

const colors: Record<DeadlineTone, 'error' | 'warning' | 'success' | 'default'> = {
  overdue: 'error',
  soon: 'warning',
  ok: 'default',
  done: 'default',
};

/** Plazo de una no conformidad: rojo si venció, ámbar si vence en 7 días o menos, neutro si hay margen. */
export default function DeadlineChip({ dueDate, closed = false }: { dueDate: string; closed?: boolean }) {
  const info = deadlineInfo(dueDate, closed);
  const icon =
    info.tone === 'overdue' ? (
      <WarningAmberIcon />
    ) : info.tone === 'done' ? (
      <CheckCircleOutlineIcon />
    ) : (
      <AccessTimeIcon />
    );
  return (
    <Chip
      size="small"
      icon={icon}
      label={info.label}
      color={colors[info.tone]}
      variant={info.tone === 'overdue' || info.tone === 'soon' ? 'filled' : 'outlined'}
    />
  );
}
