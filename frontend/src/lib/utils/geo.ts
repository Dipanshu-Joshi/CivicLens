export interface TileCoordinate {
  x: number
  y: number
  z: number
}

export interface LatLon {
  latitude: number
  longitude: number
}

export const TILE_SIZE = 256
export const MIN_ZOOM = 3
export const MAX_ZOOM = 18

export function clampZoom(zoom: number): number {
  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom))
}

export function latLonToTile(latitude: number, longitude: number, zoom: number): TileCoordinate {
  const scale = 2 ** zoom
  const x = ((longitude + 180) / 360) * scale
  const latitudeRad = (latitude * Math.PI) / 180
  const y = ((1 - Math.asinh(Math.tan(latitudeRad)) / Math.PI) / 2) * scale
  return { x, y, z: zoom }
}

export function tileToLatLon(x: number, y: number, zoom: number): LatLon {
  const scale = 2 ** zoom
  const longitude = (x / scale) * 360 - 180
  const latitude = (Math.atan(Math.sinh(Math.PI * (1 - (2 * y) / scale))) * 180) / Math.PI
  return { latitude, longitude }
}

export function tileUrl(template: string, tile: TileCoordinate): string {
  return template
    .replace('{z}', String(tile.z))
    .replace('{x}', String(tile.x))
    .replace('{y}', String(tile.y))
}

/** Converts a click inside the map viewport into geographic coordinates. */
export function pointToLatLon(
  offsetX: number,
  offsetY: number,
  width: number,
  height: number,
  center: TileCoordinate,
): LatLon {
  const tileX = center.x + (offsetX - width / 2) / TILE_SIZE
  const tileY = center.y + (offsetY - height / 2) / TILE_SIZE
  const result = tileToLatLon(tileX, tileY, center.z)
  return {
    latitude: Math.min(85.0511, Math.max(-85.0511, result.latitude)),
    longitude: Math.min(180, Math.max(-180, result.longitude)),
  }
}

export function panCenter(center: TileCoordinate, deltaX: number, deltaY: number): TileCoordinate {
  return {
    z: center.z,
    x: center.x - deltaX / TILE_SIZE,
    y: center.y - deltaY / TILE_SIZE,
  }
}
