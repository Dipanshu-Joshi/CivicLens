export const MAX_PHOTOS = 5
export const MAX_PHOTO_BYTES = 10 * 1024 * 1024
export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const

export interface PhotoCandidate {
  file: File
  previewUrl: string
}

export interface ImageValidationResult {
  accepted: PhotoCandidate[]
  rejected: Array<{ name: string; reason: string }>
}

/** Client-side pre-validation; the backend repeats all checks authoritatively. */
export function validateImageFiles(
  files: File[],
  existing: PhotoCandidate[] = [],
): ImageValidationResult {
  const accepted: PhotoCandidate[] = []
  const rejected: Array<{ name: string; reason: string }> = []
  const seen = new Set(
    existing.map((item) => `${item.file.name}:${item.file.size}:${item.file.lastModified}`),
  )

  let slots = Math.max(0, MAX_PHOTOS - existing.length)

  for (const file of files) {
    const key = `${file.name}:${file.size}:${file.lastModified}`
    if (seen.has(key)) {
      rejected.push({ name: file.name, reason: 'Already selected' })
      continue
    }
    if (!ALLOWED_IMAGE_TYPES.includes(file.type as (typeof ALLOWED_IMAGE_TYPES)[number])) {
      rejected.push({ name: file.name, reason: 'Use JPEG, PNG, or WebP' })
      continue
    }
    if (file.size > MAX_PHOTO_BYTES) {
      rejected.push({ name: file.name, reason: 'Larger than 10 MB' })
      continue
    }
    if (slots === 0) {
      rejected.push({ name: file.name, reason: `Maximum ${MAX_PHOTOS} photos` })
      continue
    }
    seen.add(key)
    slots -= 1
    accepted.push({ file, previewUrl: URL.createObjectURL(file) })
  }

  return { accepted, rejected }
}

export function releasePreviews(photos: PhotoCandidate[]): void {
  photos.forEach((photo) => URL.revokeObjectURL(photo.previewUrl))
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
