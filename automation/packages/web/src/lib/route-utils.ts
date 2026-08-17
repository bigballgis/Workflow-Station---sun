import { Permission } from '@activepieces/core-utils';

import { authenticationSession } from './authentication-session';

export const routesThatRequireProjectId = {
  runs: '/runs',
  singleRun: '/runs/:runId',
  flows: '/flows',
  singleFlow: '/flows/:flowId',
  automations: '/automations',
  connections: '/connections',
  singleConnection: '/connections/:connectionId',
  variables: '/variables',
  settings: '/settings',
};

// HERMES FR-D2: tables / releases / chat routes removed with their domains.
export const determineDefaultRoute = ({
  checkAccess,
}: {
  checkAccess: (permission: Permission) => boolean;
}) => {
  if (checkAccess(Permission.READ_FLOW)) {
    return authenticationSession.appendProjectRoutePrefix('/automations');
  }
  if (checkAccess(Permission.READ_RUN)) {
    return authenticationSession.appendProjectRoutePrefix('/runs');
  }
  return authenticationSession.appendProjectRoutePrefix('/settings');
};

export const NEW_FLOW_QUERY_PARAM = 'newFlow';
