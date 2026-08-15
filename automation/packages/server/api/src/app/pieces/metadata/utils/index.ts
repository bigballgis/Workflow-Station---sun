import { ActionBase } from '@activepieces/pieces-framework'
import { PieceAudienceFilter, PieceCategory, PieceOrderBy, PieceSortBy, SuggestionType } from '@activepieces/shared'
import { FastifyBaseLogger } from 'fastify'
import { PieceMetadataSchema } from '../piece-metadata-entity'
import { pieceSearching } from './piece-searching'
import { pieceSorting } from './piece-sorting'

export const pieceListUtils = (_log: FastifyBaseLogger) => ({
    // HERMES-PATCH-004 (piece-admin P2) — NOT PORTABLE TO 0.88 AS-IS: the 0.84 fork
    // restored a platform-level visibility filter here keyed on
    // platform.filteredPieceNames/filteredPieceBehavior so the Admin Center
    // enable/disable toggle worked. Upstream 0.88 dropped those Platform columns
    // entirely and moved filtering into the (now deleted) EE resolveVisibility
    // policy; the 0.88 Platform model only has pinnedPieces/pieceSelectorConfig.
    // Re-enabling the admin toggle needs a schema decision on the platform entity
    // (owned outside this area) — until then the list is unfiltered, like upstream CE.
    async sortAndSearchPieces(params: SortAndSearchPiecesParams): Promise<PieceMetadataSchema[]> {
        const sortedPieces = pieceSorting.sortAndOrder(
            params.sortBy,
            params.orderBy,
            params.pieces,
        )

        return pieceSearching.search({
            categories: params.categories,
            searchQuery: params.searchQuery,
            pieces: sortedPieces,
            suggestionType: params.suggestionType,
        })
    },
})

export function filterActionsByAudience(
    actions: Record<string, ActionBase>,
    audience: PieceAudienceFilter | undefined,
): Record<string, ActionBase> {
    return Object.fromEntries(
        Object.entries(actions).filter(([, action]) => {
            switch (audience) {
                case PieceAudienceFilter.ALL:
                    return true
                case PieceAudienceFilter.AI:
                    return action.audience !== 'human'
                case PieceAudienceFilter.HUMAN:
                case undefined:
                default:                                
                    return action.audience !== 'ai'
            }
            
        }),
    )
}

export type SortAndSearchPiecesParams = {
    searchQuery?: string
    categories?: PieceCategory[]
    sortBy?: PieceSortBy
    orderBy?: PieceOrderBy
    pieces: PieceMetadataSchema[]
    suggestionType?: SuggestionType
}

export * from './piece-cache-utils'
