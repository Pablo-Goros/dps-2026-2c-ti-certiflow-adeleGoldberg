import '@fontsource-variable/public-sans'
import './styles.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { HashRouter } from 'react-router-dom'
import { App } from './App'
import { PartyProvider } from './components/Party'
import { ToastProvider } from './components/ui'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <HashRouter>
      <ToastProvider>
        <PartyProvider>
          <App />
        </PartyProvider>
      </ToastProvider>
    </HashRouter>
  </StrictMode>,
)
