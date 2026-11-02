import type { ReactNode } from 'react';
import { Box, Stack, Typography } from '@mui/material';

export default function PageHeader({
  title,
  subtitle,
  action,
}: {
  title: string;
  subtitle?: string;
  action?: ReactNode;
}) {
  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ justifyContent: "space-between", alignItems: { xs: 'flex-start', sm: 'center' }, mb: 3 }}>
      <Box>
        <Typography variant="h4">{title}</Typography>
        {subtitle && (
          <Typography variant="body2" color="text.secondary">
            {subtitle}
          </Typography>
        )}
      </Box>
      {action}
    </Stack>
  );
}
