import { useMemo, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { AppLayout } from '../components/AppLayout'
import { Icon } from '../components/Icon'
import { LocationPicker, type LocationValue } from '../components/LocationPicker'
import { PhotoCapture } from '../components/PhotoCapture'
import { Button, Card, Field, inputClass, PageHeader } from '../components/ui'
import {
  listCategories,
  submitComplaint,
  type Category,
  type SubmitComplaintPayload,
} from '../lib/api/citizen'
import { discardUpload } from '../lib/api/uploads'
import { useImageUploads } from '../lib/api/useImageUploads'
import { errorMessage } from '../lib/api/errors'
import { formatCoordinates } from '../lib/utils/format'
import type { PhotoCandidate } from '../lib/utils/images'

const steps = ['Details', 'Photos', 'Location', 'Review'] as const
type StepIndex = 0 | 1 | 2 | 3

function flattenCategories(categories: Category[]): Category[] {
  return categories.flatMap((category) => [category, ...flattenCategories(category.children)])
}

export default function ReportIssuePage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [step, setStep] = useState<StepIndex>(0)
  const [errors, setErrors] = useState<Record<string, string>>({})

  const [description, setDescription] = useState('')
  const [additionalInfo, setAdditionalInfo] = useState('')
  const [categoryCode, setCategoryCode] = useState('')
  const [subcategoryCode, setSubcategoryCode] = useState('')
  const [photos, setPhotos] = useState<PhotoCandidate[]>([])
  const [location, setLocation] = useState<LocationValue | null>(null)
  const [addressLine, setAddressLine] = useState('')
  const [city, setCity] = useState('')
  const [state, setState] = useState('')
  const [postalCode, setPostalCode] = useState('')
  const [country, setCountry] = useState('India')
  const [ward, setWard] = useState('')
  const [zone, setZone] = useState('')
  const { uploadAll, progress: uploadProgress, reset: resetUpload } = useImageUploads()

  const categories = useQuery({
    queryKey: ['meta', 'categories'],
    queryFn: () => listCategories(),
  })
  const allCategories = useMemo(
    () => flattenCategories(categories.data ?? []),
    [categories.data],
  )
  const selectedCategory = allCategories.find((category) => category.code === categoryCode)
  const selectedSubcategory = allCategories.find((category) => category.code === subcategoryCode)

  const mutation = useMutation({
    mutationFn: submitComplaint,
    onSuccess: async (complaint) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['citizen', 'complaints'] }),
        queryClient.invalidateQueries({ queryKey: ['notifications', 'unread'] }),
      ])
      navigate(`/complaints/${complaint.id}/submitted`, { replace: true })
    },
  })

  const busy = mutation.isPending || uploadProgress.stage === 'uploading'

  function validateCurrentStep(): boolean {
    const next: Record<string, string> = {}
    if (step === 0) {
      if (description.trim().length < 10) {
        next.description = 'Please describe the issue in at least 10 characters.'
      }
      if (description.length > 2000) next.description = 'Keep the description under 2000 characters.'
    }
    if (step === 2 && !location) {
      next.location = 'Choose a location using GPS, the map, or coordinates.'
    }
    setErrors(next)
    return Object.keys(next).length === 0
  }

  function nextStep() {
    if (!validateCurrentStep()) return
    setStep((current) => Math.min(current + 1, 3) as StepIndex)
  }

  function previousStep() {
    setErrors({})
    setStep((current) => Math.max(current - 1, 0) as StepIndex)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!location) {
      setErrors({ location: 'A location is required before submission.' })
      setStep(2)
      return
    }

    setErrors({})
    resetUpload()
    let imageIds: string[] = []
    try {
      // No complaint is created until every selected photo is verified by the backend.
      imageIds = await uploadAll(photos)
    } catch {
      return
    }

    const payload: SubmitComplaintPayload = {
      description: description.trim(),
      additionalInfo: additionalInfo.trim() || undefined,
      latitude: location.latitude,
      longitude: location.longitude,
      locationSource: location.source,
      addressLine: addressLine.trim() || undefined,
      city: city.trim() || undefined,
      state: state.trim() || undefined,
      postalCode: postalCode.trim() || undefined,
      country: country.trim() || undefined,
      ward: ward.trim() || undefined,
      zone: zone.trim() || undefined,
      categoryCode: categoryCode || undefined,
      subcategoryCode: subcategoryCode || undefined,
      imageIds,
    }
    mutation.mutate(payload, {
      onError: async () => {
        // The complaint was never created; do not leave verified orphan uploads behind.
        await Promise.allSettled(imageIds.map((id) => discardUpload(id)))
      },
    })
  }

  return (
    <AppLayout>
      <PageHeader
        eyebrow="New report"
        title="Report an issue"
        description="Four short steps. Review everything before it reaches the backend."
      />

      <div className="mb-7 flex flex-wrap gap-2 sm:gap-3">
        {steps.map((label, index) => (
          <button
            key={label}
            type="button"
            onClick={() => {
              if (index < step) setStep(index as StepIndex)
            }}
            disabled={index > step || busy}
            className={`flex items-center gap-2 rounded-xl border px-3 py-2 text-xs font-semibold transition ${
              index === step
                ? 'border-brand-500 bg-brand-50 text-brand-800'
                : index < step
                  ? 'border-brand-200 bg-white text-brand-700'
                  : 'border-stone-200 bg-white text-stone-400'
            }`}
          >
            <span
              className={`flex h-5 w-5 items-center justify-center rounded-full text-[10px] ${
                index <= step ? 'bg-brand-700 text-white' : 'bg-stone-100 text-stone-400'
              }`}
            >
              {index < step ? <Icon name="check" className="h-3 w-3" /> : index + 1}
            </span>
            {label}
          </button>
        ))}
      </div>

      <form onSubmit={submit}>
        <Card className="p-5 sm:p-7">
          {step === 0 && (
            <div className="animate-fade-in space-y-5">
              <div>
                <h2 className="text-lg font-semibold text-stone-900">Tell us what happened</h2>
                <p className="mt-1 text-sm text-stone-600">
                  Clear, specific descriptions help departments act faster.
                </p>
              </div>
              <Field
                label="Description"
                required
                error={errors.description}
                hint={`${description.length}/2000 characters`}
              >
                <textarea
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  className={`${inputClass} min-h-36 resize-y`}
                  placeholder="For example: A large pothole near the school gate is causing traffic and safety issues."
                />
              </Field>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Category">
                  <select
                    value={categoryCode}
                    onChange={(event) => {
                      setCategoryCode(event.target.value)
                      setSubcategoryCode('')
                    }}
                    className={inputClass}
                  >
                    <option value="">Choose a category (optional)</option>
                    {(categories.data ?? []).map((category) => (
                      <option key={category.id} value={category.code}>
                        {category.name}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Subcategory">
                  <select
                    value={subcategoryCode}
                    onChange={(event) => setSubcategoryCode(event.target.value)}
                    className={inputClass}
                    disabled={!selectedCategory?.children.length}
                  >
                    <option value="">None</option>
                    {selectedCategory?.children.map((child) => (
                      <option key={child.id} value={child.code}>
                        {child.name}
                      </option>
                    ))}
                  </select>
                </Field>
              </div>
              {categories.isError && (
                <p className="text-xs font-medium text-amber-700">
                  Categories could not be loaded. You can still submit without one.
                </p>
              )}
              <Field label="Additional information" hint="Optional context, landmarks, or timing.">
                <textarea
                  value={additionalInfo}
                  onChange={(event) => setAdditionalInfo(event.target.value)}
                  className={`${inputClass} min-h-24 resize-y`}
                />
              </Field>
            </div>
          )}

          {step === 1 && (
            <div className="animate-fade-in space-y-5">
              <div>
                <h2 className="text-lg font-semibold text-stone-900">Add photos</h2>
                <p className="mt-1 text-sm text-stone-600">
                  Show the issue from a useful angle if you can.
                </p>
              </div>
              <PhotoCapture photos={photos} onPhotosChange={setPhotos} />
            </div>
          )}

          {step === 2 && (
            <div className="animate-fade-in space-y-6">
              <LocationPicker value={location} onChange={setLocation} error={errors.location} />
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="sm:col-span-2">
                  <Field label="Address or landmark">
                    <input
                      value={addressLine}
                      onChange={(event) => setAddressLine(event.target.value)}
                      className={inputClass}
                      placeholder="Street, landmark, nearby shop…"
                    />
                  </Field>
                </div>
                <Field label="City">
                  <input value={city} onChange={(event) => setCity(event.target.value)} className={inputClass} />
                </Field>
                <Field label="State">
                  <input value={state} onChange={(event) => setState(event.target.value)} className={inputClass} />
                </Field>
                <Field label="Postal code">
                  <input
                    value={postalCode}
                    onChange={(event) => setPostalCode(event.target.value)}
                    className={inputClass}
                  />
                </Field>
                <Field label="Country">
                  <input value={country} onChange={(event) => setCountry(event.target.value)} className={inputClass} />
                </Field>
                <Field label="Ward">
                  <input value={ward} onChange={(event) => setWard(event.target.value)} className={inputClass} />
                </Field>
                <Field label="Zone">
                  <input value={zone} onChange={(event) => setZone(event.target.value)} className={inputClass} />
                </Field>
              </div>
            </div>
          )}

          {step === 3 && (
            <div className="animate-fade-in space-y-5">
              <div>
                <h2 className="text-lg font-semibold text-stone-900">Review your report</h2>
                <p className="mt-1 text-sm text-stone-600">
                  Check the details below. You can still go back and change anything.
                </p>
              </div>
              <dl className="grid gap-4 sm:grid-cols-2">
                <div className="rounded-xl border border-stone-200 p-4 sm:col-span-2">
                  <dt className="text-xs font-semibold text-stone-500">Description</dt>
                  <dd className="mt-1 whitespace-pre-wrap text-sm text-stone-900">{description}</dd>
                </div>
                <div className="rounded-xl border border-stone-200 p-4">
                  <dt className="text-xs font-semibold text-stone-500">Category</dt>
                  <dd className="mt-1 text-sm text-stone-900">
                    {selectedCategory?.name ?? 'Not specified'}
                    {selectedSubcategory ? ` · ${selectedSubcategory.name}` : ''}
                  </dd>
                </div>
                <div className="rounded-xl border border-stone-200 p-4">
                  <dt className="text-xs font-semibold text-stone-500">Location</dt>
                  <dd className="mt-1 font-mono text-sm text-stone-900">
                    {location ? formatCoordinates(location.latitude, location.longitude) : '—'}
                  </dd>
                  <dd className="mt-1 text-xs text-stone-500">{location?.source}</dd>
                </div>
                <div className="rounded-xl border border-stone-200 p-4">
                  <dt className="text-xs font-semibold text-stone-500">Address</dt>
                  <dd className="mt-1 text-sm text-stone-900">
                    {[addressLine, city, state, postalCode, country].filter(Boolean).join(', ') || '—'}
                  </dd>
                </div>
                <div className="rounded-xl border border-stone-200 p-4">
                  <dt className="text-xs font-semibold text-stone-500">Photos</dt>
                  <dd className="mt-1 text-sm text-stone-900">
                    {photos.length
                      ? `${photos.length} photo${photos.length === 1 ? '' : 's'} will be uploaded and verified before submission`
                      : 'None'}
                  </dd>
                </div>
              </dl>

              <div className="space-y-3 rounded-xl border border-stone-200 border-l-4 border-l-clay-500 bg-stone-50 p-4 text-xs leading-relaxed text-stone-800">
                <p className="flex gap-2 font-semibold">
                  <Icon name="shield" className="h-4 w-4 shrink-0" />
                  How photos and analysis work
                </p>
                <p>
                  Photos are uploaded to private storage and verified by the server before the
                  complaint is created. If a photo fails verification, nothing is submitted.
                </p>
                <p>
                  AI analysis is advisory only. It never assigns a department, never infers a
                  precise location from photos, and low-confidence results are flagged for human
                  review. If analysis fails, your complaint is still saved.
                </p>
              </div>

              {uploadProgress.stage === 'uploading' && (
                <p className="text-xs font-medium text-brand-700" role="status">
                  Uploading and verifying {uploadProgress.completed} of {uploadProgress.total} photos…
                </p>
              )}

              {uploadProgress.stage === 'failed' && (
                <div
                  role="alert"
                  className="rounded-xl border border-red-200 bg-red-50 px-3 py-2.5 text-sm text-red-700"
                >
                  {uploadProgress.error ?? 'Photo upload failed. No complaint was submitted.'}
                </div>
              )}

              {mutation.isError && (
                <div
                  role="alert"
                  className="rounded-xl border border-red-200 bg-red-50 px-3 py-2.5 text-sm text-red-700"
                >
                  {errorMessage(mutation.error, 'Could not submit the complaint')}
                </div>
              )}
            </div>
          )}

          <div className="mt-8 flex flex-col-reverse gap-3 border-t border-stone-200 pt-5 sm:flex-row sm:items-center sm:justify-between">
            <Button
              type="button"
              variant="ghost"
              onClick={previousStep}
              disabled={step === 0 || busy}
            >
              <Icon name="arrow-left" className="h-4 w-4" />
              Back
            </Button>
            {step < 3 ? (
              <Button type="button" onClick={nextStep} disabled={busy}>
                Continue
                <Icon name="arrow-right" className="h-4 w-4" />
              </Button>
            ) : (
              <Button type="submit" loading={busy}>
                {uploadProgress.stage === 'uploading'
                  ? 'Uploading photos…'
                  : mutation.isPending
                    ? 'Submitting…'
                    : 'Submit complaint'}
              </Button>
            )}
          </div>
        </Card>
      </form>
    </AppLayout>
  )
}
