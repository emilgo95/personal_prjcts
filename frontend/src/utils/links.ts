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
