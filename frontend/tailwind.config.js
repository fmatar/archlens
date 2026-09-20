/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{svelte,js,ts}'],
  theme: {
    extend: {
      colors: {
        canvas: '#0f172a',
        surface: '#1e293b',
        border: '#334155',
        violation: '#ef4444',
        healthy: '#22c55e'
      }
    },
  },
  plugins: [],
}
