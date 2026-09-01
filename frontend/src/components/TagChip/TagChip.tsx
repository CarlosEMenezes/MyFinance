import type { TagChipProps } from './TagChip.types';

/**
 * A small label: an account kind, a job status, a goal's pace, or the currency
 * tag that marks an amount converted from another currency (BR-8).
 *
 * Styling comes entirely from the design system's `.tag` classes, so a chip
 * cannot drift from the system by being restyled locally.
 */
export function TagChip({ children, variant = 'neutral', className }: TagChipProps) {
  return (
    <span className={['tag', `tag-${variant}`, className].filter(Boolean).join(' ')}>
      {children}
    </span>
  );
}
