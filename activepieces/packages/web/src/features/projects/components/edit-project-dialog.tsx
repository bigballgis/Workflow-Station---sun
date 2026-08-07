import {
  Permission,
  PlatformRole,
  UpdateProjectRequest,
} from '@activepieces/shared';
import { t } from 'i18next';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogFooter,
  DialogHeader,
} from '@/components/ui/dialog';
import {
  Form,
  FormField,
  FormItem,
  FormMessage,
  FormDescription,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { internalErrorToast } from '@/components/ui/sonner';
import { projectCollectionUtils } from '@/features/projects/stores/project-collection';
import { useAuthorization } from '@/hooks/authorization-hooks';
import { platformHooks } from '@/hooks/platform-hooks';
import { userHooks } from '@/hooks/user-hooks';

interface EditProjectDialogProps {
  open: boolean;
  onClose: () => void;
  projectId: string;
  initialValues?: {
    projectName?: string;
    externalId?: string;
  };
}

export function EditProjectDialog({
  open,
  onClose,
  projectId,
  initialValues,
}: EditProjectDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="max-w-md w-full">
        <DialogHeader>
          {' '}
          <DialogTitle>
            {t('Edit')} {initialValues?.projectName}
          </DialogTitle>
        </DialogHeader>

        <EditProjectForm
          onClose={onClose}
          projectId={projectId}
          initialValues={initialValues}
        />
      </DialogContent>
    </Dialog>
  );
}

const EditProjectForm = ({
  onClose,
  projectId,
  initialValues,
}: {
  onClose: () => void;
  projectId: string;
  initialValues?: EditProjectDialogProps['initialValues'];
}) => {
  const { checkAccess } = useAuthorization();
  const { platform } = platformHooks.useCurrentPlatform();
  const platformRole = userHooks.getCurrentUserPlatformRole();
  const { mutate, isPending } = projectCollectionUtils.useUpdateProject(
    () => {
      toast.success(t('Your changes have been saved.'), {
        duration: 3000,
      });
      onClose();
    },
    (error) => {
      console.error(error);
      internalErrorToast();
    },
  );

  const form = useForm<UpdateProjectRequest>({
    defaultValues: {
      displayName: initialValues?.projectName,
      externalId: initialValues?.externalId,
    },
    disabled: checkAccess(Permission.WRITE_PROJECT) === false,
  });

  return (
    <Form {...form}>
      <form
        className="space-y-4"
        onSubmit={form.handleSubmit((values) => {
          mutate({
            projectId,
            request: {
              displayName: values.displayName,
              externalId: values.externalId,
            },
          });
        })}
      >
        <FormField
          name="displayName"
          render={({ field }) => (
            <FormItem>
              <Label htmlFor="displayName">{t('Project Name')}</Label>
              <Input
                {...field}
                id="displayName"
                placeholder={t('Project Name')}
                className="rounded-sm"
              />
              <FormMessage />
            </FormItem>
          )}
        />

        {platform.plan.embeddingEnabled &&
          platformRole === PlatformRole.ADMIN && (
            <FormField
              name="externalId"
              render={({ field }) => (
                <FormItem>
                  <Label htmlFor="externalId">{t('External ID')}</Label>
                  <FormDescription>
                    {t('Used to identify the project based on your SaaS ID')}
                  </FormDescription>
                  <Input
                    {...field}
                    id="externalId"
                    placeholder={t('org-3412321')}
                    className="rounded-sm"
                  />
                  <FormMessage />
                </FormItem>
              )}
            />
          )}

        <DialogFooter className="justify-end mt-6">
          <Button type="button" variant="outline" onClick={onClose}>
            {t('Cancel')}
          </Button>
          <Button type="submit" disabled={isPending} loading={isPending}>
            {t('Save')}
          </Button>
        </DialogFooter>
      </form>
    </Form>
  );
};
