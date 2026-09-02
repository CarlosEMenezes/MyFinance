export interface LeadTimeToggleProps {
  /** How many days before a payment is due to warn. BR-12 allows 10, 5 and 2. */
  readonly days: number;
  readonly enabled: boolean;
  /** How many due items this lead time currently catches. */
  readonly itemCount: number;
  readonly onChange: (enabled: boolean) => void;
}
