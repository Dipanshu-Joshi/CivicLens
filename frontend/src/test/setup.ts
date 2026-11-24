import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

if (!URL.createObjectURL) {
  URL.createObjectURL = () => 'blob:test-preview'
}
if (!URL.revokeObjectURL) {
  URL.revokeObjectURL = () => undefined
}

afterEach(() => {
  cleanup()
})
