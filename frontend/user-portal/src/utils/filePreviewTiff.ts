/** TIFF IFD sizing — pick full-size pages and drop thumbnail IFDs. */

export interface TiffIfdSize {
  width?: number
  height?: number
  t256?: number[]
  t257?: number[]
}

export interface TiffDisplayPage {
  ifdIndex: number
  width: number
  height: number
}

/** Tag 256/257 (ImageWidth / ImageLength) or UTIF's decoded width/height. */
export function tiffIfdPixelSize(ifd: TiffIfdSize): { width: number; height: number } {
  const width = Number(ifd.width || ifd.t256?.[0] || 0)
  const height = Number(ifd.height || ifd.t257?.[0] || 0)
  return {
    width: Number.isFinite(width) ? width : 0,
    height: Number.isFinite(height) ? height : 0,
  }
}

/**
 * Keep IFDs that are at least 25% of the largest frame. Scanner TIFFs often
 * store a tiny thumbnail IFD first; using that as page 0 looks like a blurry file.
 */
export function selectTiffDisplayPages(ifds: TiffIfdSize[]): TiffDisplayPage[] {
  const sized: TiffDisplayPage[] = []
  for (let i = 0; i < ifds.length; i++) {
    const { width, height } = tiffIfdPixelSize(ifds[i])
    if (width > 0 && height > 0) sized.push({ ifdIndex: i, width, height })
  }
  if (sized.length === 0) return []
  const maxArea = Math.max(...sized.map((p) => p.width * p.height))
  const minKeep = maxArea * 0.25
  return sized.filter((p) => p.width * p.height >= minKeep)
}
