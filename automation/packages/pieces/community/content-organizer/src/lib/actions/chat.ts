import { randomUUID } from 'crypto';
import { Property, createAction } from '@activepieces/pieces-framework';
import { FetchLike, complete } from '../common/client';
import { readConfig } from '../common/config';

export const chatAction = createAction({
  name: 'chat', // stable machine name, stored in flow JSON — renaming is breaking
  displayName: 'Chat',
  description:
    'Asks Content Organizer one question with the chat prompt setting of this environment (no document). '
    + 'Credentials and the prompt setting come from the environment, not from the flow.',
  props: {
    message: Property.LongText({
      displayName: 'Message',
      description: 'The latest user message, e.g. {{trigger.output.body.message}}.',
      required: true,
    }),
    context: Property.LongText({
      displayName: 'Context',
      description: "Fills the prompt setting's variable — the application context and conversation so far, "
        + 'e.g. {{trigger.output.body.userInput}}.',
      required: true,
    }),
    staffId: Property.ShortText({
      displayName: 'Staff ID',
      description: 'Whose request this is, for Content Organizer auditing, e.g. {{trigger.output.body.staffId}}.',
      required: true,
    }),
  },
  run: async (ctx) => chat({
    message: ctx.propsValue.message,
    context: ctx.propsValue.context,
    staffId: ctx.propsValue.staffId,
  }),
});

export async function chat(params: {
  message: string;
  context: string;
  staffId: string;
  fetchImpl?: FetchLike;
  env?: NodeJS.ProcessEnv;
}): Promise<{ reply: string; sessionId: string }> {
  const message = params.message?.trim();
  const context = params.context?.trim();
  const staffId = params.staffId?.trim();
  if (!message) {
    throw new Error('Message is empty');
  }
  if (!context) {
    throw new Error('Context is empty — the prompt setting requires its variable');
  }
  if (!staffId) {
    throw new Error('Staff ID is empty — the user has no employee id, or the reference is wrong');
  }
  const config = readConfig(params.env, 'chat');
  // A new session per call: the caller sends the conversation in the context, nothing is kept in CO.
  const session = { config, fetchImpl: params.fetchImpl ?? fetch, sessionId: `hermes-${randomUUID()}`, userId: staffId };
  const reply = await complete(session, message, context);
  return { reply: reply.trim(), sessionId: session.sessionId };
}
