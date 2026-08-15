import { FlowOperationType, FlowTriggerType } from '@activepieces/shared';
import { t } from 'i18next';
import { LayoutGridIcon, PuzzleIcon, WrenchIcon } from 'lucide-react';
import React, { useEffect, useRef } from 'react';
import { useDebounce } from 'use-debounce';

import { useBuilderStateContext } from '@/app/builder/builder-hooks';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import { Separator } from '@/components/ui/separator';
import {
  PiecesSearchInput,
  PieceSelectorTabs,
  PieceSelectorTabsProvider,
  PieceSelectorTabType,
  PieceSelectorOperation,
  pieceSelectorUtils,
  pieceSelectorCustomization,
  PieceSearchProvider,
  usePieceSearchContext,
} from '@/features/pieces';
import { platformHooks } from '@/hooks/platform-hooks';
import { useIsMobile } from '@/hooks/use-mobile';

// HERMES-PATCH-001 (docs/ap-integration/HERMES_PATCHES.md): AITabContent and
// ApprovalsTabContent are deliberately NOT imported — see below.
import { ExploreTabContent } from './explore-tab-content';
import { PiecesCardList } from './pieces-card-list';

const getTabsList = (operationType: FlowOperationType) => {
  const baseTabs = [
    {
      value: PieceSelectorTabType.EXPLORE,
      name: t('Explore'),
      icon: <LayoutGridIcon className="size-5" />,
    },
    {
      value: PieceSelectorTabType.APPS,
      name: t('Apps'),
      icon: <PuzzleIcon className="size-5" />,
    },
    {
      value: PieceSelectorTabType.UTILITY,
      name: t('Utility'),
      icon: <WrenchIcon className="size-5" />,
    },
  ];

  // HERMES-PATCH-001 (docs/ap-integration/HERMES_PATCHES.md): the Approvals tab is
  // dropped. approvals-tab-content.tsx hardcodes six SaaS pieces (slack / discord /
  // ms-teams / ms-outlook / gmail / telegram-bot) and renders a skeleton until ALL of
  // them load; under our piece allowlist all six 404 forever, so the tab spins and
  // floods the console. The feature ("request approval via a Slack/Teams/Gmail
  // message") cannot work in an air-gapped cluster at all.
  // The AI & Agents tab is dropped with it on 0.88: it is gated on
  // /v1/ai-providers (server `ai` domain deleted, FR-D2) and surfaces
  // @activepieces/piece-ai, which is absent from the piece allowlist by decision.
  return baseTabs;
};

type PieceSelectorProps = {
  children: React.ReactNode;
  id: string;
  operation: PieceSelectorOperation;
  openSelectorOnClick?: boolean;
  stepToReplacePieceDisplayName?: string;
};

const PieceSelectorWrapper = (props: PieceSelectorProps) => {
  return (
    <PieceSearchProvider>
      <PieceSelectorContent {...props} />
    </PieceSearchProvider>
  );
};

