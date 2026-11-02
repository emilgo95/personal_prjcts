import type { ReactNode } from 'react';
import { Alert, Box, CircularProgress } from '@mui/material';
import { extractErrorMessage } from '../api/client';

export default function QueryBoundary({
  isLoading,
  error,
  children,
}: {
  isLoading: boolean;
  error: unknown;
  children: ReactNode;
}) {
  if (isLoading) {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', py: 6 }}>
        <CircularProgress />
      </Box>
    );
  }
  if (error) {
    return <Alert severity="error">{extractErrorMessage(error)}</Alert>;
  }
  return <>{children}</>;
}
