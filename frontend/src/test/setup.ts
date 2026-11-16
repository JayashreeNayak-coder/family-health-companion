import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

afterEach(() => {
  // Tests marked for the node environment (the edge proxy) have no page to clean up.
  if (typeof window === 'undefined') return
  cleanup()
  // Page addresses live in jsdom's history, which otherwise carries over between tests.
  window.history.replaceState(null, '', '/')
})
