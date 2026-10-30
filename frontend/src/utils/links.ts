/** Solo direcciones web completas (http o https): evita mostrar como enlace un «javascript:» o algo sin dominio. */
export function isWebLink(url: string | null | undefined): boolean {
  if (!url) return false;
  try {
    const parsed = new URL(url.trim());
    return (parsed.protocol === 'http:' || parsed.protocol === 'https:') && parsed.hostname !== '';
  } catch {
    return false;
  }
}

/** Dirección de una NC dentro de su auditoría: pestaña Hallazgos y CAPA, con la NC desplegada y resaltada. */
export function findingLink(auditId: number, findingId: number): string {
  return `/audits/${auditId}?tab=hallazgos&nc=${findingId}`;
}

/** Lista de NC de la organización en una vista (abiertas, vencidas, 7-dias, cerradas…). */
export function findingsListLink(vista?: string): string {
  return vista ? `/audits/findings?vista=${vista}` : '/audits/findings';
}
