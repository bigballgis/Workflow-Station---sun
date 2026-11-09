export default {
  ldapSyncGuide: {
    pageTitle: 'LDAP Sync Records',
    crumb: 'Admin Center · Audit Log',
    intro:
      'LDAP Sync Records lists every LDAP / AD sync run in this environment: user profiles and virtual-group lists, with status, counts and the message the run left. The page is read-only. System Administrators, Technical Leads and Auditors see the same records.',
    flowTitle: 'Order of work',
    flow1: 'Open Admin Center → Audit Log → LDAP Sync Records.',
    flow2: 'Filter Status to Failed or Partly succeeded from the column header, or search for an AD group name.',
    flow3: 'Read Message. For the people a group run added or removed, open Admin Center Audit Logs and look for Operator LDAP_SYNC_JOB.',
    listTitle: 'What the list shows',
    listBody:
      'One row per run, newest first. A run is written when it starts (Running) and updated when it ends (Succeeded, Partly succeeded or Failed).',
    listLead: 'Field catalog — LDAP Sync Records list.',
    listFigure: 'LDAP Sync Records as a Technical Lead: the Audit Log menu, a search for No virtual group, and the Message column. There is no button to start a sync.',
    fType:
      'Users (full) and Users (incremental) read user profiles from the directory; they run only when a System Administrator starts them. Incremental reads only entries changed since the last Users (full) or Users (incremental) run that ended Succeeded; Partly succeeded runs and group runs do not count. With no such run yet it runs, and is recorded, as Users (full). Groups (full) and Groups (incremental) bring each virtual-group list in line with the AD group it is mapped to; both do the same work. The runs at start-up and on the schedule are recorded as Groups (incremental).',
    fStatus: 'Running, Succeeded, Partly succeeded or Failed (see What each status means). Filter it from the column header.',
    fStarted: 'When the run started, in your browser’s time zone.',
    fDuration:
      'How long the run took, for example 12.3s or 2m 05s. A dash: the run has not finished, or its type does not record a duration.',
    fFetched: 'Directory entries the run read.',
    fAddedUpdated:
      'User accounts the run created and updated from the directory. A dash: the run has not finished, or it is an older record without these numbers.',
    fFailedItems:
      'Directory entries with an employee ID that could not be stored, for example because of a database error; the Admin Center log names them. The run goes on without them; how they affect the status is under Partly succeeded in What each status means.',
    fSkipped:
      'Directory entries without an employee ID. Admin Center does not store such accounts, by design: they are not failures and do not change the status. Message gives the number after Skipped without an employee ID (not stored). In a group run an account in two mapped AD groups counts twice, as in Fetched. A dash: an older record without this number.',
    fMessage:
      'A run that stopped on an error: the error text as recorded, cut at 1,000 characters. Group runs: notes such as Not read, lists unchanged, Read incompletely, nobody removed, or No members found (missing or empty AD group), each followed by the AD groups concerned and separated by semicolons. Any run that read the directory: Skipped without an employee ID (not stored) followed by a number; Users runs: Could not be stored followed by a number. Empty when there is nothing to report. A long message is cut off in the cell; hover over it to read all of it, one note per line.',
    statusTitle: 'What each status means',
    statusLead: 'Field catalog — Status values.',
    sRunning:
      'The run has started and not ended yet.',
    sSucceeded:
      'Users runs: the directory was read to the end and every entry with an employee ID was stored. Group runs: every mapped AD group was read in full and all its members with an employee ID were stored. Entries without an employee ID are skipped (Skipped items) and do not change the status. An AD group that is empty or missing (No members found), System Administrators keeping its AD members (Kept the AD members of …), or no list mapped to AD at all still counts as Succeeded.',
    sPartial:
      'Users runs: the directory was read to the end; some entries with an employee ID could not be stored (Failed items; Message: Could not be stored), the others were. The next Users (incremental) run starts from the last Succeeded Users run, not from this one. Group runs: some mapped AD groups could not be read (Not read, lists unchanged), or were read only in part or not all of their members could be stored (Read incompletely, nobody removed). The lists they feed lost nobody; every other AD group was synced as usual. Message names the AD groups.',
    sFailed:
      'Group runs: none of the mapped AD groups could be read, for example because the directory was unreachable; no list changed and Message lists them after Not read, lists unchanged. Users runs: the directory was read to the end but none of the entries with an employee ID could be stored (Added and Updated 0; Message: Could not be stored); the next Users (incremental) run starts from the last Succeeded Users run. Any run: it stopped on an error, and Message holds the error text.',
    scheduleTitle: 'When syncs run',
    scheduleBody:
      'LDAP sync runs only while Admin Center has both ldap.enabled and ldap.sync-enabled switched on. Then a group sync runs once after Admin Center starts and again on the ldap.sync-cron schedule (by default every 2 hours, on the hour). A System Administrator can also start any of the four types through the API; Admin Center has no button for it.',
    scheduleLock:
      'Only one LDAP sync runs at a time, across every Admin Center instance. While a start-up, scheduled or manual sync is running, starting another through the API is refused with HTTP 409 and Another LDAP sync is running. Start this one again when it has finished. A start-up or scheduled run that finds another sync running is skipped, not queued, and Admin Center logs a warning. A run keeps the lock for as long as it takes; if Admin Center stops during a run, the lock is released within 20 minutes. When Redis, which holds the lock, cannot be reached, starting a sync through the API is refused with HTTP 503 and The LDAP sync did not start: its lock service (Redis) cannot be reached. Try again later, or ask the platform operator to check Redis.',
    findTitle: 'Find a run',
    findLead: 'Field catalog — toolbar and column headers.',
    fSearch:
      'Matches Message and the AD groups a group run read (the groups are not a column). It also matches the stored Type and Status codes, such as FAILED or HERMES_AD_GROUP, not their labels. Press Enter or click Search; clearing the box reloads the list.',
    fColumnMenu: 'Sort, filter, move or resize a column. Type and Status filter by their labels.',
    fExport: 'Downloads the checked rows as CSV, or the current page when none is checked.',
    fRefresh: 'Reloads the list with the current search and filters.',
    fTotal: 'How many runs match the search and filters.',
    accessTitle: 'Who can open it',
    accessBody:
      'Everyone who can open the page sees the same records, not filtered by team. The page has no button to start, stop or delete a run.',
    accessMessage:
      'Message is shown as recorded for every role. A failure can name an LDAP host or a DN; it does not contain passwords.',
    fRoleAdmin: 'Sees every record. Only a System Administrator can start a sync, through the API only; there is no button for it.',
    fRoleLead: 'Sees every record, read-only.',
    fRoleAuditor: 'Sees every record, read-only.',
    membersTitle: 'Who was added or removed',
    membersBody:
      'A group run keeps only counts here. Each person it adds to or removes from a virtual-group list is a separate entry in Admin Center Audit Logs, with Operator LDAP_SYNC_JOB. Members added by hand are never removed by a sync.',
    failTitle: 'When something looks wrong',
    failEmpty:
      'No records: LDAP sync is switched off in this environment (ldap.enabled and ldap.sync-enabled must both be on), or has not run yet. Records written before it was switched off stay on the list.',
    failRunning:
      'Running long after Started: the run did not finish, for example because Admin Center restarted during it. The next run writes its own record.',
    failFailed:
      'Failed: read Message. Not read, lists unchanged followed by every mapped AD group means the directory could not be read at all; Could not be stored with Added and Updated 0 means a Users run read the directory but could not store any entry, and the Admin Center log says why; anything else is the error the run stopped on. The group sync runs again at the next scheduled time; once the cause is fixed, a System Administrator can also start a run.',
    failPartialStatus:
      'Partly succeeded: for a group run, Message names the AD groups that were not read or read only in part; nobody was removed from the lists they feed, and the next run tries them again. For a Users run, some entries could not be stored (Could not be stored); the Admin Center log names them, and the next Users (incremental) run reads again from the last Succeeded Users run.',
    failNotRead:
      'Not read, lists unchanged: those AD groups could not be read, so the lists they feed kept their AD members. The run is Partly succeeded, or Failed when no AD group could be read.',
    failPartial:
      'Read incompletely, nobody removed: only part of those AD groups was read, or some of their members could not be stored. New members were still added, but nobody was removed from the lists they feed. The run is Partly succeeded.',
    failNoMembers:
      'No members found (missing or empty AD group): the mapped AD group does not exist or has no members, and the lists it feeds lose their AD members. This note does not change the status. On Virtual Group, check AD Group (or Developer AD Group / Technical Lead AD Group on a team).',
    failKept:
      'Kept the AD members of …: removing them would leave no active member — only for the System Administrators group: the run kept its AD members so that group is not left without an active member. This note does not change the status. Other lists have no such guard; their AD members are removed as AD says.',
    failBusy:
      'Starting a sync through the API returns 409 Another LDAP sync is running…: a start-up, scheduled or manual sync is running. Start it again when that run has ended. A start-up or scheduled run keeps refusing manual starts for one minute after it started, even if it ends sooner.',
    failLockUnavailable:
      'Starting a sync through the API returns 503 The LDAP sync did not start: its lock service (Redis) cannot be reached…: Admin Center cannot reach Redis, which holds the sync lock, so nothing was started. Try again later; if it keeps happening, ask the platform operator to check Redis.',
    failNoMapping: 'No virtual group maps an AD group: no list is synced from AD, so the run read nothing. The run is Succeeded.',
    failLoad: 'Failed to load LDAP sync records: click Refresh.',
    failMenu:
      'LDAP Sync Records is not under Audit Log: your account is not a System Administrator, an Auditor, or a Technical Lead of an active team.',
  },
}
