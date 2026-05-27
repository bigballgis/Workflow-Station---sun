/**
 * 统一 Logger
 *
 * 集中管理所有 console 输出，预留 analytics / 远程日志接口。
 * DEV 环境输出详细日志，PROD 仅输出 error。
 */

const isDev = typeof import.meta !== 'undefined' ? import.meta.env?.DEV : false

type LogLevel = 'debug' | 'info' | 'warn' | 'error'

interface LogEntry {
  level: LogLevel
  module: string
  message: string
  data?: unknown
}

// 预留：远程日志发送
const sendRemote = (_entry: LogEntry) => {
  // no-op until analytics endpoint is configured
}

const log = (level: LogLevel, module: string, message: string, data?: unknown) => {
  const entry: LogEntry = { level, module, message, data }

  // DEV: 控制台输出
  if (isDev) {
    const fn = level === 'error' ? console.error : level === 'warn' ? console.warn : console.debug
    fn(`[${module}] ${message}`, data ?? '')
  } else if (level === 'error') {
    // PROD: 仅 error 输出
    console.error(`[${module}] ${message}`, data ?? '')
  }

  sendRemote(entry)
}

export const logger = {
  debug: (module: string, message: string, data?: unknown) => log('debug', module, message, data),
  info: (module: string, message: string, data?: unknown) => log('info', module, message, data),
  warn: (module: string, message: string, data?: unknown) => log('warn', module, message, data),
  error: (module: string, message: string, data?: unknown) => log('error', module, message, data),
}
