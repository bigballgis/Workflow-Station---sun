// HERMES FR-D2: tables and templates domains are removed — the empty state is
// flows-only (start from scratch / import). The upstream version also offered
// "Create a Table" and a suggested-templates section.
import { Permission } from '@activepieces/core-utils';
import { UncategorizedFolderId } from '@activepieces/shared';
import { t } from 'i18next';
import { ChevronRight, Plus, Upload, Workflow } from 'lucide-react';

import { PermissionNeededTooltip } from '@/components/custom/permission-needed-tooltip';
import { Card, CardContent } from '@/components/ui/card';
import { ImportFlowDialog } from '@/features/flows/components/import-flow-dialog';
import { flowHooks } from '@/features/flows/hooks/flow-hooks';
import { useAuthorization } from '@/hooks/authorization-hooks';
import { flagsHooks } from '@/hooks/flags-hooks';
import { platformHooks } from '@/hooks/platform-hooks';

type ActionRowProps = {
  icon: React.ReactNode;
  label: string;
  onClick?: () => void;
  disabled?: boolean;
  hasPermission?: boolean;
};

const ActionRow = ({
  icon,
  label,
  onClick,
  disabled,
  hasPermission = true,
}: ActionRowProps) => {
  const content = (
    <button
      onClick={onClick}
      disabled={disabled || !hasPermission}
      className="flex items-center justify-between w-full px-4 py-3 text-left hover:bg-muted/50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed border-t first:border-t-0"
    >
      <div className="flex items-center gap-3">
        <span className="text-muted-foreground">{icon}</span>
        <span className="text-sm font-medium">{label}</span>
      </div>
      <ChevronRight className="h-4 w-4 text-muted-foreground" />
    </button>
  );

  if (!hasPermission) {
    return (
      <PermissionNeededTooltip hasPermission={hasPermission}>
        {content}
      </PermissionNeededTooltip>
    );
  }

  return content;
};

type GetStartedCardProps = {
  icon: React.ReactNode;
  iconBgClass: string;
  title: string;
  description: string;
  children: React.ReactNode;
};

const GetStartedCard = ({
  icon,
  iconBgClass,
  title,
  description,
  children,
}: GetStartedCardProps) => {
  return (
    <Card className="flex-1 overflow-hidden">
      <CardContent className="p-0">
        <div className="flex items-center gap-3 px-4 py-4">
          <div
            className={`w-10 h-10 rounded-lg flex items-center justify-center ${iconBgClass}`}
          >
            {icon}
          </div>
          <div>
            <h3 className="font-semibold text-base">{title}</h3>
            <p className="text-sm text-muted-foreground">{description}</p>
          </div>
        </div>
        <div className="flex flex-col">{children}</div>
      </CardContent>
    </Card>
  );
};

type AutomationsEmptyStateProps = {
  onRefresh: () => void;
};

export const AutomationsEmptyState = ({
  onRefresh,
}: AutomationsEmptyStateProps) => {
  const { checkAccess } = useAuthorization();
  const userHasPermissionToWriteFlow = checkAccess(Permission.WRITE_FLOW);

  const { platform } = platformHooks.useCurrentPlatform();

  const { mutate: createFlow, isPending: isCreateFlowPending } =
    flowHooks.useStartFromScratch(UncategorizedFolderId);

  const branding = flagsHooks.useWebsiteBranding();

  return (
    <div className="flex flex-col gap-8 py-8 px-4 max-w-5xl mx-auto">
      <div>
        <h2 className="text-sm font-medium text-muted-foreground mb-4">
          {t('Get started with {brandName}', {
            brandName: branding.websiteName ?? platform.name,
          })}
        </h2>
        <div className="flex gap-4">
          <GetStartedCard
            icon={<Workflow className="h-5 w-5 text-primary" />}
            iconBgClass="bg-primary-100"
            title={t('Build a Flow')}
            description={t('Create automated workflows')}
          >
            <ActionRow
              icon={<Plus className="h-4 w-4" />}
              label={t('Start from scratch')}
              onClick={() => createFlow()}
              disabled={isCreateFlowPending}
              hasPermission={userHasPermissionToWriteFlow}
            />
            <PermissionNeededTooltip
              hasPermission={userHasPermissionToWriteFlow}
            >
              <ImportFlowDialog
                insideBuilder={false}
                onRefresh={onRefresh}
                folderId={UncategorizedFolderId}
              >
                <button
                  disabled={!userHasPermissionToWriteFlow}
                  className="flex items-center justify-between w-full px-4 py-3 text-left hover:bg-muted/50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed border-t"
                >
                  <div className="flex items-center gap-3">
                    <span className="text-muted-foreground">
                      <Upload className="h-4 w-4" />
                    </span>
                    <span className="text-sm font-medium">{t('Import')}</span>
                  </div>
                  <ChevronRight className="h-4 w-4 text-muted-foreground" />
                </button>
              </ImportFlowDialog>
            </PermissionNeededTooltip>
          </GetStartedCard>
        </div>
      </div>
    </div>
  );
};
