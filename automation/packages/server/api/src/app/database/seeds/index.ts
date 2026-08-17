import { devDataSeed } from './dev-seeds'
import { rolesSeed } from './role-seed'

// HERMES: knowledgeBaseSeed removed with the knowledge-base domain (FR-D2).
export const databaseSeeds = {
    async run() {
        const seeds = [
            rolesSeed,
            devDataSeed,
        ]
        for (const seed of seeds) {
            await seed.run()
        }
    },
}
