/** 业务日历纯函数：全部按 UTC 的「年月日」计算，规避时区把日期算偏。 */

/** 把任意可解析的日期输入归一成 UTC 零点，只保留年月日。 */
function toUtcDate(input: string | Date): Date {
  const d = new Date(input);
  if (Number.isNaN(d.getTime())) {
    throw new Error(`无法解析日期：${String(input)}`);
  }
  return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()));
}

/** 归一成 YYYY-MM-DD，用于和节假日清单比对、以及输出。 */
function toYmd(d: Date): string {
  return d.toISOString().slice(0, 10);
}

function isWeekend(d: Date): boolean {
  const day = d.getUTCDay(); // 0=周日 6=周六
  return day === 0 || day === 6;
}

/** 是否工作日：非周末且不在节假日清单里。holidays 传 ['2026-10-01', ...]。 */
export function isBusinessDay(input: string | Date, holidays: string[] = []): boolean {
  const d = toUtcDate(input);
  return !isWeekend(d) && !holidays.includes(toYmd(d));
}

/** 从 start 起加 N 个工作日（N 可为负，向前推）。返回 YYYY-MM-DD。 */
export function addBusinessDays(
  start: string | Date,
  days: number,
  holidays: string[] = [],
): string {
  if (!Number.isInteger(days)) {
    throw new Error(`工作日数必须是整数，收到：${days}`);
  }
  const step = days >= 0 ? 1 : -1;
  let remaining = Math.abs(days);
  const cur = toUtcDate(start);
  while (remaining > 0) {
    cur.setUTCDate(cur.getUTCDate() + step);
    if (isBusinessDay(cur, holidays)) {
      remaining--;
    }
  }
  return toYmd(cur);
}

/**
 * start 与 end 之间的工作日数（含 end、不含 start；end 早于 start 返回负数）。
 */
export function businessDaysBetween(
  start: string | Date,
  end: string | Date,
  holidays: string[] = [],
): number {
  const a = toUtcDate(start);
  const b = toUtcDate(end);
  const step = b >= a ? 1 : -1;
  let count = 0;
  const cur = new Date(a);
  while (toYmd(cur) !== toYmd(b)) {
    cur.setUTCDate(cur.getUTCDate() + step);
    if (isBusinessDay(cur, holidays)) {
      count += step;
    }
  }
  return count;
}
