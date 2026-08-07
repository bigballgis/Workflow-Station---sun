import { promisify } from 'node:util'
import { zstdCompress as zstdCompressCallback, zstdDecompress as zstdDecompressCallback } from 'node:zlib'
import { FileCompression, isZstdCompressed } from '@activepieces/shared'

// zstd landed in node:zlib in Node 22.15; the repo pins v24 (.nvmrc). On an older runtime
// these imports are undefined and `promisify(undefined)` throws AT IMPORT TIME, taking the
// whole api test suite down at collection because nearly everything reaches file.service.
const zstdCompress = promisify(zstdCompressCallback)
const zstdDecompress = promisify(zstdDecompressCallback)

export const fileCompressor = {
    async compress({ data, compression }: Params): Promise<Buffer> {
        switch (compression) {
            case FileCompression.NONE:
                return data
            case FileCompression.ZSTD:
                return zstdCompress(data)
        }
    },

    async decompress({ data, compression }: Params): Promise<Buffer> {
        switch (compression) {
            case FileCompression.NONE:
                if (isZstdCompressed(data)) {
                    return zstdDecompress(data)
                }
                return data
            case FileCompression.ZSTD:
                return zstdDecompress(data)
        }
    },
}

type Params = {
    data: Buffer
    compression: FileCompression
}