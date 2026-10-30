import { createTheme } from '@mui/material/styles';
import { esES } from '@mui/material/locale';
import { esES as dataGridEsES } from '@mui/x-data-grid/locales';
import type {} from '@mui/x-data-grid/themeAugmentation';

/** Fondo y borde de las cabeceras de tabla: el verde de la marca, muy diluido. */
const tableHeaderBg = '#edf4f1';
const tableHeaderBorder = '#cfe0da';

export const theme = createTheme(
  {
    palette: {
      mode: 'light',
      primary: { main: '#0f6b5f', dark: '#0f2e2b', light: '#3d9488' },
      secondary: { main: '#8a6100' },
      // Tonos más oscuros que los de MUI para que el texto blanco de los chips llegue a contraste 4,5:1 (WCAG AA).
      warning: { main: '#b45f06' },
      info: { main: '#0277bd' },
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
      // Cabeceras de tabla con un fondo suave del color de la marca, en todas las tablas de la aplicación.
      MuiTableHead: {
        styleOverrides: {
          root: {
            '& .MuiTableCell-head': {
              backgroundColor: tableHeaderBg,
              color: '#24413b',
              fontWeight: 600,
              borderBottomColor: tableHeaderBorder,
            },
          },
        },
      },
      MuiButton: { defaultProps: { disableElevation: true } },
      MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' } } },
      // Paginación de las tablas («Filas por página» y páginas) a la izquierda, bajo la primera columna.
      MuiDataGrid: {
        styleOverrides: {
          root: {
            '--DataGrid-containerBackground': tableHeaderBg,
            '& .MuiDataGrid-columnHeader, & .MuiDataGrid-filler, & .MuiDataGrid-scrollbarFiller--header': {
              backgroundColor: tableHeaderBg,
            },
            '& .MuiDataGrid-columnHeaderTitle': { fontWeight: 600, color: '#24413b' },
            '& .MuiDataGrid-columnHeaders .MuiDataGrid-columnHeader': { borderBottomColor: tableHeaderBorder },
          },
          footerContainer: {
            justifyContent: 'flex-start',
            '& .MuiTablePagination-root': { marginLeft: 0 },
            '& .MuiTablePagination-spacer': { display: 'none' },
          },
        },
      },
    },
  },
  esES,
  dataGridEsES,
);
