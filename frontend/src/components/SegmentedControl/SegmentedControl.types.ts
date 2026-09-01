export interface SegmentedOption<T extends string> {
  readonly value: T;
  readonly label: string;
}

export interface SegmentedControlProps<T extends string> {
  /**
   * Radio group name. Must be unique on the page, or two controls will fight
   * over the same selection.
   */
  readonly name: string;
  /** Names the control. Shown beside it unless `hideLabel` is set. */
  readonly label: string;
  /** Hide the label visually while keeping it for assistive technology. */
  readonly hideLabel?: boolean;
  readonly options: readonly SegmentedOption<T>[];
  readonly value: T;
  readonly onChange: (value: T) => void;
  readonly className?: string | undefined;
}
