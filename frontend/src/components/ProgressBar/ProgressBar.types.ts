/**
 * `accent` is the ordinary fill. `negative` is the over-plan red, used when a
 * credit card is running hot against its limit — the same colour BR-9 gives an
 * overspend, so the meaning is consistent wherever it appears.
 */
export type ProgressTone = 'accent' | 'negative';

export interface ProgressBarProps {
  /** Names the bar for assistive technology; the design shows no visible label. */
  readonly label: string;
  /** Percentage complete. Clamped to 0-100. */
  readonly value: number;
  /**
   * Where the plan says progress should have reached by now, as a percentage.
   * Drawn as a tick across the bar so being ahead or behind is readable at a
   * glance rather than by arithmetic (BR-11).
   */
  readonly paceMarker?: number;
  readonly tone?: ProgressTone;
  readonly className?: string | undefined;
}
