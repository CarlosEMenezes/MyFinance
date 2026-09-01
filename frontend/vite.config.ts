import { fileURLToPath, URL } from 'node:url';

import react from '@vitejs/plugin-react';
// vitest/config re-exports Vite's defineConfig with the `test` block typed.
import { defineConfig } from 'vitest/config';

const COVERAGE_FLOOR_OVERALL = 70;
const COVERAGE_FLOOR_PURE_MODULES = 90;

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8085',
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      // Spec §0.3: the build fails below these floors.
      thresholds: {
        lines: COVERAGE_FLOOR_OVERALL,
        functions: COVERAGE_FLOOR_OVERALL,
        branches: COVERAGE_FLOOR_OVERALL,
        statements: COVERAGE_FLOOR_OVERALL,
        'src/lib/**': {
          lines: COVERAGE_FLOOR_PURE_MODULES,
          functions: COVERAGE_FLOOR_PURE_MODULES,
          branches: COVERAGE_FLOOR_PURE_MODULES,
          statements: COVERAGE_FLOOR_PURE_MODULES,
        },
      },
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/main.tsx',
        'src/test/**',
        'src/**/*.d.ts',
        'src/**/*.types.ts',
        'src/**/index.ts',
      ],
    },
  },
});
