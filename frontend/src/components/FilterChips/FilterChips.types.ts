export interface FilterOption {
  readonly value: string;
  readonly label: string;
}

export interface FilterChipsProps {
  /** Radio group name. Must be unique on the page. */
  readonly name: string;
  /** Shown beside the chips, e.g. "Paid with". */
  readonly label: string;
  readonly options: readonly FilterOption[];
  readonly value: string;
  readonly onChange: (value: string) => void;
  readonly className?: string | undefined;
}
