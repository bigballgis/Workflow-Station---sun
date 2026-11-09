import { describe, expect, it } from 'vitest'

import { importTeamHint, initialImportTeam } from '../useFunctionUnitImport'
import type { ImportPreview, ImportTeamOption } from '@/api/functionUnit'

const teamA: ImportTeamOption = { code: 'TEAM_A', name: 'Team A', state: 'ACTIVE' }
const teamB: ImportTeamOption = { code: 'TEAM_B', name: 'Team B', state: 'ACTIVE' }

function preview(overrides: Partial<ImportPreview>): ImportPreview {
  return {
    code: 'fu-x',
    name: 'FU X',
    version: '1.0.0',
    packageTeam: null,
    currentTeam: null,
    selectableTeams: [teamA, teamB],
    ...overrides,
  }
}

// Platform access model §8.1 / D-23: which team the Admin import dialog starts with, and when it refuses.
describe('initialImportTeam', () => {
  it('prefills the package team when it is active here', () => {
    expect(initialImportTeam(preview({ packageTeam: teamB }))).toEqual({ teamCode: 'TEAM_B', block: null })
  })

  it('refuses a package whose team is missing or disabled here, naming the team', () => {
    const missing = initialImportTeam(preview({
      packageTeam: { code: 'PAYMENT_OPS', name: 'Payment Ops', state: 'MISSING' },
    }))
    expect(missing.teamCode).toBe('')
    expect(missing.block).toEqual({
      key: 'functionUnit.importTeamMissing', params: { name: 'Payment Ops', code: 'PAYMENT_OPS' },
    })
    const disabled = initialImportTeam(preview({ packageTeam: { ...teamA, state: 'INACTIVE' } }))
    expect(disabled.block?.key).toBe('functionUnit.importTeamInactive')
  })

  it('does not prefill a package team the user cannot choose', () => {
    expect(initialImportTeam(preview({ packageTeam: teamB, selectableTeams: [teamA] })))
      .toEqual({ teamCode: '', block: null })
  })

  it('prefills an old package with the team the unit already has, else leaves the choice open', () => {
    expect(initialImportTeam(preview({ currentTeam: teamA })).teamCode).toBe('TEAM_A')
    expect(initialImportTeam(preview({})).teamCode).toBe('')
    expect(initialImportTeam(preview({ currentTeam: { ...teamA, state: 'MISSING' }, selectableTeams: [teamB] })).teamCode)
      .toBe('')
  })
})

describe('importTeamHint', () => {
  it('says where the team came from', () => {
    expect(importTeamHint(preview({ packageTeam: teamA }), 'TEAM_A').key).toBe('functionUnit.teamFromPackage')
    expect(importTeamHint(preview({ currentTeam: teamA }), 'TEAM_A').key).toBe('functionUnit.teamFromCatalog')
    expect(importTeamHint(preview({ currentTeam: teamA }), 'TEAM_B').key).toBe('functionUnit.teamRequired')
    expect(importTeamHint(preview({}), '').key).toBe('functionUnit.teamRequired')
  })

  it('tells a user the package team is not one of theirs', () => {
    expect(importTeamHint(preview({ packageTeam: teamB, selectableTeams: [teamA] }), '')).toEqual({
      key: 'functionUnit.packageTeamNotYours', params: { name: 'Team B', code: 'TEAM_B' },
    })
  })
})
