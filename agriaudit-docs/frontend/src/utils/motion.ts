import { useCallback, useEffect, useRef, useState } from 'react';

/** Si la persona ha pedido menos movimiento en su sistema, no se animan fondos, desplazamientos ni contadores. */
export function usePrefersReducedMotion(): boolean {
  const query = '(prefers-reduced-motion: reduce)';
  const [reduced, setReduced] = useState(
    () => typeof window !== 'undefined' && typeof window.matchMedia === 'function' && window.matchMedia(query).matches,
  );
  useEffect(() => {
    if (typeof window.matchMedia !== 'function') return;
    const media = window.matchMedia(query);
    const onChange = () => setReduced(media.matches);
    media.addEventListener('change', onChange);
    return () => media.removeEventListener('change', onChange);
  }, []);
  return reduced;
}

/** Número que sube desde 0 hasta {@code value} con una curva suave; sin animación si se pide menos movimiento. */
export function useCountUp(value: number, durationMs = 1100): number {
  const reduced = usePrefersReducedMotion();
  const [shown, setShown] = useState(reduced ? value : 0);
  useEffect(() => {
    if (reduced) {
      setShown(value);
      return;
    }
    const start = performance.now();
    let frame = 0;
    const tick = (now: number) => {
      const k = Math.min(1, (now - start) / durationMs);
      setShown(Math.round(value * (1 - Math.pow(1 - k, 3))));
      if (k < 1) frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [value, durationMs, reduced]);
  return shown;
}

/**
 * Transición entre el bloque de indicadores y el detalle del panel, al estilo de las páginas de producto de Apple:
 * - {@code --p} (0 a 1) en el elemento raíz indica cuánto se ha avanzado hacia el detalle; el CSS lo usa para
 *   desvanecer los indicadores y mover el fondo (paralaje) sin volver a pintar React.
 * - La rueda del ratón o el botón de la flecha desplazan suavemente de un bloque al otro, sin quedarse a medias.
 * - {@code revealed} se activa cuando el detalle entra en pantalla, para que sus tarjetas aparezcan en cascada.
 */
export function useDashboardScroll() {
  const rootRef = useRef<HTMLDivElement>(null);
  const detailRef = useRef<HTMLDivElement>(null);
  const reduced = usePrefersReducedMotion();
  const locked = useRef(false);
  const [revealed, setRevealed] = useState(typeof IntersectionObserver === 'undefined');

  /** Posición de la página en la que el detalle queda justo debajo de la barra superior. */
  const detailTop = useCallback(() => {
    const el = detailRef.current;
    if (!el) return 0;
    const bar = document.querySelector('header')?.getBoundingClientRect().height ?? 64;
    return Math.max(0, Math.round(el.getBoundingClientRect().top + window.scrollY - bar));
  }, []);

  const scrollToY = useCallback(
    (top: number) => {
      locked.current = true;
      window.scrollTo({ top, behavior: reduced ? 'auto' : 'smooth' });
      window.setTimeout(() => (locked.current = false), reduced ? 50 : 900);
    },
    [reduced],
  );

  const goDetail = useCallback(() => scrollToY(detailTop()), [scrollToY, detailTop]);
  const goTop = useCallback(() => scrollToY(0), [scrollToY]);

  useEffect(() => {
    let frame = 0;
    const update = () => {
      frame = 0;
      const top = detailTop();
      const p = top > 0 ? Math.min(1, Math.max(0, window.scrollY / top)) : 0;
      rootRef.current?.style.setProperty('--p', p.toFixed(3));
    };
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update);
    };
    const onWheel = (e: WheelEvent) => {
      if (e.ctrlKey || Math.abs(e.deltaY) < 8) return;
      const top = detailTop();
      if (top <= 0) return;
      if (locked.current) {
        // Mientras dura la transición se ignora la rueda para que un gesto largo no la interrumpa.
        if (window.scrollY < top) e.preventDefault();
        return;
      }
      if (e.deltaY > 0 && window.scrollY < top * 0.5) {
        e.preventDefault();
        scrollToY(top);
      } else if (e.deltaY < 0 && window.scrollY > 0 && window.scrollY <= top + 24) {
        e.preventDefault();
        scrollToY(0);
      }
    };
    update();
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll);
    window.addEventListener('wheel', onWheel, { passive: false });
    return () => {
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', onScroll);
      window.removeEventListener('wheel', onWheel);
      if (frame) cancelAnimationFrame(frame);
    };
  }, [detailTop, scrollToY]);

  useEffect(() => {
    const el = detailRef.current;
    if (!el || typeof IntersectionObserver === 'undefined') return;
    const observer = new IntersectionObserver(([entry]) => setRevealed(entry.isIntersecting), { threshold: 0.12 });
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return { rootRef, detailRef, goDetail, goTop, revealed, reduced };
}
