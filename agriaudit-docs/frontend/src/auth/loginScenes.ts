import domingo from '../assets/login/domingo-melocotoneros.svg';
import lunes from '../assets/login/lunes-naranjos.svg';
import martes from '../assets/login/martes-limoneros.svg';
import miercoles from '../assets/login/miercoles-almendros.svg';
import jueves from '../assets/login/jueves-uva.svg';
import viernes from '../assets/login/viernes-lechugas.svg';
import sabado from '../assets/login/sabado-olivar.svg';

export interface LoginScene {
  /** Día de la semana, como en {@link Date.getDay} (0 = domingo). */
  weekday: number;
  dayLabel: string;
  title: string;
  place: string;
  image: string;
}

/** Ilustraciones del fondo del login: una escena agrícola y ecológica por día de la semana. */
export const LOGIN_SCENES: readonly LoginScene[] = [
  { weekday: 0, dayLabel: 'Domingo', title: 'Melocotoneros y colmenas al atardecer', place: 'Cieza', image: domingo },
  { weekday: 1, dayLabel: 'Lunes', title: 'Naranjos al amanecer', place: 'Vega del Segura', image: lunes },
  { weekday: 2, dayLabel: 'Martes', title: 'Limoneros a mediodía', place: 'Huerta de Murcia', image: martes },
  { weekday: 3, dayLabel: 'Miércoles', title: 'Almendros en flor', place: 'Noroeste murciano', image: miercoles },
  { weekday: 4, dayLabel: 'Jueves', title: 'Uva de mesa en la hora dorada', place: 'Valle del Guadalentín', image: jueves },
  { weekday: 5, dayLabel: 'Viernes', title: 'Lechugas en bancales', place: 'Campo de Cartagena', image: viernes },
  { weekday: 6, dayLabel: 'Sábado', title: 'Olivar ecológico con cubierta vegetal', place: 'Mula', image: sabado },
];

export function sceneForDate(date: Date = new Date()): LoginScene {
  return LOGIN_SCENES[date.getDay()];
}

export function greetingForDate(date: Date = new Date()): string {
  const hour = date.getHours();
  if (hour >= 6 && hour < 14) return 'Buenos días';
  if (hour >= 14 && hour < 21) return 'Buenas tardes';
  return 'Buenas noches';
}
