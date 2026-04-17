import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';

// ---- matchMedia ----
// jsdom doesn't implement matchMedia; ThemeProvider uses it.
if (!window.matchMedia) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }),
  });
}

// ---- HTMLCanvasElement.getContext ----
// axe-core's color-contrast check calls getContext('2d'); jsdom doesn't
// implement it. Return a no-op context so axe falls back gracefully.
HTMLCanvasElement.prototype.getContext = vi.fn().mockReturnValue({
  fillStyle: '',
  fillRect: () => {},
  getImageData: () => ({ data: new Uint8ClampedArray(4) }),
  putImageData: () => {},
  createImageData: () => ({ data: new Uint8ClampedArray(4) }),
  measureText: () => ({ width: 0 }),
  font: '',
}) as unknown as typeof HTMLCanvasElement.prototype.getContext;

// ---- Reset localStorage + DOM between tests ----
beforeEach(() => {
  localStorage.clear();
  document.documentElement.classList.remove('dark');
});

afterEach(() => {
  cleanup();
});
