import { t } from 'i18next';
import { SearchXIcon } from 'lucide-react';

// HERMES-PATCH-023: the SHOW_COMMUNITY-gated "Request Piece" button opened
// shared's feedbackUrl = feedback.activepieces.com (upstream feature board).
// SHOW_COMMUNITY is true for CE, so it rendered a link that can never resolve
// air-gapped, and requesting pieces from upstream is meaningless for a fork
// whose piece set is an explicit 13-entry allowlist. Button + flag + embed
// guard removed with it.
const NoResultsFound = () => {
  return (
    <div className="flex flex-col items-center justify-center gap-3 h-full px-6 text-center">
      <div className="flex items-center justify-center size-12 rounded-full bg-muted">
        <SearchXIcon className="size-6 text-muted-foreground" />
      </div>
      <div className="flex flex-col gap-1">
        <div className="text-sm font-medium text-foreground">
          {t('No results found')}
        </div>
        <div className="text-xs text-muted-foreground">
          {t('Try a different search term')}
        </div>
      </div>
    </div>
  );
};

export { NoResultsFound };
