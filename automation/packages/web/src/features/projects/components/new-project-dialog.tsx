import {
  AppConnectionWithoutSensitiveData,
  CreatePlatformProjectRequest,
  ProjectWithLimits,
} from '@activepieces/shared';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { t } from 'i18next';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { DefaultTag } from '@/components/custom/global-connection-utils';
import { MultiSelectPieceProperty } from '@/components/custom/multi-select-piece-property';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Form, FormField, FormItem, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { SkeletonList } from '@/components/ui/skeleton';
import { internalErrorToast } from '@/components/ui/sonner';
import { globalConnectionsQueries } from '@/features/connections';
import { projectCollectionUtils } from '@/features/projects';
import { platformHooks } from '@/hooks/platform-hooks';

type NewProjectDialogProps = {
  children: React.ReactNode;
  onCreate?: (project: ProjectWithLimits) => void;
};

export const NewProjectDialog = (props: NewProjectDialogProps) => {
  const [open, setOpen] = useState(false);
  const { platform } = platformHooks.useCurrentPlatform();
  const globalConnectionsEnabled = platform.plan.globalConnectionsEnabled;

  const { data: globalConnectionsPage, isLoading: isLoadingConnections } =
    globalConnectionsQueries.useGlobalConnections({
      request: { limit: 9999 },
      extraKeys: [],
    });

  const globalConnections = globalConnectionsPage?.data ?? [];

  return (
    <Dialog key={open ? 'open' : 'closed'} open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{props.children}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('Create Project')}</DialogTitle>
          <DialogDescription>
            {t(
              'Set up a new project to organize your automations and connections.',
            )}
          </DialogDescription>
        </DialogHeader>
        {(!isLoadingConnections || !globalConnectionsEnabled) && (
          <NewProjectForm
            setOpen={setOpen}
            globalConnections={globalConnections}
            globalConnectionsEnabled={globalConnectionsEnabled}
            onCreate={props.onCreate}
          />
        )}
        {isLoadingConnections && globalConnectionsEnabled && (
          <SkeletonList numberOfItems={3} className="h-10" />
        )}
      </DialogContent>
    </Dialog>
  );
};

const NewProjectForm = ({
  onCreate,
  setOpen,
  globalConnections,
  globalConnectionsEnabled,
}: Omit<NewProjectDialogProps, 'children'> & {
  setOpen: (open: boolean) => void;
  globalConnections: AppConnectionWithoutSensitiveData[];
  globalConnectionsEnabled: boolean;
}) => {
  const queryClient = useQueryClient();
  const preselectedConnectionExternalIds = globalConnections
    .filter((connection) => connection.preSelectForNewProjects)
    .map((connection) => connection.externalId);

  const form = useForm<CreatePlatformProjectRequest>({
    resolver: zodResolver(
      z.object({
        displayName: z.string().min(1, t('Name is required')),
      }),
    ),
    defaultValues: {
      globalConnectionExternalIds: preselectedConnectionExternalIds,
    },
  });

  // HERMES: POST /v1/projects validates with a `.strict()` body
  // (server/api/src/app/project/project.controller.ts CreateProjectRequestBody) that
  // accepts only displayName/externalId/metadata — deliberately, so an unsupported
  // field fails loudly instead of being silently stripped. Spreading the whole form
  // state here sent `globalConnectionExternalIds` (and formerly `alertReceiverEmail`)
  // on every submit, so project creation always came back 400. Send only what the
  // endpoint accepts.
  const handleCreate = () => {
    const values = form.getValues();
    mutate({
      displayName: values.displayName,
    });
  };

  const { mutate, isPending } = projectCollectionUtils.useCreateProject(
    (data) => {
      onCreate?.(data);
      setOpen(false);
      queryClient.invalidateQueries({
        queryKey: globalConnectionsQueries.getGlobalConnectionsQueryKey([]),
      });
    },
    (error) => {
      console.error(error);
      internalErrorToast();
    },
  );

  return (
    <>
      <Form {...form}>
        <form
          className="grid space-y-4"
          onSubmit={(e) => form.handleSubmit(handleCreate)(e)}
        >
          <FormField
            name="displayName"
            render={({ field }) => (
              <FormItem className="grid space-y-2">
                <Label htmlFor="displayName" showRequiredIndicator>
                  {t('Project Name')}
                </Label>
                <Input
                  {...field}
                  id="displayName"
                  placeholder={t('Project Name')}
                  className="rounded-sm"
                />
              </FormItem>
            )}
          />
          {/* HERMES: the "Alert Receiver Email" field is removed with the alerts +
              SMTP domains (SMTP_CONFIGURED is hard-false in flag.service.ts and every
              AP_SMTP_* system prop is now unreferenced), so this build cannot send a
              flow-failure email to anything the user types here. The server also
              rejects the field outright — see handleCreate above. */}
          {globalConnectionsEnabled && (
            <FormField
              name="globalConnectionExternalIds"
              render={({ field }) => (
                <FormItem className="grid space-y-2">
                  <Label>{t('Global Connections')}</Label>
                  <MultiSelectPieceProperty
                    placeholder={t('Select global connections')}
                    options={
                      globalConnections.map((connection) => ({
                        value: connection.externalId,
                        label: connection.displayName,
                      })) ?? []
                    }
                    loading={false}
                    onChange={(value) => {
                      field.onChange(value ?? []);
                    }}
                    itemExtraContent={(index) => {
                      if (globalConnections[index].preSelectForNewProjects) {
                        return <DefaultTag />;
                      }
                      return null;
                    }}
                    initialValues={field.value ?? []}
                    showDeselect={(field.value ?? []).length > 0}
                  />
                </FormItem>
              )}
            />
          )}
          {form?.formState?.errors?.root?.serverError && (
            <FormMessage>
              {form.formState.errors.root.serverError.message}
            </FormMessage>
          )}
          <DialogFooter>
            <Button
              variant={'outline'}
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                e.preventDefault();
                setOpen(false);
              }}
            >
              {t('Cancel')}
            </Button>
            <Button
              disabled={isPending}
              loading={isPending}
              onClick={(e) => {
                e.stopPropagation();
                e.preventDefault();
                form.handleSubmit(handleCreate)(e);
              }}
            >
              {t('Create Project')}
            </Button>
          </DialogFooter>
        </form>
      </Form>
    </>
  );
};
