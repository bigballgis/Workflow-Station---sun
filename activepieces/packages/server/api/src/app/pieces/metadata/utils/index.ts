import { PieceCategory, PieceOrderBy, PieceSortBy, PlatformId, SuggestionType } from '@activepieces/shared'
import { FastifyBaseLogger } from 'fastify'
import { PieceMetadataSchema } from '../piece-metadata-entity'
import { pieceSearching } from './piece-searching'
import { pieceSorting } from './piece-sorting'

export const pieceListUtils = (log: FastifyBaseLogger) => ({
    async filterPieces(params: FilterPiecesParams): Promise<PieceMetadataSchema[]> {
        const sortedPieces = pieceSorting.sortAndOrder(
            params.sortBy,
            params.orderBy,
            params.pieces,
        )

        const userBasedPieces = pieceSearching.search({
            categories: params.categories,
            searchQuery: params.searchQuery,
            pieces: sortedPieces,
            suggestionType: params.suggestionType,
        })

        // HERMES: EE piece white/blacklist filtering removed (AG-EE / G12). CE governs the
        // available piece set via build-time offline pre-install (FR-F01); no runtime filter.
        return userBasedPieces
    },
})

export type FilterPiecesParams = {
    includeHidden?: boolean
    platformId?: PlatformId
    searchQuery?: string
    categories?: PieceCategory[]
    projectId?: string
    sortBy?: PieceSortBy
    orderBy?: PieceOrderBy
    pieces: PieceMetadataSchema[]
    suggestionType?: SuggestionType
}

export * from './piece-cache-utils'