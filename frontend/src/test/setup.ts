import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach } from 'vitest'

import { clearCsrfCookie } from './mockApi'

// findBy*/waitFor default to 1 s, which busy machines and CI runners can
// exceed on a first render (pages are loaded lazily, so a first render also
// waits for a module import). Assertions are unchanged; only the wait is longer.
configure({ asyncUtilTimeout: 10_000 })

afterEach(() => {
  cleanup()
  clearCsrfCookie()
})
