import { CreateProjectRequest, ProjectWithLimits } from '@activepieces/shared';
import { zodResolver } from '@hookform/resolvers/zod';
import { t } from 'i18next';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

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
import { internalErrorToast } from '@/components/ui/sonner';
import { projectCollectionUtils } from '@/features/projects';

type NewProjectDialogProps = {
  children: React.ReactNode;
  onCreate?: (project: ProjectWithLimits) => void;
};

export const NewProjectDialog = (props: NewProjectDialogProps) => {
  const [open, setOpen] = useState(false);

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
        <NewProjectForm setOpen={setOpen} onCreate={props.onCreate} />
      </DialogContent>
    </Dialog>
  );
};

// Alert Receiver Email and Global Connections used to live here. Both fed EE modules this
// build does not have (alerts, platform-scoped connections), so the fields could only ever
// be collected and dropped — see DECISIONS.md D13.
const NewProjectForm = ({
  onCreate,
  setOpen,
}: Omit<NewProjectDialogProps, 'children'> & {
  setOpen: (open: boolean) => void;
}) => {
  const form = useForm<CreateProjectRequest>({
    resolver: zodResolver(
      z.object({
        displayName: z.string().min(1, t('Name is required')),
      }),
    ),
  });

  const { mutate, isPending } = projectCollectionUtils.useCreateProject(
    (data) => {
      onCreate?.(data);
      setOpen(false);
    },
    (error) => {
      console.error(error);
      internalErrorToast();
    },
  );

  const handleCreate = () => {
    mutate(form.getValues());
  };

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
                <FormMessage />
              </FormItem>
            )}
          />
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
