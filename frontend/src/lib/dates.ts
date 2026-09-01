/**
 * Calendar dates, held as an ISO `YYYY-MM-DD` string.
 *
 * Spec §0.5: dates in the domain are calendar dates, never timestamps. A
 * `Date` is an instant, and an instant carries a timezone — which is how a
 * transaction logged at 00:30 ends up on the wrong day, and how a month-long
 * span across a daylight-saving change comes out as 30.958 days.
 *
 * Every operation here is integer arithmetic on the proleptic Gregorian
 * calendar. No `Date` is involved except at the one boundary where the
 * system clock is read ({@link today}). The ISO form also sorts correctly as
 * plain text and matches how Java's `LocalDate` serialises, so it crosses the
 * API unchanged.
 */

declare const calendarDateBrand: unique symbol;

/** A date on the calendar, with no time and no timezone. */
export type CalendarDate = string & { readonly [calendarDateBrand]: true };

/** The formats the settings page offers (spec §2, `dateFormat`). */
export type DateFormat = 'DD-MM-YYYY' | 'MM-DD-YYYY' | 'YYYY-MM-DD';

const DEFAULT_DATE_FORMAT: DateFormat = 'DD-MM-YYYY';

const ISO_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;
/** Shared by both year-last formats — they differ in how the first two groups
 *  are read, not in what they match. */
const YEAR_LAST_PATTERN = /^(\d{2})-(\d{2})-(\d{4})$/;

const MONTHS_PER_YEAR = 12;
const DAYS_PER_COMMON_MONTH = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

/** Days from 1970-01-01 to 0000-03-01, the epoch shift used below. */
const EPOCH_SHIFT_DAYS = 719468;
const DAYS_PER_ERA = 146097;
const YEARS_PER_ERA = 400;