const PieceSelectorContent = ({
  children,
  operation,
  id,
  openSelectorOnClick = true,
  stepToReplacePieceDisplayName,
}: PieceSelectorProps) => {
  const [
    openedPieceSelectorStepNameOrAddButtonId,
    setOpenedPieceSelectorStepNameOrAddButtonId,
    setSelectedPieceMetadataInPieceSelector,
    isForEmptyTrigger,
    deselectStep,
  ] = useBuilderStateContext((state) => [
    state.openedPieceSelectorStepNameOrAddButtonId,
    state.setOpenedPieceSelectorStepNameOrAddButtonId,
    state.setSelectedPieceMetadataInPieceSelector,
    state.flowVersion.trigger.type === FlowTriggerType.EMPTY &&
      id === 'trigger',
    state.deselectStep,
  ]);
  const { searchQuery, setSearchQuery } = usePieceSearchContext();
  const isForReplace =
    operation.type === FlowOperationType.UPDATE_ACTION ||
    (operation.type === FlowOperationType.UPDATE_TRIGGER && !isForEmptyTrigger);
  const [debouncedQuery] = useDebounce(searchQuery, 300);
  const isOpen = openedPieceSelectorStepNameOrAddButtonId === id;
  const isMobile = useIsMobile();
  const { listHeightRef, popoverTriggerRef } =
    pieceSelectorUtils.useAdjustPieceListHeightToAvailableSpace();
  const listHeight =
    Math.min(listHeightRef.current, 300) -
    pieceSelectorUtils.PIECE_SELECTOR_CLIPPING_THRESHOLD;
  const searchInputRef = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (isOpen) {
      setTimeout(() => {
        searchInputRef.current?.focus();
      });
    }
  }, [isOpen]);
  const clearSearch = () => {
    setSearchQuery('');
    setSelectedPieceMetadataInPieceSelector(null);
  };

  const { platform } = platformHooks.useCurrentPlatform();
  const tabsList = pieceSelectorCustomization.buildResolvedTabs({
    availableBuiltinTabs: getTabsList(operation.type),
    config: platform.pieceSelectorConfig,
  });
  const firstTab = tabsList[0];

  return (
    <Popover
      open={isOpen}
      modal={false}
      onOpenChange={(open) => {
        if (open) {
          if (isForEmptyTrigger || openSelectorOnClick) {
            setOpenedPieceSelectorStepNameOrAddButtonId(id);
          }
          return;
        }
        clearSearch();
        setOpenedPieceSelectorStepNameOrAddButtonId(null);
        if (isForEmptyTrigger) {
          deselectStep();
        }
      }}
    >
      <PopoverTrigger
        ref={popoverTriggerRef}
        asChild={true}
        onClick={() => {
          if (openSelectorOnClick) {
            setOpenedPieceSelectorStepNameOrAddButtonId(id);
          }
        }}
      >
        {children}
      </PopoverTrigger>

      <PieceSelectorTabsProvider
        initiallySelectedTab={
          isForReplace || isMobile
            ? PieceSelectorTabType.NONE
            : firstTab?.type ?? PieceSelectorTabType.EXPLORE
        }
        initiallySelectedCustomTabId={
          isForReplace || isMobile ? null : firstTab?.customTabId ?? null
        }
        onTabChange={clearSearch}
        key={isOpen ? 'open' : 'closed'}
      >
        <PopoverContent
          onContextMenu={(e) => {
            e.stopPropagation();
          }}
          onInteractOutside={(e) => {
            if (e.detail.originalEvent.type === 'focusin') {
              e.preventDefault();
            }
          }}
          className="w-[340px] md:w-[600px] p-0 shadow-lg"
          onClick={(e) => {
            e.stopPropagation();
            e.preventDefault();
          }}
        >
          <>
            <div>
              <PiecesSearchInput
                searchInputRef={searchInputRef}
                onSearchChange={(e) => {
                  setSelectedPieceMetadataInPieceSelector(null);
                  if (e === '') {
                    clearSearch();
                  }
                }}
              />
              {!isMobile && <PieceSelectorTabs tabs={tabsList} />}
              <Separator orientation="horizontal" className="mt-1" />
            </div>
            <div
              className=" flex flex-row max-h-[300px]"
              style={{
                height: listHeight + 'px',
              }}
            >
              <ExploreTabContent operation={operation} />
              {/* HERMES-PATCH-001: <AITabContent> / <ApprovalsTabContent> not
                  rendered — they fire their piece queries before the selected-tab
                  early return, so merely hiding the tabs would still 404-spam. */}

              <PiecesCardList
                //this is done to avoid debounced results when user clears search
                searchQuery={searchQuery === '' ? '' : debouncedQuery}
                operation={operation}
                stepToReplacePieceDisplayName={
                  isMobile ? undefined : stepToReplacePieceDisplayName
                }
              />
            </div>
          </>
        </PopoverContent>
      </PieceSelectorTabsProvider>
    </Popover>
  );
};

export { PieceSelectorWrapper as PieceSelector };
