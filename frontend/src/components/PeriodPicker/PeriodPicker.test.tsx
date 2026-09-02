import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { PeriodPicker } from './PeriodPicker';

const show = (overrides = {}) =>
  render(
    <PeriodPicker
      value="MONTH"
      onChange={vi.fn()}
      rangeLabel="01-08 → 31-08-2026"
      {...overrides}
    />,
  );

describe('PeriodPicker', () => {
  it('offers every window the app reports on', () => {
    show();

    for (const label of ['Day', 'Week', 'Month', 'Year', 'Custom']) {
      expect(screen.getByRole('radio', { name: label })).toBeInTheDocument();
    }
  });

  it('marks the window currently in view', () => {
    show();

    expect(screen.getByRole('radio', { name: 'Month' })).toBeChecked();
  });

  it('reports the window that was picked', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    show({ onChange });

    await user.click(screen.getByRole('radio', { name: 'Year' }));

    expect(onChange).toHaveBeenCalledWith('YEAR');
  });

  it('spells out the dates the window actually covers', () => {
    show();

    expect(screen.getByText('01-08 → 31-08-2026')).toBeInTheDocument();
  });

  it('is a named group, since it carries no visible label', () => {
    show();

    expect(screen.getByRole('radiogroup', { name: 'Period' })).toBeInTheDocument();
  });
});
