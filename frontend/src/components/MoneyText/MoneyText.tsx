import { format, formatSigned } from '../../lib/money';

import type { MoneyTextProps } from './MoneyText.types';

/**
 * A money figure on screen.
 *
 * The single edge where an amount stops being an exact integer of minor units
 * and becomes text. Everything upstream of this stays in `Money`, which is
 * what keeps spec §0.5 enforceable rather than merely intended.
 *
 * Tabular numerals are not optional (spec §5): without them a column of
 * figures fails to line up, and money in this app is always read in columns.
 */
export function MoneyText({ amount, currency = 'EUR', signed = false, className }: MoneyTextProps) {
  const text = signed ? formatSigned(amount, currency) : format(amount, currency);

  return <span className={['tabular', className].filter(Boolean).join(' ')}>{text}</span>;
}
