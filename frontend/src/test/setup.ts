import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

afterEach(() => {
  cleanup()
  // Page addresses live in jsdom's history, which otherwise carries over between tests.
  window.history.replaceState(null, '', '/')
})
