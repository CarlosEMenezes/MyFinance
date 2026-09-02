import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { fromIso } from '../../lib/dates';
import { fromDecimal } from '../../lib/money';

import { InstalmentCalculatorPanel } from './InstalmentCalculatorPanel';

const noop = {
  onInstalmentCountChange: vi.fn(),
  onInstalmentAmountChange: vi.fn(),
  onFrequencyChange: vi.fn(),
};

/** The prototype's laptop plan: 399 cash, 6 x 71.50 monthly. */
const laptop = {
  mode: 'INSTALMENT',
  principal: fromDecimal('399'),
  instalmentCount: '6',
  instalmentAmount: '71.50',
  frequency: 'MONTHLY',
  firstDueDate: fromIso('2026-09-05'),
  ...noop,
} as const;

/** The prototype's credit union loan: 2500 received, 24 x 118.40 monthly. */
const creditUnion = {
  mode: 'LOAN',
  principal: fromDecimal('2500'),
  instalmentCount: '24',
  instalmentAmount: '118.40',
  frequency: 'MONTHLY',
  ...noop,
} as const;

describe('InstalmentCalculatorPanel - the inputs', () => {
  it('asks for the number of instalments when spreading a purchase', () => {
    render(<InstalmentCalculatorPanel {...laptop} />);

    expect(screen.getByLabelText('Number of instalments')).toHaveValue('6');
  });

  it('asks for a duration in months when the money is borrowed', () => {
    render(<InstalmentCalculatorPanel {...creditUnion} />);

    expect(screen.getByLabelText('Duration in months')).toHaveValue('24');
  });

  it('reports a change to the instalment amount', async () => {
    const onInstalmentAmountChange = vi.fn();
    const user = userEvent.setup();
    render(
      <InstalmentCalculatorPanel {...laptop} onInstalmentAmountChange={onInstalmentAmountChange} />,
    );

    await user.type(screen.getByLabelText('Amount per instalment'), '9');

    expect(onInstalmentAmountChange).toHaveBeenCalled();
  });

  it('reports a change to the number of instalments', async () => {
    const onInstalmentCountChange = vi.fn();
    const user = userEvent.setup();
    render(
      <InstalmentCalculatorPanel {...laptop} onInstalmentCountChange={onInstalmentCountChange} />,
    );

    await user.type(screen.getByLabelText('Number of instalments'), '2');

    expect(onInstalmentCountChange).toHaveBeenCalled();
  });

  it('offers the repayment frequency', async () => {
    const onFrequencyChange = vi.fn();
    const user = userEvent.setup();
    render(<InstalmentCalculatorPanel {...laptop} onFrequencyChange={onFrequencyChange} />);

    await user.click(screen.getByRole('radio', { name: 'Weekly' }));

    expect(onFrequencyChange).toHaveBeenCalledWith('WEEKLY');
  });
});

describe('InstalmentCalculatorPanel - waiting for enough to work with', () => {
  it('shows no figures until an amount has been entered', () => {
    render(<InstalmentCalculatorPanel {...laptop} instalmentAmount="" />);

    expect(screen.queryByText('Financed total')).not.toBeInTheDocument();
  });

  it('shows no figures while the amount is only half typed', () => {
    render(<InstalmentCalculatorPanel {...laptop} instalmentAmount="71." />);

    expect(screen.queryByText('Financed total')).not.toBeInTheDocument();
  });

  it('shows no figures without a count', () => {
    render(<InstalmentCalculatorPanel {...laptop} instalmentCount="0" />);

    expect(screen.queryByText('Financed total')).not.toBeInTheDocument();
  });
});

describe('InstalmentCalculatorPanel - spreading a purchase (BR-6)', () => {
  it('shows what the plan really costs', () => {
    render(<InstalmentCalculatorPanel {...laptop} />);

    expect(screen.getByText('Cash price')).toBeInTheDocument();
    expect(screen.getByText('\u20ac399.00')).toBeInTheDocument();
    expect(screen.getByText('\u20ac429.00')).toBeInTheDocument();
    expect(screen.getByText('+\u20ac30.00')).toBeInTheDocument();
  });

  it('states the rate per period and the APR', () => {
    render(<InstalmentCalculatorPanel {...laptop} />);

    expect(screen.getByText(/2\.11% a month/)).toBeInTheDocument();
    expect(screen.getByText(/28\.5%/)).toBeInTheDocument();
  });

  it('says when the first instalment actually lands (BR-4)', () => {
    render(<InstalmentCalculatorPanel {...laptop} />);

    expect(screen.getByText('05-09-2026')).toBeInTheDocument();
  });

  it('omits the due date until the card cycle is known', () => {
    render(<InstalmentCalculatorPanel {...laptop} firstDueDate={undefined} />);

    expect(screen.queryByText('First instalment due')).not.toBeInTheDocument();
    expect(screen.getByText('Financed total')).toBeInTheDocument();
  });

  it('says plainly what spreading the cost adds', () => {
    render(<InstalmentCalculatorPanel {...laptop} />);

    expect(screen.getByText(/costs \u20ac30\.00 extra/)).toBeInTheDocument();
  });

  it('calls a plan interest free when the rounding tolerance absorbs it', () => {
    // 3 x 61.34 = 184.02 against a 184.00 price: 2c of rounding, not interest.
    render(
      <InstalmentCalculatorPanel
        {...laptop}
        principal={fromDecimal('184')}
        instalmentCount="3"
        instalmentAmount="61.34"
      />,
    );

    expect(screen.getByText(/Interest free/)).toBeInTheDocument();
    expect(screen.getByText('0%')).toBeInTheDocument();
  });
});

describe('InstalmentCalculatorPanel - borrowing (BR-2, BR-7)', () => {
  it('shows both sides of the loan moving', () => {
    render(<InstalmentCalculatorPanel {...creditUnion} />);

    expect(screen.getByText('Adds to money now')).toBeInTheDocument();
    expect(screen.getByText('+\u20ac2,500.00')).toBeInTheDocument();
    expect(screen.getByText('Creates a debt of')).toBeInTheDocument();
    expect(screen.getByText('\u20ac2,841.60')).toBeInTheDocument();
  });

  it('states the repayment that becomes a planned expense (BR-3)', () => {
    render(<InstalmentCalculatorPanel {...creditUnion} />);

    expect(screen.getByText(/\u20ac118\.40 every month/)).toBeInTheDocument();
  });

  it('says what clearing it today would cost and save', () => {
    render(<InstalmentCalculatorPanel {...creditUnion} />);

    expect(screen.getByText(/Clearing it today costs/)).toBeInTheDocument();
  });

  it('says an interest-free loan gains nothing by early payment', () => {
    render(
      <InstalmentCalculatorPanel
        {...creditUnion}
        principal={fromDecimal('600')}
        instalmentCount="6"
        instalmentAmount="100"
      />,
    );

    expect(screen.getByText(/Paying early gains nothing/)).toBeInTheDocument();
  });
});
