import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import { Icon } from './Icon'
import {
  MAX_PHOTOS,
  formatBytes,
  validateImageFiles,
  type PhotoCandidate,
} from '../lib/utils/images'
import { DEMO_ENABLED } from '../demo/feature'

export function PhotoCapture({
  photos,
  onPhotosChange,
}: {
  photos: PhotoCandidate[]
  onPhotosChange: (photos: PhotoCandidate[]) => void
}) {
  const cameraInput = useRef<HTMLInputElement>(null)
  const galleryInput = useRef<HTMLInputElement>(null)
  const photosRef = useRef(photos)
  const [rejected, setRejected] = useState<Array<{ name: string; reason: string }>>([])

  useEffect(() => {
    photosRef.current = photos
  }, [photos])

  useEffect(() => {
    return () => {
      photosRef.current.forEach((photo) => URL.revokeObjectURL(photo.previewUrl))
    }
  }, [])

  function handleSelection(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? [])
    event.target.value = ''
    if (!files.length) return
    const result = validateImageFiles(files, photos)
    setRejected(result.rejected)
    if (result.accepted.length) {
      onPhotosChange([...photos, ...result.accepted])
    }
  }

  function removePhoto(photo: PhotoCandidate) {
    URL.revokeObjectURL(photo.previewUrl)
    setRejected([])
    onPhotosChange(photos.filter((item) => item !== photo))
  }

  return (
    <div>
      <input
        ref={cameraInput}
        type="file"
        accept="image/*"
        capture="environment"
        multiple
        className="hidden"
        onChange={handleSelection}
        aria-label="Capture photo with camera"
      />
      <input
        ref={galleryInput}
        type="file"
        accept="image/*"
        multiple
        className="hidden"
        onChange={handleSelection}
        aria-label="Upload photos from device"
      />

      <div className="grid gap-3 sm:grid-cols-2">
        <button
          type="button"
          onClick={() => cameraInput.current?.click()}
          className="group flex items-center gap-3 rounded-2xl border border-dashed border-brand-300 bg-brand-50/60 p-4 text-left transition hover:border-brand-500 hover:bg-brand-50"
        >
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-white text-brand-700 shadow-sm">
            <Icon name="camera" />
          </span>
          <span>
            <span className="block text-sm font-semibold text-stone-900">Use camera</span>
            <span className="block text-xs text-stone-600">Capture the issue on your device</span>
          </span>
        </button>
        <button
          type="button"
          onClick={() => galleryInput.current?.click()}
          className="group flex items-center gap-3 rounded-2xl border border-dashed border-stone-300 bg-stone-50 p-4 text-left transition hover:border-brand-500 hover:bg-brand-50/50"
        >
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-white text-stone-600 shadow-sm">
            <Icon name="upload" />
          </span>
          <span>
            <span className="block text-sm font-semibold text-stone-900">Upload photos</span>
            <span className="block text-xs text-stone-600">JPEG, PNG, WebP · up to 8 MB each</span>
          </span>
        </button>
      </div>

      {photos.length > 0 && (
        <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {photos.map((photo, index) => (
            <div
              key={photo.previewUrl}
              className="group relative overflow-hidden rounded-xl border border-stone-200 bg-stone-100"
            >
              <img
                src={photo.previewUrl}
                alt={`Selected photo ${index + 1}`}
                className="h-28 w-full object-cover"
              />
              <button
                type="button"
                onClick={() => removePhoto(photo)}
                aria-label={`Remove photo ${index + 1}`}
                className="absolute top-2 right-2 rounded-full bg-white/95 p-1.5 text-stone-700 shadow-sm transition hover:bg-red-50 hover:text-red-600"
              >
                <Icon name="x" className="h-3.5 w-3.5" />
              </button>
              <p className="truncate bg-white px-2 py-1.5 text-[10px] text-stone-500">
                {photo.file.name} · {formatBytes(photo.file.size)}
              </p>
            </div>
          ))}
        </div>
      )}

      <p className="mt-3 text-[11px] text-stone-500">
        {photos.length}/{MAX_PHOTOS} photos selected. Photos are validated and previewed locally.
      </p>

      {rejected.length > 0 && (
        <div className="mt-3 rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-700">
          <p className="font-semibold">Some files were not added:</p>
          <ul className="mt-1 list-inside list-disc">
            {rejected.map((item) => (
              <li key={`${item.name}-${item.reason}`}>
                {item.name}: {item.reason}
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="mt-3 flex gap-2 rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800">
        <Icon name="info" className="mt-0.5 h-4 w-4 shrink-0" />
        {DEMO_ENABLED ? (
          <p>
            Demo mode keeps a reduced local preview in this browser and simulates the presign,
            upload and confirmation steps. No object storage or backend endpoint is contacted; the
            preview is only for this demo session.
          </p>
        ) : (
          <p>
            Photos are uploaded through the secure presign flow and attached to the complaint after
            the server confirms the upload.
          </p>
        )}
      </div>
    </div>
  )
}
