import { z } from 'zod'
import { Nullable, OptionalArrayFromQuery } from '../../core/common/base-model'
import { Metadata } from '../../core/common/metadata'
import { SAFE_STRING_PATTERN } from '../../core/common/security'
import { ProjectIcon, ProjectType } from './project'

// The project request contracts carry only fields this build can honour. Dropped with the
// EE modules they fed (D13): `plan.{pieces,piecesFilterType}` (project plans),
// `globalConnectionExternalIds` (platform-scoped connections), `maxConcurrentJobs`
// (concurrency pools), `alertReceiverEmail` (alerts). A schema that still accepted them
// would promise failure emails nobody sends and connection grants nobody applies.
//
// `.strict()` is the point, not decoration: zod's default is to STRIP unknown keys, so a
// client still sending `alertReceiverEmail` would get a 201 and silently no alerts — the
// exact silent-drop this change exists to remove. Strict turns it into a 400 that names the
// offending field.
export const UpdateProjectRequest = z.object({
    releasesEnabled: z.boolean().optional(),
    displayName: z.string().regex(new RegExp(SAFE_STRING_PATTERN)).optional(),
    externalId: z.string().optional(),
    metadata: Metadata.optional(),
    icon: ProjectIcon.optional(),
}).strict()

export type UpdateProjectRequest = z.infer<typeof UpdateProjectRequest>

export const CreateProjectRequest = z.object({
    displayName: z.string().regex(new RegExp(SAFE_STRING_PATTERN)),
    externalId: Nullable(z.string()),
    metadata: Nullable(Metadata),
}).strict()

export type CreateProjectRequest = z.infer<typeof CreateProjectRequest>

export const ListProjectRequestForPlatformQueryParams = z.object({
    externalId: z.string().optional(),
    externalUserId: z.string().optional(),
    limit: z.coerce.number().optional(),
    cursor: z.string().optional(),
    displayName: z.string().optional(),
    types: OptionalArrayFromQuery(z.nativeEnum(ProjectType)),
})

export type ListProjectRequestForPlatformQueryParams = z.infer<typeof ListProjectRequestForPlatformQueryParams>
