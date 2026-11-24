import { describe, expect, it, vi } from 'vitest'
import { demoEnabledFor } from '../demo/feature'

describe('production exclusion', () => {
  it('never installs the demo adapter when the dev gate is false', async () => {
    expect(demoEnabledFor(false)).toBe(false)
    expect(demoEnabledFor(true)).toBe(true)

    vi.resetModules()
    vi.doMock('../demo/feature', () => ({
      DEMO_ENABLED: false,
      DEMO_STORAGE_VERSION: 1,
      DEMO_CREDENTIALS: {},
      DEMO_ACCOUNTS: [],
      demoEnabledFor,
    }))

    try {
      const axios = (await import('axios')).default
      const { api } = await import('../lib/api/client')
      expect(api.defaults.adapter).toEqual(axios.defaults.adapter)

      const { installDemoAdapter } = await import('../demo/adapter')
      const instance = axios.create()
      const before = instance.defaults.adapter
      installDemoAdapter(instance)
      expect(instance.defaults.adapter).toBe(before)
    } finally {
      vi.doUnmock('../demo/feature')
      vi.resetModules()
    }
  })

  it('ships a no-op production stub with no demo credentials or UI', async () => {
    const stub = await import('../demo/prodStub')
    expect(stub.DEMO_ENABLED).toBe(false)
    expect(stub.DEMO_ACCOUNTS).toHaveLength(0)
    expect(Object.keys(stub.DEMO_CREDENTIALS)).toHaveLength(0)
    expect(stub.demoEnabledFor(true)).toBe(false)
    expect(stub.isDemoUploadUrl('demo://upload/example')).toBe(false)
    expect(stub.demoImageIdFromUploadUrl('demo://upload/example')).toBeNull()

    const axios = (await import('axios')).default
    const instance = axios.create()
    const before = instance.defaults.adapter
    stub.installDemoAdapter(instance)
    expect(instance.defaults.adapter).toBe(before)
    await expect(stub.storeDemoUploadPreview('example', new Blob(['demo']))).resolves.toBeUndefined()
  })
})
