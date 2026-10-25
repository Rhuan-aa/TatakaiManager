import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// Fontes auto-hospedadas (variable) — sem requisições externas em runtime.
import '@fontsource-variable/inter'
import '@fontsource/anton'
import './index.css'
import App from './App.jsx'
import { ErrorBoundary } from './components/ErrorBoundary.jsx'

// Impressão/PDF: abre os <details> fechados e fecha de novo depois.
window.addEventListener('beforeprint', () => {
  document.querySelectorAll('details:not([open])').forEach((d) => { d.dataset.printOpened = ''; d.open = true; })
})
window.addEventListener('afterprint', () => {
  document.querySelectorAll('details[data-print-opened]').forEach((d) => { delete d.dataset.printOpened; d.open = false })
})

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
  </StrictMode>,
)