function isLeapYear(year: number): boolean {
  return (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
}

export function daysInMonth(year: number, month: number): number {
  if (month === 2 && isLeapYear(year)) {
    return 29;
  }
  return DAYS_PER_COMMON_MONTH[month - 1] ?? 0;
}

function isRealDate(year: number, month: number, day: number): boolean {
  if (!Number.isInteger(year) || !Number.isInteger(month) || !Number.isInteger(day)) {
    return false;
  }
  if (month < 1 || month > MONTHS_PER_YEAR) {
    return false;
  }
  return day >= 1 && day <= daysInMonth(year, month);
}

function pad(value: number, length: number): string {
  return String(value).padStart(length, '0');
}

function assemble(year: number, month: number, day: number): CalendarDate {
  return `${pad(year, 4)}-${pad(month, 2)}-${pad(day, 2)}` as CalendarDate;
}

export function fromParts(year: number, month: number, day: number): CalendarDate {
  if (!isRealDate(year, month, day)) {
    throw new RangeError(`The date ${pad(year, 4)}-${pad(month, 2)}-${pad(day, 2)} does not exist`);
  }
  return assemble(year, month, day);
}

export function fromIso(text: string): CalendarDate {
  const match = ISO_PATTERN.exec(text);
  if (match === null) {
    throw new TypeError(`"${text}" is not a valid ISO date, expected YYYY-MM-DD`);
  }
  return fromParts(Number(match[1]), Number(match[2]), Number(match[3]));
}

export function toIso(date: CalendarDate): string {
  return date;
}

export function yearOf(date: CalendarDate): number {
  return Number(date.slice(0, 4));
}

/** The calendar month, 1–12 — not the 0-based month a `Date` would give. */
export function monthOf(date: CalendarDate): number {
  return Number(date.slice(5, 7));
}

export function dayOf(date: CalendarDate): number {
  return Number(date.slice(8, 10));
}

export function parse(text: string, dateFormat: DateFormat = DEFAULT_DATE_FORMAT): CalendarDate {
  if (dateFormat === 'YYYY-MM-DD') {
    return fromIso(text);
  }

  const match = YEAR_LAST_PATTERN.exec(text);
  if (match === null) {
    throw new TypeError(`"${text}" does not match the format ${dateFormat}`);
  }

  const first = Number(match[1]);
  const second = Number(match[2]);
  const year = Number(match[3]);

  return dateFormat === 'DD-MM-YYYY'
    ? fromParts(year, second, first)
    : fromParts(year, first, second);
}

export function format(date: CalendarDate, dateFormat: DateFormat = DEFAULT_DATE_FORMAT): string {
  const year = pad(yearOf(date), 4);
  const month = pad(monthOf(date), 2);
  const day = pad(dayOf(date), 2);

  switch (dateFormat) {
    case 'DD-MM-YYYY':
      return `${day}-${month}-${year}`;
    case 'MM-DD-YYYY':
      return `${month}-${day}-${year}`;
    case 'YYYY-MM-DD':
      return `${year}-${month}-${day}`;
  }
}

/**
 * Days since 1970-01-01, by Howard Hinnant's `days_from_civil`.
 *
 * Shifting the year to start in March puts the leap day at the end, so the
 * month-length pattern repeats and needs no table or branching.
 */
function toEpochDay(date: CalendarDate): number {
  const month = monthOf(date);
  const shiftedYear = yearOf(date) - (month <= 2 ? 1 : 0);
  const era = Math.floor(shiftedYear / YEARS_PER_ERA);
  const yearOfEra = shiftedYear - era * YEARS_PER_ERA;
  const dayOfYear = Math.floor((153 * (month + (month > 2 ? -3 : 9)) + 2) / 5) + dayOf(date) - 1;
  const dayOfEra =
    yearOfEra * 365 + Math.floor(yearOfEra / 4) - Math.floor(yearOfEra / 100) + dayOfYear;

  return era * DAYS_PER_ERA + dayOfEra - EPOCH_SHIFT_DAYS;
}

/** The inverse of {@link toEpochDay}. */
function fromEpochDay(epochDay: number): CalendarDate {
  const shifted = epochDay + EPOCH_SHIFT_DAYS;
  const era = Math.floor(shifted / DAYS_PER_ERA);
  const dayOfEra = shifted - era * DAYS_PER_ERA;
  const yearOfEra = Math.floor(
    (dayOfEra -
      Math.floor(dayOfEra / 1460) +
      Math.floor(dayOfEra / 36524) -
      Math.floor(dayOfEra / 146096)) /
      365,
  );
  const shiftedYear = yearOfEra + era * YEARS_PER_ERA;
  const dayOfYear =
    dayOfEra - (365 * yearOfEra + Math.floor(yearOfEra / 4) - Math.floor(yearOfEra / 100));
  const shiftedMonth = Math.floor((5 * dayOfYear + 2) / 153);
  const day = dayOfYear - Math.floor((153 * shiftedMonth + 2) / 5) + 1;
  const month = shiftedMonth + (shiftedMonth < 10 ? 3 : -9);

  return assemble(shiftedYear + (month <= 2 ? 1 : 0), month, day);
}

export function addDays(date: CalendarDate, days: number): CalendarDate {
  return fromEpochDay(toEpochDay(date) + days);
}

/**
 * Adds whole months, clamping the day to the length of the target month.
 *
 * BR-10: a plan anchored on the 31st lands on the 30th in a 30-day month, and
 * on the 28th or 29th in February. The anchor itself is never rewritten, so
 * the following month returns to the 31st.
 */
export function addMonths(date: CalendarDate, months: number): CalendarDate {
  const monthIndex = yearOf(date) * MONTHS_PER_YEAR + (monthOf(date) - 1) + months;
  const targetYear = Math.floor(monthIndex / MONTHS_PER_YEAR);
  const targetMonth = (monthIndex % MONTHS_PER_YEAR) + 1;

  return assemble(
    targetYear,
    targetMonth,
    Math.min(dayOf(date), daysInMonth(targetYear, targetMonth)),
  );
}

/** Whole days from `from` to `to`; negative when `to` is the earlier date. */
export function daysBetween(from: CalendarDate, to: CalendarDate): number {
  return toEpochDay(to) - toEpochDay(from);
}

/** Negative, zero or positive — the contract `Array.prototype.sort` expects. */
export function compare(a: CalendarDate, b: CalendarDate): number {
  if (a < b) {
    return -1;
  }
  return a > b ? 1 : 0;
}

export function isBefore(date: CalendarDate, other: CalendarDate): boolean {
  return date < other;
}

export function isAfter(date: CalendarDate, other: CalendarDate): boolean {
  return date > other;
}

export function isSameOrBefore(date: CalendarDate, other: CalendarDate): boolean {
  return date <= other;
}

export function isSameOrAfter(date: CalendarDate, other: CalendarDate): boolean {
  return date >= other;
}

/**
 * The calendar date of an instant, read from its **local** parts.
 *
 * The instant is a parameter so that everything downstream is testable and
 * nothing reads the wall clock implicitly. `toISOString()` is deliberately not
 * used: it converts to UTC first, which shifts the date either side of
 * midnight for most of the world.
 */
export function today(now: Date = new Date()): CalendarDate {
  return fromParts(now.getFullYear(), now.getMonth() + 1, now.getDate());
}
