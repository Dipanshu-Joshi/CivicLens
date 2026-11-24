import { useEffect, useRef, useState, type MouseEvent } from 'react'
import type { LocationSource } from '../lib/api/citizen'
import {
  MAX_ZOOM,
  MIN_ZOOM,
  TILE_SIZE,
  clampZoom,
  latLonToTile,
  panCenter,
  pointToLatLon,
  tileToLatLon,
  tileUrl,
  type LatLon,
} from '../lib/utils/geo'
import { Icon } from './Icon'
import { Button, Field, inputClass } from './ui'

export interface LocationValue extends LatLon {
  source: 'GPS' | 'MANUAL' | 'GEOCODED'
}

const DEFAULT_CENTER: LatLon = { latitude: 28.6139, longitude: 77.209 }
const TILE_TEMPLATE =
  import.meta.env.VITE_MAP_TILE_URL ?? 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'
const TILE_ATTRIBUTION =
  import.meta.env.VITE_MAP_TILE_ATTRIBUTION ?? '© OpenStreetMap contributors'

export function LocationPicker({
  value,
  onChange,
  error,
}: {
  value: LocationValue | null
  onChange: (value: LocationValue | null) => void
  error?: string
}) {
  const viewportRef = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(640)
  const [zoom, setZoom] = useState(14)
  const [center, setCenter] = useState<LatLon>(
    value ? { latitude: value.latitude, longitude: value.longitude } : DEFAULT_CENTER,
  )
  const [locating, setLocating] = useState(false)
  const [gpsError, setGpsError] = useState<string | null>(null)

  useEffect(() => {
    const element = viewportRef.current
    if (!element) return
    setWidth(element.clientWidth || 640)
    if (typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver((entries) => {
      const entry = entries[0]
      if (entry) setWidth(entry.contentRect.width || 640)
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  const centerTile = latLonToTile(center.latitude, center.longitude, zoom)
  const tiles: Array<{ key: string; url: string; left: number; top: number }> = []
  const startX = Math.floor(centerTile.x) - 2
  const startY = Math.floor(centerTile.y) - 2
  for (let y = startY; y <= startY + 4; y += 1) {
    for (let x = startX; x <= startX + 4; x += 1) {
      tiles.push({
        key: `${zoom}-${x}-${y}`,
        url: tileUrl(TILE_TEMPLATE, { x, y, z: zoom }),
        left: width / 2 + (x - centerTile.x) * TILE_SIZE,
        top: 150 + (y - centerTile.y) * TILE_SIZE,
      })
    }
  }

  const selectedTile = value ? latLonToTile(value.latitude, value.longitude, zoom) : null
  const markerLeft = selectedTile ? width / 2 + (selectedTile.x - centerTile.x) * TILE_SIZE : width / 2
  const markerTop = selectedTile ? 150 + (selectedTile.y - centerTile.y) * TILE_SIZE : 150

  function selectFromMap(event: MouseEvent<HTMLDivElement>) {
    const rect = event.currentTarget.getBoundingClientRect()
    const point = pointToLatLon(
      event.clientX - rect.left,
      event.clientY - rect.top,
      rect.width || width,
      rect.height || 300,
      centerTile,
    )
    onChange({ ...point, source: 'MANUAL' })
    setGpsError(null)
  }

  function useMapCentre() {
    const point = tileToLatLon(centerTile.x, centerTile.y, zoom)
    onChange({ ...point, source: 'MANUAL' })
    setGpsError(null)
  }

  function pan(deltaX: number, deltaY: number) {
    const next = panCenter(centerTile, deltaX, deltaY)
    setCenter(tileToLatLon(next.x, next.y, zoom))
  }

  function changeZoom(nextZoom: number) {
    setZoom(clampZoom(nextZoom))
  }

  function useGps() {
    setGpsError(null)
    if (!navigator.geolocation) {
      setGpsError('Geolocation is not available in this browser.')
      return
    }
    setLocating(true)
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const next = {
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          source: 'GPS' as const,
        }
        setCenter({ latitude: next.latitude, longitude: next.longitude })
        onChange(next)
        setLocating(false)
      },
      () => {
        setGpsError('Location permission was denied or could not be resolved. You can pin it manually.')
        setLocating(false)
      },
      { enableHighAccuracy: true, timeout: 10_000, maximumAge: 30_000 },
    )
  }

  function updateCoordinate(axis: 'latitude' | 'longitude', raw: string) {
    if (raw.trim() === '') {
      onChange(null)
      return
    }
    const number = Number(raw)
    if (!Number.isFinite(number)) return
    const next = {
      latitude: value?.latitude ?? DEFAULT_CENTER.latitude,
      longitude: value?.longitude ?? DEFAULT_CENTER.longitude,
      source: 'MANUAL' as LocationSource,
      [axis]: number,
    }
    setCenter({ latitude: next.latitude, longitude: next.longitude })
    onChange(next)
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-stone-900">Where did it happen?</p>
          <p className="mt-1 text-xs text-stone-600">
            Use GPS, pin a spot on the map, or enter coordinates manually.
          </p>
        </div>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          onClick={useGps}
          loading={locating}
          icon={<Icon name="location" className="h-4 w-4" />}
        >
          {locating ? 'Locating…' : 'Use my location'}
        </Button>
      </div>

      <div className="mt-4 overflow-hidden rounded-2xl border border-stone-200 bg-stone-100">
        <div
          ref={viewportRef}
          onClick={selectFromMap}
          className="relative h-[300px] cursor-crosshair overflow-hidden bg-paper-deep"
          data-testid="map-viewport"
        >
          {tiles.map((tile) => (
            <img
              key={tile.key}
              src={tile.url}
              alt=""
              draggable={false}
              className="pointer-events-none absolute h-64 w-64 max-w-none select-none opacity-90"
              style={{ left: tile.left, top: tile.top }}
            />
          ))}
          <div className="pointer-events-none absolute inset-0 bg-linear-to-b from-white/10 via-transparent to-stone-900/5" />
          <div
            className="pointer-events-none absolute z-10 -translate-x-1/2 -translate-y-full"
            style={{ left: markerLeft, top: markerTop }}
            data-testid="map-marker"
          >
            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-clay-500 text-white shadow-lg ring-4 ring-white/70">
              <Icon name="location" className="h-5 w-5" />
            </div>
            <div className="mx-auto h-2 w-2 rotate-45 -translate-y-1 bg-clay-500" />
          </div>
          <div className="absolute right-3 bottom-3 z-20 flex flex-col gap-1">
            <button
              type="button"
              aria-label="Zoom in"
              onClick={(event) => {
                event.stopPropagation()
                changeZoom(zoom + 1)
              }}
              className="h-8 w-8 rounded-lg bg-white text-stone-700 shadow-md hover:bg-brand-50"
            >
              +
            </button>
            <button
              type="button"
              aria-label="Zoom out"
              onClick={(event) => {
                event.stopPropagation()
                changeZoom(zoom - 1)
              }}
              className="h-8 w-8 rounded-lg bg-white text-stone-700 shadow-md hover:bg-brand-50"
            >
              −
            </button>
          </div>
          <div className="absolute bottom-3 left-3 z-20 flex gap-1">
            {[
              { label: '←', dx: TILE_SIZE / 2, dy: 0 },
              { label: '→', dx: -TILE_SIZE / 2, dy: 0 },
              { label: '↑', dx: 0, dy: TILE_SIZE / 2 },
              { label: '↓', dx: 0, dy: -TILE_SIZE / 2 },
            ].map((button) => (
              <button
                key={button.label}
                type="button"
                aria-label={`Pan ${button.label}`}
                onClick={(event) => {
                  event.stopPropagation()
                  pan(button.dx, button.dy)
                }}
                className="h-8 w-8 rounded-lg bg-white text-sm font-semibold text-stone-700 shadow-md hover:bg-brand-50"
              >
                {button.label}
              </button>
            ))}
          </div>
          <p className="absolute top-2 right-2 z-20 rounded-md bg-white/90 px-2 py-1 text-[10px] font-semibold text-stone-600">
            Zoom {zoom} · {MIN_ZOOM}–{MAX_ZOOM}
          </p>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-stone-200 bg-white px-3 py-2">
          <p className="text-[10px] text-stone-500">{TILE_ATTRIBUTION}</p>
          <button
            type="button"
            onClick={useMapCentre}
            className="text-[11px] font-semibold text-brand-700 hover:underline"
          >
            Use map centre
          </button>
        </div>
      </div>

      {gpsError && <p className="mt-2 text-xs font-medium text-red-600">{gpsError}</p>}
      {error && <p className="mt-2 text-xs font-medium text-red-600">{error}</p>}

      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <Field label="Latitude" required>
          <input
            className={inputClass}
            type="number"
            step="any"
            value={value?.latitude ?? ''}
            onChange={(event) => updateCoordinate('latitude', event.target.value)}
            placeholder="28.61390"
          />
        </Field>
        <Field label="Longitude" required>
          <input
            className={inputClass}
            type="number"
            step="any"
            value={value?.longitude ?? ''}
            onChange={(event) => updateCoordinate('longitude', event.target.value)}
            placeholder="77.20900"
          />
        </Field>
      </div>

      {value && (
        <div className="mt-3 flex items-center gap-2 rounded-xl bg-brand-50 px-3 py-2 text-xs font-medium text-brand-800">
          <Icon name="check-circle" className="h-4 w-4" />
          {value.source} location selected: {value.latitude.toFixed(5)}, {value.longitude.toFixed(5)}
        </div>
      )}
    </div>
  )
}
