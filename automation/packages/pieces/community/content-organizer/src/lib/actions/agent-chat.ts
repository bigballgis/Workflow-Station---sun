import { randomUUID } from 'crypto';
import { Property, createAction } from '@activepieces/pieces-framework';
import { FetchLike, completeWithAgent } from '../common/client';
import { readConfig } from '../common/config';

export const agentChatAction = createAction({
  name: 'agent_chat', // stable machine name, stored in flow JSON — renaming is breaking
  displayName: 'Agent Chat',
  description:
    'Sends one message to the Content Organizer agent setting of this environment (multi-agent, no document) '
    + 'and returns the answering agent\'s reply. Credentials and the agent setting come from the environment, not from the flow.',
  props: {
    message: Property.LongText({
      displayName: 'Message',
      description: 'The whole request, e.g. {{trigger.output.body.message}}. An agent setting has no variable, '
        + 'so any context travels inside the message.',
      required: true,
    }),
    staffId: Property.ShortText({
      displayName: 'Staff ID',
      description: 'Whose request this is, for Content Organizer auditing, e.g. {{trigger.output.body.staffId}}.',
      required: true,
    }),
  },
  run: async (ctx) => agentChat({
    message: ctx.propsValue.message,
    staffId: ctx.propsValue.staffId,
  }),
});

export async function agentChat(params: {
  message: string;
  staffId: string;
  fetchImpl?: FetchLike;
  env?: NodeJS.ProcessEnv;
}): Promise<{ reply: string; sessionId: string }> {
  const message = params.message?.trim();
  const staffId = params.staffId?.trim();
  if (!message) {
    throw new Error('Message is empty');
  }
  if (!staffId) {
    throw new Error('Staff ID is empty — the user has no employee id, or the reference is wrong');
  }
  const config = readConfig(params.env, 'agent');
  // A new session per call: the caller sends the conversation in the message, nothing is kept in CO.
  const session = { config, fetchImpl: params.fetchImpl ?? fetch, sessionId: `hermes-${randomUUID()}`, userId: staffId };
  const reply = await completeWithAgent(session, message);
  return { reply: reply.trim(), sessionId: session.sessionId };
}
