import { Checkbox } from '../Checkbox';

import type { LeadTimeToggleProps } from './LeadTimeToggle.types';

/**
 * One of BR-12's lead times: warn me this many days before a payment is due.
 *
 * The count beside it is what makes the choice concrete — "10 days before due"
 * is abstract, "10 days before due, 3 items" tells you what turning it off
 * would hide.
 */

const describeCount = (itemCount: number): string => {
  if (itemCount === 0) {
    return 'nothing yet';
  }
  return itemCount === 1 ? '1 item' : `${String(itemCount)} items`;
};

export function LeadTimeToggle({ days, enabled, itemCount, onChange }: LeadTimeToggleProps) {
  return (
    <Checkbox
      label={`${String(days)} days before due`}
      meta={describeCount(itemCount)}
      checked={enabled}
      onChange={onChange}
    />
  );
}
