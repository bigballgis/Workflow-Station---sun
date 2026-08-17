import { Property, createAction } from '@activepieces/pieces-framework';
import { HashAlgorithm, HashEncoding, hashText } from '../common/hashing';

export const hashTextAction = createAction({
  name: 'hash_text',                 // 稳定机器名,进 flow JSON,改名=breaking
  displayName: 'Hash Text',
  description: '计算文本摘要(SHA-256/SHA-512/SHA-1/MD5),纯本地。',
  props: {
    text: Property.LongText({
      displayName: 'Text',
      description: '要计算摘要的文本。',
      required: true,
    }),
    algorithm: Property.StaticDropdown({
      displayName: 'Algorithm',
      required: true,
      defaultValue: 'sha256',
      options: {
        options: [
          { label: 'SHA-256', value: 'sha256' },
          { label: 'SHA-512', value: 'sha512' },
          { label: 'SHA-1', value: 'sha1' },
          { label: 'MD5', value: 'md5' },
        ],
      },
    }),
    encoding: Property.StaticDropdown({
      displayName: 'Output Encoding',
      required: false,
      defaultValue: 'hex',
      options: {
        options: [
          { label: 'Hex', value: 'hex' },
          { label: 'Base64', value: 'base64' },
        ],
      },
    }),
  },
  run: async (ctx) => {
    const { text, algorithm, encoding } = ctx.propsValue;
    const digest = hashText(
      text,
      algorithm as HashAlgorithm,
      (encoding as HashEncoding) ?? 'hex',
    );
    return { digest, algorithm, encoding: encoding ?? 'hex' };
  },
});
