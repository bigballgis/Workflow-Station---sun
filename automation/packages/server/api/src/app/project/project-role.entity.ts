import { PlatformId, ProjectMember, ProjectRole } from '@activepieces/shared'
import { EntitySchema } from 'typeorm'
import { BaseColumnSchemaPart } from '../database/database-common'

// HERMES: CE reimplementation of the project_role TypeORM schema, moved out of app/ee
// (AG-EE / EE_REMOVAL_PLAN G15). The underlying table is created by the MIT migration
// (1731424289830), so this is a schema mapping with no EE logic. See G2 for the RBAC
// services that consume it.
export type ProjectRoleSchema = ProjectRole & {
    name: string
    permissions: string[]
    platformId: PlatformId
    projectMembers: ProjectMember[]
}

export const ProjectRoleEntity = new EntitySchema<ProjectRoleSchema>({
    name: 'project_role',
    columns: {
        ...BaseColumnSchemaPart,
        name: {
            type: String,
            nullable: false,
        },
        permissions: {
            type: String,
            array: true,
            nullable: false,
        },
        platformId: {
            type: String,
            nullable: true,
        },
        type: {
            type: String,
            nullable: false,
        },
    },
})
