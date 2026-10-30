import type { GridLocaleText } from '@mui/x-data-grid';
import { esES } from '@mui/x-data-grid/locales';

/**
 * Textos de la tabla en español con los propios de una pantalla. Hace falta porque el `localeText` que se pasa a una
 * tabla sustituye entero al del tema: sin esto, la paginación («Filas por página») vuelve a salir en inglés.
 */
export function gridLocaleText(overrides: Partial<GridLocaleText>): Partial<GridLocaleText> {
  return { ...esES.components.MuiDataGrid.defaultProps.localeText, ...overrides };
}
