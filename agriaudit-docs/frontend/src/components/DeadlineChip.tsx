import type { ReactElement } from 'react';
import { Chip } from '@mui/material';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import AlarmIcon from '@mui/icons-material/Alarm';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined';
import EventBusyIcon from '@mui/icons-material/EventBusy';
import { deadlineInfo, type DeadlineTone } from '../utils/dates';

const styles: Record<DeadlineTone, { color: 'error' | 'warning' | 'success' | 'default'; filled: boolean }> = {
  overdue: { color: 'error', filled: true },
  urgent: { color: 'error', filled: false },
  soon: { color: 'warning', filled: true },
  watch: { color: 'warning', filled: false },
  ok: { color: 'default', filled: false },
  onTime: { color: 'success', filled: false },
  late: { color: 'error', filled: false },
  done: { color: 'default', filled: false },
};

const icons: Record<DeadlineTone, ReactElement> = {
  overdue: <WarningAmberIcon />,
  urgent: <AlarmIcon />,
  soon: <AlarmIcon />,
  watch: <AccessTimeIcon />,
  ok: <AccessTimeIcon />,
  onTime: <CheckCircleOutlineIcon />,
  late: <EventBusyIcon />,
  done: <CheckCircleOutlineIcon />,
};

/**
 * Reloj de cierre de una no conformidad: rojo si venció o vence en 2 días, ámbar a 7 y 14 días, neutro si hay
 * margen; una vez cerrada, verde si fue en plazo y rojo si fue con retraso.
 */
export default function DeadlineChip({
  dueDate,
  closed = false,
  closedAt = null,
}: {
  dueDate: string;
  closed?: boolean;
  closedAt?: string | null;
}) {
  const info = deadlineInfo(dueDate, closed, new Date(), closedAt);
  const style = styles[info.tone];
  return (
    <Chip
      size="small"
      icon={icons[info.tone]}
      label={info.label}
      color={style.color}
      variant={style.filled ? 'filled' : 'outlined'}
      title={`Fecha límite: ${new Date(dueDate + 'T00:00:00').toLocaleDateString('es-ES')}`}
    />
  );
}
