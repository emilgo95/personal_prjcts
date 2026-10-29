import { describe, expect, it } from 'vitest';
import type { Farm } from '../api/types';
import { filterFarms, normalizeText } from './search';

const farm = (id: number, over: Partial<Farm>): Farm => ({
  id,
  legalName: `Finca ${id}`,
  tradeName: null,
  registrationNumber: null,
  address: null,
  city: null,
  region: null,
  country: null,
  latitude: null,
  longitude: null,
  totalHectares: null,
  crops: null,
  productionAreas: null,
  contactName: null,
  contactEmail: null,
  contactPhone: null,
  notes: null,
  ...over,
});

const farms = [
  farm(1, { legalName: 'Hortícola Águilas S.L.', city: 'Águilas', region: 'Murcia', crops: 'Lechuga, brócoli' }),
  farm(2, { legalName: 'Invernaderos del Poniente', city: 'El Ejido', region: 'Almería', registrationNumber: 'GGN 4049928', crops: 'Tomate' }),
  farm(3, { legalName: 'Cítricos La Vega', city: 'Lorca', region: 'Murcia', crops: 'Limón', contactName: 'Marta Soler' }),
];

describe('normalizeText', () => {
  it('quita tildes y mayúsculas', () => {
    expect(normalizeText('  ÁGUILAS Almería ')).toBe('aguilas almeria');
  });
});

describe('filterFarms', () => {
  it('sin texto devuelve todas', () => {
    expect(filterFarms(farms, '   ')).toHaveLength(3);
  });

  it('encuentra sin importar tildes ni mayúsculas', () => {
    expect(filterFarms(farms, 'aguilas').map((f) => f.id)).toEqual([1]);
    expect(filterFarms(farms, 'ALMERIA').map((f) => f.id)).toEqual([2]);
  });

  it('busca por razón social, GGN, localidad, región y cultivo', () => {
    expect(filterFarms(farms, 'poniente').map((f) => f.id)).toEqual([2]);
    expect(filterFarms(farms, '4049928').map((f) => f.id)).toEqual([2]);
    expect(filterFarms(farms, 'murcia').map((f) => f.id)).toEqual([1, 3]);
    expect(filterFarms(farms, 'limon').map((f) => f.id)).toEqual([3]);
  });

  it('exige todas las palabras, en cualquier orden', () => {
    expect(filterFarms(farms, 'murcia limon').map((f) => f.id)).toEqual([3]);
    expect(filterFarms(farms, 'limon murcia lechuga')).toEqual([]);
  });

  it('no busca en los datos de contacto', () => {
    expect(filterFarms(farms, 'soler')).toEqual([]);
  });
});
