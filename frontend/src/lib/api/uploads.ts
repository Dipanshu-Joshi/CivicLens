import { api } from './client'
import type { ImageKind } from './citizen'
import { demoImageIdFromUploadUrl, isDemoUploadUrl, storeDemoUploadPreview } from '../../demo/adapter'
import { DEMO_ENABLED } from '../../demo/feature'

/** Server-validated image upload contract (Phase 5). */
export type UploadStatus = 'PENDING' | 'VERIFIED' | 'REJECTED'

export interface PresignUploadRequest {
  fileName: string
  contentType: string
  sizeBytes: number
  imageKind: ImageKind
}

export interface PresignUploadResponse {
  imageId: string
  uploadUrl: string
  storageKey: string
  contentType: string
  expiresAt: string
  headers?: Record<string, string>
}

export interface UploadedImage {
  imageId: string
  complaintId: string | null
  storageKey: string
  contentType: string
  sizeBytes: number
  width: number | null
  height: number | null
  uploadStatus: UploadStatus
  createdAt: string
}

export interface ViewableImage {
  imageId: string
  url: string
  expiresAt: string
}

export async function presignUpload(payload: PresignUploadRequest): Promise<PresignUploadResponse> {
  const { data } = await api.post<PresignUploadResponse>('/uploads/presign', payload)
  return data
}

/**
 * Sends bytes directly to the S3-compatible provider using the short-lived
 * presigned URL. No CivicSense auth header is sent — the URL itself is the
 * capability. A non-2xx response is a real failure and must be surfaced.
 */
export async function putObject(
  uploadUrl: string,
  file: File,
  contentType: string,
  signedHeaders: Record<string, string> = {},
): Promise<void> {
  if (DEMO_ENABLED && isDemoUploadUrl(uploadUrl)) {
    const imageId = demoImageIdFromUploadUrl(uploadUrl)
    if (imageId) {
      await storeDemoUploadPreview(imageId, file)
      return
    }
  }
  const response = await fetch(uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': contentType, ...signedHeaders },
    body: file,
  })
  if (!response.ok) {
    throw new Error(`Storage rejected the upload (HTTP ${response.status})`)
  }
}

export async function confirmUpload(imageId: string): Promise<UploadedImage> {
  const { data } = await api.post<UploadedImage>(`/uploads/${imageId}/confirm`)
  return data
}

export async function discardUpload(imageId: string): Promise<void> {
  await api.delete(`/uploads/${imageId}`)
}

export async function getImageViewUrl(imageId: string): Promise<ViewableImage> {
  const { data } = await api.get<ViewableImage>(`/uploads/${imageId}/url`)
  return data
}
