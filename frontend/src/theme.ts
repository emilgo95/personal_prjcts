import { createTheme } from '@mui/material/styles';
import { esES } from '@mui/material/locale';
import { esES as dataGridEsES } from '@mui/x-data-grid/locales';

export const theme = createTheme(
  {
    palette: {
      mode: 'light',
      primary: { main: '#0f6b5f', dark: '#0f2e2b', light: '#3d9488' },
      secondary: { main: '#8a6100' },
      background: { default: '#f4f6f5' },
    },
    shape: { borderRadius: 8 },
    typography: {
      fontFamily: '"Inter","Roboto","Helvetica","Arial",sans-serif',
      h4: { fontWeight: 700 },
      h5: { fontWeight: 700 },
      h6: { fontWeight: 700 },
    },
    components: {
      MuiButton: { defaultProps: { disableElevation: true } },
      MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' } } },
    },
  },
  esES,
  dataGridEsES,
);
