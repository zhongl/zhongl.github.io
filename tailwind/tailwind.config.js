/** @type {import('tailwindcss').Config} */
const defaultTheme = require('tailwindcss/defaultTheme')

module.exports = {
    content: ["../**/*.html", "../**/*.md"],
    theme: {
    },
    plugins: [
        require('@tailwindcss/typography'),
    ],
}

