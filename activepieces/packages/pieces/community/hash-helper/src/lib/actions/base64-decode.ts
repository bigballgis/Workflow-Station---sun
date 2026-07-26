import { Property, createAction } from '@activepieces/pieces-framework';
import { base64Decode } from '../common/hashing';

export const base64DecodeAction = createAction({
  name: 'base64_decode',
  displayName: 'Base64 Decode',
  description: '把 Base64 解码为 UTF-8 文本;非法输入抛错中断。',
  props: {
    encoded: Property.LongText({ displayName: 'Base64 Text', required: true }),
  },
  run: async (ctx) => {
    return { text: base64Decode(ctx.propsValue.encoded) };
  },
});
