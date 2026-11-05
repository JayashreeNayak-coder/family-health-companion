import { useEffect, useState } from 'react'
import { isBackendUp } from './api/health'

type Connection = 'loading' | 'connected' | 'unavailable'

export function App() {
  const [connection, setConnection] = useState<Connection>('loading')

  useEffect(() => {
    let ignore = false

    isBackendUp().then((up) => {
      if (!ignore) {
        setConnection(up ? 'connected' : 'unavailable')
      }
    })

    return () => {
      ignore = true
    }
  }, [])

  return (
    <main>
      <h1>Family Health Companion</h1>
      {connection === 'loading' && <p role="status">Checking backend connection…</p>}
      {connection === 'connected' && <p role="status">Backend connected</p>}
      {connection === 'unavailable' && (
        <p role="alert">Backend unavailable. Please try again later.</p>
      )}
    </main>
  )
}
