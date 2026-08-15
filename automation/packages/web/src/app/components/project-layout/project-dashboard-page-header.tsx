// HERMES FR-D2: the members feature (invite dialog, member count button) is
// removed — identity and membership are owned by HERMES. The settings dialog is
// reduced to its General tab.
import { ProjectType } from '@activepieces/shared';
import { t } from 'i18next';
import { Lock } from 'lucide-react';
import { useState } from 'react';
import { useLocation } from 'react-router-dom';

import { AnimatedIconButton } from '@/components/custom/animated-icon-button';
import { PageHeader } from '@/components/custom/page-header';
import { SettingsIcon } from '@/components/icons/settings';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { getProjectName, projectCollectionUtils } from '@/features/projects';
import { ApProjectDisplay } from '@/features/projects/components/ap-project-display';

import { ProjectSettingsDialog } from '../project-settings';

export const ProjectDashboardPageHeader = ({
  children,
  description,
}: {
  children?: React.ReactNode;
  description?: React.ReactNode;
}) => {
  const { project } = projectCollectionUtils.useCurrentProject();
  const [settingsOpen, setSettingsOpen] = useState(false);
  const location = useLocation();
  const isProjectPage = location.pathname.includes('/projects/');

  const titleContent = (
    <div className="flex items-center gap-1">
      <ApProjectDisplay
        title={getProjectName(project)}
        maxLengthToNotShowTooltip={30}
        titleClassName="text-sm font-medium"
        projectType={project.type}
      />
      {project.type === ProjectType.PERSONAL && (
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>
              <Lock className="w-4 h-4" />
            </TooltipTrigger>
            <TooltipContent>
              <p>
                {t(
                  'This is your private project. Only you can see and access it.',
                )}
              </p>
            </TooltipContent>
          </Tooltip>
        </TooltipProvider>
      )}
    </div>
  );

  const rightContent = isProjectPage ? (
    <div className="flex items-center gap-3">
      <AnimatedIconButton
        icon={SettingsIcon}
        iconSize={16}
        variant="ghost"
        size="icon"
        className="h-8 w-8"
        onClick={() => setSettingsOpen(true)}
      />
    </div>
  ) : (
    children
  );

  return (
    <>
      <PageHeader
        title={titleContent}
        description={description}
        rightContent={rightContent}
        showSidebarToggle={true}
        className="min-w-full"
      />
      <ProjectSettingsDialog
        open={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        initialTab="general"
        initialValues={{
          projectName: project?.displayName,
        }}
      />
    </>
  );
};
