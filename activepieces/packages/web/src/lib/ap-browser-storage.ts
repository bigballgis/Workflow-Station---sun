import { apHost } from './host-config';

export class ApStorage {
  private static instance: Storage;
  private constructor(value: Storage) {
    ApStorage.instance = value;
  }
  static getInstance() {
    if (!ApStorage.instance) {
      // HERMES L1 (#1): a host-injected Storage takes precedence, giving DW a
      // single physical override point for token/projectId (api/socket/hooks all
      // read through ApStorage). Falls back to localStorage for standalone AP.
      ApStorage.instance = apHost.getConfig().storage ?? window.localStorage;
    }
    return ApStorage.instance;
  }
  static setInstanceToSessionStorage() {
    ApStorage.instance = window.sessionStorage;
  }
}
