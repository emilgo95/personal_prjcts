import { Chip } from '@mui/material';
import { evidenceStatusColors, evidenceStatusLabels } from '../utils/labels';

/** Estado de una evidencia con color y texto (el color nunca va solo). */
export default function EvidenceStatusChip({ status, size = 'small' }: { status: string; size?: 'small' | 'medium' }) {
  const color = evidenceStatusColors[status] ?? 'default';
  return (
    <Chip
      size={size}
      color={color}
      variant={color === 'default' ? 'outlined' : 'filled'}
      label={evidenceStatusLabels[status] ?? status}
    />
  );
}
