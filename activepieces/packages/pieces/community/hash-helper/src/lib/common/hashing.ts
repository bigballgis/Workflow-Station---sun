import { createHash } from 'crypto';

/** 支持的摘要算法(与 node:crypto 名称一致)。 */
export type HashAlgorithm = 'sha256' | 'sha512' | 'sha1' | 'md5';

/** 输出编码。 */
export type HashEncoding = 'hex' | 'base64';

/** 计算文本摘要,纯本地、零外网。 */
export function hashText(
  text: string,
  algorithm: HashAlgorithm,
  encoding: HashEncoding = 'hex',
): string {
  return createHash(algorithm).update(text, 'utf8').digest(encoding);
}

/** UTF-8 文本 → Base64。 */
export function base64Encode(text: string): string {
  return Buffer.from(text, 'utf8').toString('base64');
}

/** Base64 → UTF-8 文本。非法输入抛错(Buffer 会静默容错,这里显式校验)。 */
export function base64Decode(encoded: string): string {
  const normalized = encoded.replace(/\s+/g, '');
  if (!/^[A-Za-z0-9+/]*={0,2}$/.test(normalized) || normalized.length % 4 !== 0) {
    throw new Error(`不是合法的 Base64 输入:${encoded.slice(0, 40)}`);
  }
  return Buffer.from(normalized, 'base64').toString('utf8');
}
