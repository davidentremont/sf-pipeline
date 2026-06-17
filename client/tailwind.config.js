/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        sf: {
          blue:   '#0176d3',
          dark:   '#032d60',
          light:  '#d8edff',
          green:  '#2e844a',
          red:    '#ba0517',
          yellow: '#fe9339',
          gray:   '#f3f2f2',
          border: '#dddbda',
        }
      }
    }
  },
  plugins: []
}
