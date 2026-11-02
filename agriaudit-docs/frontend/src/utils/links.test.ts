import { describe, expect, it } from 'vitest';
import { isWebLink } from './links';

describe('isWebLink', () => {
  it('acepta direcciones web completas', () => {
    expect(isWebLink('https://www.globalgap.org/es/')).toBe(true);
    expect(isWebLink('http://example.org')).toBe(true);
  });

  it('rechaza enlaces que ejecutan código, sin protocolo o vacíos', () => {
    expect(isWebLink('javascript:alert(1)')).toBe(false);
    expect(isWebLink('data:text/html,hola')).toBe(false);
    expect(isWebLink('www.globalgap.org')).toBe(false);
    expect(isWebLink('')).toBe(false);
    expect(isWebLink(null)).toBe(false);
  });
});
