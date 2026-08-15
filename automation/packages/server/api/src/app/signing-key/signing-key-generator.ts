import {
    generateKeyPair as generateKeyPairCallback,
    RSAKeyPairOptions,
} from 'node:crypto'
import { promisify } from 'node:util'
import { KeyAlgorithm } from '@activepieces/shared'

const generateKeyPair = promisify(generateKeyPairCallback)

export const signingKeyGenerator = {
    async generate(): Promise<GeneratedKeyPair> {
        const algorithm = 'rsa'

        const options: RSAKeyPairOptions<'pem', 'pem'> = {
            modulusLength: 4096,
            publicKeyEncoding: {
                type: 'pkcs1',
                format: 'pem',
            },
            // HERMES divergence from upstream (was pkcs1): the private key is
            // returned once to HERMES (admin-center, Java) to sign per-user
            // external tokens. Java's KeyFactory parses PKCS8 natively
            // (PKCS8EncodedKeySpec) but not PKCS1, and adding BouncyCastle just
            // to unwrap PKCS1 is avoided (air-gap, X-3). AP never re-parses the
            // private key (only publicKey is stored/verified), so emitting PKCS8
            // here is safe and keeps the same keypair — the PKCS1 public key
            // still verifies signatures made with this PKCS8 private key.
            privateKeyEncoding: {
                type: 'pkcs8',
                format: 'pem',
            },
        }

        const keyPair = await generateKeyPair(algorithm, options)

        return {
            ...keyPair,
            algorithm: KeyAlgorithm.RSA,
        }
    },
}

type GeneratedKeyPair = {
    privateKey: string
    publicKey: string
    algorithm: KeyAlgorithm
}
