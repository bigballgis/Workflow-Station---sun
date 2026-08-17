import { Project, ProjectMember, ProjectRole, User } from '@activepieces/shared'
import { EntitySchema } from 'typeorm'
import {
    ApIdSchema,
    BaseColumnSchemaPart,
} from '../database/database-common'

// HERMES: CE reimplementation of the project_member TypeORM schema, moved out of app/ee
// (AG-EE / EE_REMOVAL_PLAN G15). The table is created by the MIT migration
// (1764867709704-UnifyCommunityWithEnterprise), so this is a schema mapping with no EE
// logic. FK to project_role is why concurrency_pool/project_role/project_member are the
// three entities kept (see the FK check in EE_REMOVAL_PLAN §4.4/R12).
export type ProjectMemberSchema = ProjectMember & {
    user: User
    project: Project
    projectRole: ProjectRole
}

export const ProjectMemberEntity = new EntitySchema<ProjectMemberSchema>({
    name: 'project_member',
    columns: {
        ...BaseColumnSchemaPart,
        projectId: ApIdSchema,
        platformId: ApIdSchema,
        userId: ApIdSchema,
        projectRoleId: ApIdSchema,
    },
    indices: [
        {
            name: 'idx_project_member_project_id_user_id_platform_id',
            columns: ['projectId', 'userId', 'platformId'],
            unique: true,
        },
    ],
    relations: {
        project: {
            type: 'many-to-one',
            target: 'project',
            cascade: true,
            onDelete: 'CASCADE',
            joinColumn: {
                name: 'projectId',
                foreignKeyConstraintName: 'fk_project_member_project_id',
            },
        },
        user: {
            type: 'many-to-one',
            target: 'user',
            cascade: true,
            onDelete: 'CASCADE',
            joinColumn: {
                name: 'userId',
                foreignKeyConstraintName: 'fk_project_member_user_id',
            },
        },
        projectRole: {
            type: 'many-to-one',
            target: 'project_role',
            cascade: true,
            onDelete: 'CASCADE',
            joinColumn: {
                name: 'projectRoleId',
                referencedColumnName: 'id',
                foreignKeyConstraintName: 'fk_project_member_project_role_id',
            },
        },
    },
})
