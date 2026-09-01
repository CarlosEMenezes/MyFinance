import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { KpiCard } from './KpiCard';

describe('KpiCard', () => {
  it('renders the kicker, the figure and the note', () => {
    render(
      <KpiCard kicker="Total money now" value="€2,412.30" note="available minus everything owed" />,
    );

    expect(screen.getByText('Total money now')).toBeInTheDocument();
    expect(screen.getByText('€2,412.30')).toBeInTheDocument();
    expect(screen.getByText('available minus everything owed')).toBeInTheDocument();
  });

  it('renders without a note', () => {
    render(<KpiCard kicker="Owed" value="€1,200.00" />);

    expect(screen.getByText('€1,200.00')).toBeInTheDocument();
  });

  it('accepts a node as the figure, so formatting stays at the edge that owns it', () => {
    render(<KpiCard kicker="MacBook Air M4" value={<span data-testid="figure">30%</span>} />);

    expect(screen.getByTestId('figure')).toHaveTextContent('30%');
  });

  it('inherits the blueprint frame and its registration marks from Panel', () => {
    const { container } = render(<KpiCard kicker="Owed" value="€1,200.00" />);

    expect(container.firstElementChild).toHaveClass('blueprint');
    expect(container.querySelectorAll('.corner')).toHaveLength(4);
  });

  it('uses the tighter KPI density', () => {
    const { container } = render(<KpiCard kicker="Owed" value="€1,200.00" />);

    expect(container.firstElementChild?.className).toMatch(/compact/);
  });

  it('renders the figure neutrally by default', () => {
    render(<KpiCard kicker="Available now" value="€2,412.30" />);

    expect(screen.getByText('€2,412.30').className).toMatch(/neutral/);
  });

  it('carries the over-plan tone for money owed', () => {
    render(<KpiCard kicker="Owed" value="€1,200.00" tone="negative" />);

    expect(screen.getByText('€1,200.00').className).toMatch(/negative/);
  });

  it('carries the accent tone for goal progress', () => {
    render(<KpiCard kicker="MacBook Air M4" value="30%" tone="accent" />);

    expect(screen.getByText('30%').className).toMatch(/accent/);
  });

  it('carries the muted tone for a reference figure', () => {
    render(<KpiCard kicker="Planned" value="€1,405.00" tone="muted" />);

    expect(screen.getByText('€1,405.00').className).toMatch(/muted/);
  });
});
