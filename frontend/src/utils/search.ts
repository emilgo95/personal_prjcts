import type { Farm } from '../api/types';

/** Minúsculas y sin tildes, para que «Almería» se encuentre escribiendo «almeria». */
export function normalizeText(text: string): string {
  return text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .trim();
}

/** Campos de una explotación en los que busca el buscador (no incluye datos de contacto). */
function farmSearchText(farm: Farm): string {
  return normalizeText(
    [
      farm.legalName,
      farm.tradeName,
      farm.registrationNumber,
      farm.address,
      farm.city,
      farm.region,
      farm.country,
      farm.crops,
      farm.productionAreas,
    ]
      .filter(Boolean)
      .join(' '),
  );
}

/** Todas las palabras escritas deben aparecer (en cualquier campo y orden); sin texto, coincide todo. */
export function filterFarms(farms: Farm[], query: string): Farm[] {
  const terms = normalizeText(query).split(/\s+/).filter(Boolean);
  if (terms.length === 0) return farms;
  return farms.filter((farm) => {
    const text = farmSearchText(farm);
    return terms.every((term) => text.includes(term));
  });
}
