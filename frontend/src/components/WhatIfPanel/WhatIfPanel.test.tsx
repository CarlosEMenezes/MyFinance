import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { fromIso } from '../../lib/dates';
import { fromDecimal } from '../../lib/money';

import { WhatIfPanel } from './WhatIfPanel';
import type { WhatIfPanelProps } from './WhatIfPanel.types';

/** The prototype's MacBook goal: 1349 target, 410 saved, four months out. */
const macBook = {
  goalName: 'MacBook Air M4',
  targetAmount: fromDecimal('1349'),
  savedAmount: fromDecimal('410'),
  months: 4,
  frequency: 'MONTHLY',
  monthlySpare: fromDecimal('400'),
  today: fromIso('2026-08-31'),
} as const;

const show = (overrides: Partial<WhatIfPanelProps> = {}) =>
  render(
    <WhatIfPanel
      {...macBook}
      onMonthsChange={vi.fn()}
      onFrequencyChange={vi.fn()}
      onApply={vi.fn()}
      {...overrides}
    />,
  );

describe('WhatIfPanel - the headline', () => {
  it('names the goal being planned', () => {
    show();

    expect(screen.getByText(/MacBook Air M4/)).toBeInTheDocument();
  });

  it('leads with what has to be put aside each period', () => {
    // Saving monthly, the headline and the per-month line agree by
    // definition, so the assertion names which one it means.
    const { container } = show();

    expect(container.querySelector('[class*="headline"]')).toHaveTextContent('€234.75');
  });

  it('says what that closes and by when', () => {
    show();

    expect(screen.getByText(/to close \u20ac939\.00 by 31-12-2026/)).toBeInTheDocument();
  });

  it('divides across weeks when saving weekly', () => {
    show({ frequency: 'WEEKLY' });

    expect(screen.getByText('\u20ac54.21')).toBeInTheDocument();
    expect(screen.getByText(/per week/)).toBeInTheDocument();
  });

  it('divides across days when saving daily', () => {
    show({ frequency: 'DAILY' });

    expect(screen.getByText('\u20ac7.72')).toBeInTheDocument();
  });
});

describe('WhatIfPanel - moving the target date', () => {
  it('offers a slider across the range BR-11 allows', () => {
    show();
    const slider = screen.getByRole('slider');

    expect(slider).toHaveAttribute('min', '1');
    expect(slider).toHaveAttribute('max', '36');
    expect(slider).toHaveValue('4');
  });

  it('says how far out the target currently is', () => {
    show();

    expect(screen.getByText(/4 months out/)).toBeInTheDocument();
  });

  it('reports the new horizon when the slider is dragged', () => {
    const onMonthsChange = vi.fn();
    show({ onMonthsChange });

    // userEvent cannot drag a range input; a change event is what the
    // browser actually dispatches.
    fireEvent.change(screen.getByRole('slider'), { target: { value: '12' } });

    expect(onMonthsChange).toHaveBeenCalledWith(12);
  });

  it('asks for less each month when the date is pushed further out', () => {
    const { container } = show({ months: 12 });

    expect(container.querySelector('[class*="headline"]')).toHaveTextContent('€78.25');
  });
});

describe('WhatIfPanel - the frequency', () => {
  it('offers the three saving rhythms', () => {
    show();

    expect(screen.getByRole('radio', { name: 'Daily' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Weekly' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Monthly' })).toBeChecked();
  });

  it('reports a change of rhythm', async () => {
    const onFrequencyChange = vi.fn();
    const user = userEvent.setup();
    show({ onFrequencyChange });

    await user.click(screen.getByRole('radio', { name: 'Weekly' }));

    expect(onFrequencyChange).toHaveBeenCalledWith('WEEKLY');
  });
});

describe('WhatIfPanel - the breakdown', () => {
  it('shows the goal, what is saved and what is left', () => {
    show();
    const lines = screen.getByRole('list', { name: /breakdown/i });

    expect(within(lines).getByText('\u20ac1,349.00')).toBeInTheDocument();
    expect(within(lines).getByText('\u20ac410.00')).toBeInTheDocument();
    expect(within(lines).getByText('\u20ac939.00')).toBeInTheDocument();
  });

  it('always states the per-month equivalent, whatever the rhythm', () => {
    show({ frequency: 'WEEKLY' });
    const lines = screen.getByRole('list', { name: /breakdown/i });

    expect(within(lines).getByText('\u20ac234.75')).toBeInTheDocument();
  });

  it('marks spare money as over plan when there is none left', () => {
    show({ monthlySpare: fromDecimal('-120') });
    const lines = screen.getByRole('list', { name: /breakdown/i });

    expect(within(lines).getByText('-€120.00').className).toMatch(/bad/);
  });

  it('shows what is currently spare each month', () => {
    show();
    const lines = screen.getByRole('list', { name: /breakdown/i });

    expect(within(lines).getByText('\u20ac400.00')).toBeInTheDocument();
  });
});

describe('WhatIfPanel - whether it is realistic (BR-11)', () => {
  it('says what would be left over when the goal fits', () => {
    show();

    expect(screen.getByText(/Fits inside your current spare/)).toBeInTheDocument();
    expect(screen.getByText(/\u20ac165\.25 left over/)).toBeInTheDocument();
  });

  it('says how much is missing when it does not fit, and what to do about it', () => {
    show({ monthlySpare: fromDecimal('150') });

    expect(screen.getByText(/\u20ac84\.75 a month more than you have spare/)).toBeInTheDocument();
    expect(screen.getByText(/Push the date out or cut a category/)).toBeInTheDocument();
  });
});

describe('WhatIfPanel - applying', () => {
  it('offers to commit the plan', async () => {
    const onApply = vi.fn();
    const user = userEvent.setup();
    show({ onApply });

    await user.click(screen.getByRole('button', { name: 'Apply to plan' }));

    expect(onApply).toHaveBeenCalledTimes(1);
  });

  it('wears the blueprint frame and its registration marks', () => {
    const { container } = show();

    expect(container.firstElementChild).toHaveClass('blueprint');
    expect(container.querySelectorAll('.corner')).toHaveLength(4);
  });
});
