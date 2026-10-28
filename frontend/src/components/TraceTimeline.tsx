import { useState } from 'react';
import { Box, Chip, Collapse, IconButton, Stack, Typography } from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import type { TraceEntry } from '../api/types';
import { formatDateTime } from '../utils/dates';
import { label, roleLabels, traceActionLabels } from '../utils/labels';

/** Línea de tiempo de cambios: quién, qué, cuándo y, desplegado, el valor anterior y el nuevo de cada campo. */
export default function TraceTimeline({ entries, empty }: { entries: TraceEntry[]; empty?: string }) {
  if (entries.length === 0) {
    return <Typography color="text.secondary">{empty ?? 'Todavía no hay cambios registrados.'}</Typography>;
  }
  return (
    <Stack spacing={1.5} component="ol" sx={{ listStyle: 'none', p: 0, m: 0 }}>
      {entries.map((e) => (
        <TraceItem key={e.id} entry={e} />
      ))}
    </Stack>
  );
}

function TraceItem({ entry }: { entry: TraceEntry }) {
  const [open, setOpen] = useState(false);
  const hasChanges = entry.changes.length > 0;
  return (
    <Box component="li" sx={{ borderLeft: 3, borderColor: 'primary.light', pl: 2, py: 0.5 }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }} useFlexGap>
        <Typography variant="body2" sx={{ fontWeight: 600 }}>
          {entry.summary}
        </Typography>
        <Chip size="small" variant="outlined" label={label(traceActionLabels, entry.action)} />
        {hasChanges && (
          <IconButton
            size="small"
            aria-label={open ? 'Ocultar los cambios' : 'Ver los cambios'}
            aria-expanded={open}
            onClick={() => setOpen((v) => !v)}
          >
            <ExpandMoreIcon fontSize="small" sx={{ transform: open ? 'rotate(180deg)' : 'none' }} />
          </IconButton>
        )}
      </Stack>
      <Typography variant="caption" color="text.secondary">
        {formatDateTime(entry.occurredAt)} · {entry.userFullName || entry.username} (
        {label(roleLabels, entry.userRole)})
      </Typography>
      {hasChanges && (
        <Collapse in={open} unmountOnExit>
          <Box component="ul" sx={{ m: 0, mt: 0.5, pl: 2 }}>
            {entry.changes.map((c) => (
              <li key={c.field}>
                <Typography variant="body2">
                  <b>{c.label}:</b>{' '}
                  <span style={{ textDecoration: c.oldValue ? 'line-through' : 'none', opacity: 0.7 }}>
                    {c.oldValue ?? '—'}
                  </span>{' '}
                  → {c.newValue ?? '—'}
                </Typography>
              </li>
            ))}
          </Box>
        </Collapse>
      )}
    </Box>
  );
}
