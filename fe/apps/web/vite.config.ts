/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react-swc';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  test: {
    // The suites under testing/ were written for an earlier Next.js-based
    // frontend and no longer resolve against this Vite SPA. They are excluded
    // rather than deleted so they can be ported back one at a time; everything
    // outside that folder must keep passing.
    exclude: ['node_modules/**', 'dist/**', 'testing/**'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
  define: {
    'process.env': {},
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@gioitruyen/ui': path.resolve(__dirname, '../../packages/ui/src/index.tsx'),
    },
  },
  server: {
    port: 3000,
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  }
});
