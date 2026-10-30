/** Sin caracteres que se confunden al dictarlos o leerlos (0/O, 1/l/I); igual que las temporales del servidor. */
const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789';

/** Contraseña temporal fácil de dictar: tres grupos de cuatro caracteres (p. ej. «Kp7m-Qx3r-Zt9w»). */
export function temporaryPassword(): string {
  const bytes = new Uint32Array(12);
  crypto.getRandomValues(bytes);
  const chars = Array.from(bytes, (n) => ALPHABET[n % ALPHABET.length]);
  return [chars.slice(0, 4), chars.slice(4, 8), chars.slice(8)].map((g) => g.join('')).join('-');
}
