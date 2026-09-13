import { describe, expect, it } from 'vitest';
import { LOGIN_SCENES, greetingForDate, sceneForDate } from './loginScenes';

describe('escenas del login', () => {
  it('hay una escena distinta para cada día de la semana', () => {
    expect(LOGIN_SCENES.map((s) => s.weekday)).toEqual([0, 1, 2, 3, 4, 5, 6]);
    expect(new Set(LOGIN_SCENES.map((s) => s.image)).size).toBe(7);
  });

  it('elige la escena según el día de la semana', () => {
    // 14-09-2026 es lunes; 20-09-2026, domingo
    expect(sceneForDate(new Date(2026, 8, 14)).dayLabel).toBe('Lunes');
    expect(sceneForDate(new Date(2026, 8, 17)).title).toBe('Uva de mesa en la hora dorada');
    expect(sceneForDate(new Date(2026, 8, 20)).place).toBe('Cieza');
  });

  it('saluda según la hora', () => {
    expect(greetingForDate(new Date(2026, 8, 14, 9))).toBe('Buenos días');
    expect(greetingForDate(new Date(2026, 8, 14, 16))).toBe('Buenas tardes');
    expect(greetingForDate(new Date(2026, 8, 14, 23))).toBe('Buenas noches');
    expect(greetingForDate(new Date(2026, 8, 14, 3))).toBe('Buenas noches');
  });
});
