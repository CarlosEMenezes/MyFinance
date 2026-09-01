import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, it } from 'vitest';

import { GhostPlanRow } from './GhostPlanRow';

const inTable = (row: ReactNode) =>
  render(
    <table>
      <tbody>{row}</tbody>
    </table>,
  );

describe('GhostPlanRow', () => {
  it('names what is planned and how often it lands', () => {
    inTable(<GhostPlanRow note="each week x 5">cell</GhostPlanRow>);

    expect(screen.getByText(/planned each week x 5/)).toBeInTheDocument();
  });

  it('renders the planned cell it is given', () => {
    inTable(
      <GhostPlanRow note="this month">
        <span data-testid="planned">780.00</span>
      </GhostPlanRow>,
    );

    expect(screen.getByTestId('planned')).toHaveTextContent('780.00');
  });

  it('lays out four cells when there is no payment-method column', () => {
    const { container } = inTable(<GhostPlanRow note="this month">cell</GhostPlanRow>);

    expect(container.querySelectorAll('td')).toHaveLength(4);
  });

  it('lays out five cells when the payment-method column is shown', () => {
    const { container } = inTable(
      <GhostPlanRow note="this month" showPaymentMethod>
        cell
      </GhostPlanRow>,
    );

    expect(container.querySelectorAll('td')).toHaveLength(5);
  });

  it('shows when the money is due in the payment-method column', () => {
    inTable(
      <GhostPlanRow note="this month" showPaymentMethod dueNote="due 01-09">
        cell
      </GhostPlanRow>,
    );

    expect(screen.getByText('due 01-09')).toBeInTheDocument();
  });

  it('hides the leading rule mark from assistive technology', () => {
    const { container } = inTable(<GhostPlanRow note="this month">cell</GhostPlanRow>);

    expect(container.querySelector('[aria-hidden="true"]')).toBeInTheDocument();
  });
});
