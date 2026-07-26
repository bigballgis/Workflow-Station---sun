import { Property, createAction } from '@activepieces/pieces-framework';
import { base64Encode } from '../common/hashing';

export const base64EncodeAction = createAction({
  name: 'base64_encode',
  displayName: 'Base64 Encode',
  description: '把 UTF-8 文本编码为 Base64。',
  props: {
    text: Property.LongText({ displayName: 'Text', required: true }),
  },
  run: async (ctx) => {
    return { encoded: base64Encode(ctx.propsValue.text) };
  },
});
