/** Fecha de calendario (sin hora) de una cadena ISO `yyyy-MM-dd`, en hora local. */
function parseDay(iso: string): Date {
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number);
  return new Date(y, m - 1, d);
}

function startOfDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

/** `2026-07-13` → `13/07/2026`; vacío → `—`. */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = parseDay(iso);
  return d.toLocaleDateString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

/** Instante ISO → `13/07/2026, 18:44`. */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('es-ES', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** Días naturales desde `today` hasta la fecha: negativo si ya pasó, 0 si es hoy. */
export function daysUntil(iso: string, today: Date = new Date()): number {
  const ms = parseDay(iso).getTime() - startOfDay(today).getTime();
  return Math.round(ms / 86_400_000);
}

/**
 * Estado del reloj de cierre de una NC (RF-301): vencida; avisos a 2, 7 y 14 días; con margen; y, ya cerrada,
 * en plazo o con retraso (`done` si se desconoce la fecha de cierre).
 */
export type DeadlineTone = 'overdue' | 'urgent' | 'soon' | 'watch' | 'ok' | 'onTime' | 'late' | 'done';

export interface DeadlineInfo {
  tone: DeadlineTone;
  label: string;
  days: number;
}

/** Avisos del reloj de cierre: días que faltan para el vencimiento (T-2, T-7, T-14). */
export const DUE_URGENT_DAYS = 2;
export const DUE_SOON_DAYS = 7;
export const DUE_WATCH_DAYS = 14;

function plural(n: number, one: string, many: string): string {
  return n === 1 ? one : many;
}

/**
 * Estado de una fecha límite (reloj de cierre de NC): vencida, avisos a 2, 7 y 14 días, o con margen. Si la NC ya
 * está cerrada o verificada (`closed`), indica si se cerró en plazo según `closedAt`; sin esa fecha solo muestra el
 * límite.
 */
export function deadlineInfo(
  dueDate: string,
  closed = false,
  today: Date = new Date(),
  closedAt?: string | null,
): DeadlineInfo {
  const days = daysUntil(dueDate, today);
  if (closed) {
    if (!closedAt) return { tone: 'done', label: `Límite ${formatDate(dueDate)}`, days };
    const late = -daysUntil(dueDate, new Date(closedAt));
    if (late <= 0) return { tone: 'onTime', label: 'Cerrada en plazo', days };
    return { tone: 'late', label: `Cerrada con ${late} ${plural(late, 'día', 'días')} de retraso`, days };
  }
  if (days < 0) {
    const n = -days;
    return { tone: 'overdue', label: `Vencida hace ${n} ${plural(n, 'día', 'días')}`, days };
  }
  if (days === 0) return { tone: 'urgent', label: 'Vence hoy', days };
  if (days === 1) return { tone: 'urgent', label: 'Vence mañana', days };
  if (days <= DUE_URGENT_DAYS) return { tone: 'urgent', label: `Vence en ${days} días`, days };
  if (days <= DUE_SOON_DAYS) return { tone: 'soon', label: `Vence en ${days} días`, days };
  if (days <= DUE_WATCH_DAYS) return { tone: 'watch', label: `Vence en ${days} días`, days };
  return { tone: 'ok', label: `Vence en ${days} días`, days };
}
