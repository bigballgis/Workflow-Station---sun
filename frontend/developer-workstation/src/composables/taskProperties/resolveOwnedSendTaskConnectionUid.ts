/**
 * Send Task BPMN stores connectionId as EmailConnection.connectionUid.
 * After clone the uid is remapped in BPMN; already-cloned units may still hold the source uid.
 */
export interface SendTaskConnectionOption {
  connectionUid: string
}

export function resolveOwnedSendTaskConnectionUid(
  storedUid: string,
  connections: SendTaskConnectionOption[]
): string {
  const stored = storedUid?.trim() ?? ''
  if (!stored) {
    return ''
  }
  if (connections.length === 0) {
    return stored
  }
  if (connections.some(c => c.connectionUid === stored)) {
    return stored
  }
  if (connections.length === 1) {
    return connections[0].connectionUid
  }
  return ''
}
