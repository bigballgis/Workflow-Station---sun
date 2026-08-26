declare module 'utif' {
  interface Ifd {
    width?: number
    height?: number
    t256?: number[]
    t257?: number[]
  }
  export function decode(buffer: ArrayBuffer | Uint8Array): Ifd[]
  export function decodeImage(buffer: ArrayBuffer | Uint8Array, ifd: Ifd, ifds?: Ifd[]): void
  export function toRGBA8(ifd: Ifd): Uint8Array
}

declare module 'pptx-preview' {
  export function init(
    el: HTMLElement,
    opts?: { width?: number; height?: number; mode?: 'list' | 'slide' },
  ): { preview: (data: ArrayBuffer) => Promise<void> | void }
}

declare module 'pdfjs-dist/build/pdf.worker.min.mjs?url' {
  const url: string
  export default url
}
