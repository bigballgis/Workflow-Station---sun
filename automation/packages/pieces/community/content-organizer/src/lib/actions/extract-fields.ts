import { randomUUID } from 'crypto';
import { Property, createAction } from '@activepieces/pieces-framework';
import { FetchLike, complete, uploadFile } from '../common/client';
import { readConfig } from '../common/config';
import { EXTRACTION_QUESTION, FieldSpec, buildFieldList, parseAnswer, validateFields } from '../common/field-prompt';

export const extractFieldsAction = createAction({
  name: 'extract_fields', // stable machine name, stored in flow JSON — renaming is breaking
  displayName: 'Extract Fields',
  description:
    'Uploads a document to Content Organizer and asks it to read the listed fields (OCR). '
    + 'Credentials come from the environment (iB2B service account), not from the flow.',
  props: {
    file: Property.ShortText({
      displayName: 'File URL',
      description: 'The uploaded file, e.g. {{trigger.output.body.variables.document}}. One file only.',
      required: true,
    }),
    staffId: Property.ShortText({
      displayName: 'Staff ID',
      description: 'Whose request this is, for Content Organizer auditing, e.g. {{trigger.output.body.context.currentUser.employeeId}}.',
      required: true,
    }),
    fields: Property.Array({
      displayName: 'Fields',
      description: 'What to read from the document. Each key becomes an output value of the same name.',
      required: true,
      properties: {
        key: Property.ShortText({ displayName: 'Key', required: true }),
        label: Property.ShortText({ displayName: 'Label on the document', required: true }),
        type: Property.StaticDropdown({
          displayName: 'Type',
          required: false,
          defaultValue: 'text',
          options: {
            options: [
              { label: 'Text', value: 'text' },
              { label: 'Date (YYYY-MM-DD)', value: 'date' },
              { label: 'Number', value: 'number' },
            ],
          },
        }),
        hint: Property.ShortText({ displayName: 'Hint', required: false }),
      },
    }),
  },
  run: async (ctx) => {
    const fields = validateFields(ctx.propsValue.fields as FieldSpec[]);
    const staffId = ctx.propsValue.staffId?.trim();
    if (!staffId) {
      throw new Error('Staff ID is empty — the user has no employee id, or the reference is wrong');
    }
    return extractFields({ fileUrl: ctx.propsValue.file, staffId, fields });
  },
});

export async function extractFields(params: {
  fileUrl: string;
  staffId: string;
  fields: FieldSpec[];
  fetchImpl?: FetchLike;
  env?: NodeJS.ProcessEnv;
}): Promise<{ values: Record<string, string | null>; sessionId: string; fileId: string }> {
  const fetchImpl = params.fetchImpl ?? fetch;
  const config = readConfig(params.env);
  const file = await downloadFile(params.fileUrl, fetchImpl);
  // A new session per run: Content Organizer allows only 3 files per session.
  const session = { config, fetchImpl, sessionId: `hermes-${randomUUID()}`, userId: params.staffId };
  const fileId = await uploadFile(session, file);
  const answer = await complete(session, EXTRACTION_QUESTION, buildFieldList(params.fields));
  return { values: parseAnswer(answer, params.fields), sessionId: session.sessionId, fileId };
}

async function downloadFile(fileUrl: string, fetchImpl: FetchLike) {
  const ref = fileUrl?.trim();
  if (!ref) {
    throw new Error('File URL is empty — no file was uploaded');
  }
  if (ref.startsWith('[')) {
    throw new Error('Exactly one file is supported; the upload field holds several');
  }
  let url: URL;
  try {
    url = new URL(ref);
  } catch {
    throw new Error(`File URL must be absolute: ${ref}`);
  }
  const res = await fetchImpl(url, { signal: AbortSignal.timeout(60_000) });
  if (!res.ok) {
    throw new Error(`Downloading the file failed (HTTP ${res.status}): ${url.pathname}`);
  }
  return {
    name: url.searchParams.get('originalName') || decodeURIComponent(url.pathname.split('/').pop() || 'document'),
    contentType: (res.headers.get('content-type') || 'application/octet-stream').split(';')[0].trim(),
    data: new Uint8Array(await res.arrayBuffer()),
  };
}
