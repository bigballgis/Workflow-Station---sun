// HERMES FR-D2: the members management feature (invite / role editing UI) is
// removed, but owner attribution still needs the read-only project-members list.
// The /v1/project-members contract is explicitly kept server-side (together with
// managed-authn / signing-key / audit-events), so this minimal client replaces
// the deleted members feature API surface for read paths only.
import { SeekPage } from '@activepieces/core-utils';
import {
  ListProjectMembersRequestQuery,
  ProjectMemberWithUser,
} from '@activepieces/shared';
import { useQuery } from '@tanstack/react-query';

import { api } from '@/lib/api';
import { authenticationSession } from '@/lib/authentication-session';

export const projectMembersMinApi = {
  list(request: ListProjectMembersRequestQuery) {
    return api.get<SeekPage<ProjectMemberWithUser>>(
      '/v1/project-members',
      request,
    );
  },
};

export function useProjectMembers() {
  const projectId = authenticationSession.getProjectId() ?? '';
  const query = useQuery({
    queryKey: ['project-members-min', projectId],
    queryFn: async () => {
      const page = await projectMembersMinApi.list({ projectId });
      return page.data;
    },
    enabled: projectId !== '',
  });
  return { projectMembers: query.data, isLoading: query.isLoading };
}
