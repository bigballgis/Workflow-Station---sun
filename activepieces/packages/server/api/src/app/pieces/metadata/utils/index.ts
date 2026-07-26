import { FilteredPieceBehavior, isNil, PieceCategory, PieceOrderBy, PieceSortBy, PlatformId, SuggestionType } from '@activepieces/shared'
import { FastifyBaseLogger } from 'fastify'
import { platformService } from '../../../platform/platform.service'
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
        // available piece set via build-time offline pre-install (FR-F01).
        // HERMES-PATCH(piece-admin P2): restore the minimal platform-level visibility
        // filter (upstream EE filterPiecesBasedPlatform semantics, verbatim) so the
        // Admin Center enable/disable toggle works via platform.filteredPieceNames.
        // Deliberately list-only: get() stays unfiltered so flows referencing a
        // disabled piece keep loading and running — disable hides from the designer
        // catalog, it does not break existing flows. Project-level filtering is not
        // restored (CE has no project_plan entity).
        return filterPiecesBasedOnPlatform(log, params, userBasedPieces)
    },
})

async function filterPiecesBasedOnPlatform(
    log: FastifyBaseLogger,
    params: FilterPiecesParams,
    pieces: PieceMetadataSchema[],
): Promise<PieceMetadataSchema[]> {
    if (params.includeHidden || isNil(params.platformId)) {
        return pieces
    }
    const platform = await platformService(log).getOne(params.platformId)
    if (isNil(platform)) {
        return pieces
    }
    const filterPredicate: Record<FilteredPieceBehavior, (p: PieceMetadataSchema) => boolean> = {
        [FilteredPieceBehavior.ALLOWED]: (p) => platform.filteredPieceNames.includes(p.name),
        [FilteredPieceBehavior.BLOCKED]: (p) => !platform.filteredPieceNames.includes(p.name),
    }
    return pieces.slice().filter(filterPredicate[platform.filteredPieceBehavior])
}

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