import { useState } from 'react'
import { confirmUpload, discardUpload, presignUpload, putObject } from './uploads'
import type { PhotoCandidate } from '../utils/images'

export interface UploadProgress {
  stage: 'idle' | 'uploading' | 'done' | 'failed'
  completed: number
  total: number
  error: string | null
}

const IDLE: UploadProgress = { stage: 'idle', completed: 0, total: 0, error: null }

/**
 * Uploads staged photos one by one and returns only backend-confirmed image ids.
 * A partial failure discards already-uploaded objects so no orphan is attached.
 */
export function useImageUploads() {
  const [progress, setProgress] = useState<UploadProgress>(IDLE)

  function reset() {
    setProgress(IDLE)
  }

  async function uploadAll(photos: PhotoCandidate[]): Promise<string[]> {
    if (!photos.length) {
      setProgress(IDLE)
      return []
    }

    const confirmed: string[] = []
    setProgress({ stage: 'uploading', completed: 0, total: photos.length, error: null })
    try {
      for (const photo of photos) {
        const presigned = await presignUpload({
          fileName: photo.file.name,
          contentType: photo.file.type,
          sizeBytes: photo.file.size,
          imageKind: 'CITIZEN',
        })
        await putObject(presigned.uploadUrl, photo.file, photo.file.type, presigned.headers)
        await confirmUpload(presigned.imageId)
        confirmed.push(presigned.imageId)
        setProgress({
          stage: 'uploading',
          completed: confirmed.length,
          total: photos.length,
          error: null,
        })
      }
      setProgress({ stage: 'done', completed: photos.length, total: photos.length, error: null })
      return confirmed
    } catch (error) {
      await Promise.allSettled(confirmed.map((id) => discardUpload(id)))
      const message = error instanceof Error ? error.message : 'Image upload failed'
      setProgress({
        stage: 'failed',
        completed: confirmed.length,
        total: photos.length,
        error: message,
      })
      throw error
    }
  }

  return { uploadAll, progress, reset }
}
