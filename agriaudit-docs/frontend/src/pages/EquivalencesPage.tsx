import { Link as RouterLink } from 'react-router-dom';
import { Box, Link } from '@mui/material';
import PageHeader from '../components/PageHeader';
import MatrixTab from '../evidence/MatrixTab';

/**
 * Auditorías › Equivalencias entre normas: qué tipo de evidencia sirve para qué punto de qué norma. Es la matriz que
 * usan el «cubierto por…» del checklist y el mapa de evidencias de cada auditoría.
 */
export default function EquivalencesPage() {
  return (
    <Box>
      <PageHeader
        title="Equivalencias entre normas"
        subtitle="Una evidencia registrada una vez vale para los puntos equivalentes de todas vuestras normas"
        action={
          <Link component={RouterLink} to="/audits/evidence" variant="body2">
            Ver las evidencias registradas
          </Link>
        }
      />
      <MatrixTab />
    </Box>
  );
}
