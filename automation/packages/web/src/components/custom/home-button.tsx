import { t } from 'i18next';
import { ChevronLeft } from 'lucide-react';
import { Link } from 'react-router-dom';

import { useEmbedding } from '@/components/providers/embed-provider';
import { Button } from '@/components/ui/button';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { flagsHooks } from '@/hooks/flags-hooks';
import { authenticationSession } from '@/lib/authentication-session';

// HERMES: inlined from the deleted `ee-embed-sdk` package (FR-D2). That package was
// EE-licensed and existed only for the iframe embed handshake, which X-6 rejected — the
// DW host mounts the builder in a Shadow DOM instead. This one postMessage `type` string
// was its last consumer anywhere in the tree; the literal must stay byte-identical for any
// host still listening on the legacy channel.
const CLIENT_BUILDER_HOME_BUTTON_CLICKED = 'CLIENT_BUILDER_HOME_BUTTON_CLICKED';

const HomeButtonWrapper = ({ children }: { children: React.ReactNode }) => {
  const { embedState } = useEmbedding();
  if (embedState.emitHomeButtonClickedEvent) {
    const handleClick = () => {
      window.parent.postMessage(
        {
          type: CLIENT_BUILDER_HOME_BUTTON_CLICKED,
          data: {
            route: '/flows',
          },
        },
        '*',
      );
    };
    return <div onClick={handleClick}>{children}</div>;
  }
  return (
    <Link to={authenticationSession.appendProjectRoutePrefix('/flows')}>
      {children}
    </Link>
  );
};
const HomeButton = () => {
  const { embedState } = useEmbedding();
  const branding = flagsHooks.useWebsiteBranding();
  const showBackButton = embedState.homeButtonIcon === 'back';
  return (
    <>
      {!embedState.hideHomeButtonInBuilder && (
        <Tooltip>
          <HomeButtonWrapper>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size={'icon'}
                className={showBackButton ? 'size-8' : 'size-10'}
              >
                {!showBackButton && (
                  <img
                    className="h-5 w-5 object-contain"
                    src={branding.logos.logoIconUrl}
                    alt={branding.websiteName}
                  />
                )}
                {showBackButton && <ChevronLeft className="h-4 w-4" />}
              </Button>
            </TooltipTrigger>
          </HomeButtonWrapper>
          {!showBackButton && (
            <TooltipContent side="bottom">
              {t('Go to Dashboard')}
            </TooltipContent>
          )}
        </Tooltip>
      )}
    </>
  );
};

HomeButton.displayName = 'HomeButton';

export { HomeButton };
