import type { ReactNode } from 'react';

/**
 * The navigation icon set, ported from the prototype's `ICONS`.
 *
 * These are marks, not components: each is a bare `<svg>` with no props, no
 * state and no behaviour, so they live in one module rather than one folder
 * apiece (spec §0.6 governs reusable *components*). Every one inherits
 * `currentColor`, so a nav decides the colour and the icon never disagrees
 * with the label beside it.
 *
 * `aria-hidden` throughout: an icon that repeats the label it sits next to is
 * noise to anyone listening rather than looking.
 */

function Glyph({ children }: { readonly children: ReactNode }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.5}
      strokeLinecap="round"
      strokeLinejoin="round"
      width="100%"
      height="100%"
      aria-hidden="true"
      focusable="false"
    >
      {children}
    </svg>
  );
}

export const OverviewIcon = () => (
  <Glyph>
    <rect x={3} y={3} width={7} height={9} />
    <rect x={14} y={3} width={7} height={5} />
    <rect x={14} y={12} width={7} height={9} />
    <rect x={3} y={16} width={7} height={5} />
  </Glyph>
);

export const EarningsIcon = () => (
  <Glyph>
    <polyline points="22 7 13.5 15.5 8.5 10.5 2 17" />
    <polyline points="16 7 22 7 22 13" />
  </Glyph>
);

export const ExpensesIcon = () => (
  <Glyph>
    <path d="M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z" />
    <path d="M8 7h8M8 11h8M8 15h5" />
  </Glyph>
);

export const GoalsIcon = () => (
  <Glyph>
    <circle cx={12} cy={12} r={9} />
    <circle cx={12} cy={12} r={5} />
    <circle cx={12} cy={12} r={1.2} />
  </Glyph>
);

export const AccountsIcon = () => (
  <Glyph>
    <path d="m12 2 9 5-9 5-9-5 9-5Z" />
    <path d="m3 12 9 5 9-5" />
    <path d="m3 17 9 5 9-5" />
  </Glyph>
);

export const CardsIcon = () => (
  <Glyph>
    <rect x={2} y={5} width={20} height={14} />
    <path d="M2 10h20M6 15h4" />
  </Glyph>
);

export const CategoriesIcon = () => (
  <Glyph>
    <path d="M3 4h8l9 8-8 8-9-9V4Z" />
    <circle cx={7.5} cy={8.5} r={1.3} />
  </Glyph>
);

export const NotificationsIcon = () => (
  <Glyph>
    <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
    <path d="M10.3 21a1.9 1.9 0 0 0 3.4 0" />
  </Glyph>
);

export const SettingsIcon = () => (
  <Glyph>
    <path d="M4 6h10M18 6h2M4 12h2M10 12h10M4 18h12M20 18h0" />
    <circle cx={16} cy={6} r={2} />
    <circle cx={8} cy={12} r={2} />
    <circle cx={18} cy={18} r={2} />
  </Glyph>
);
