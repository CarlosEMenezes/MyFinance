import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { FilterChips } from './FilterChips';

const options = [
  { value: 'ALL', label: 'All' },
  { value: 'wallet', label: 'Wallet' },
  { value: 'revolut', label: 'Revolut Current' },
  { value: 'visa', label: 'Visa 4417' },
];

const show = (overrides = {}) =>
  render(
    <FilterChips
      name="paid-with"
      label="Paid with"
      options={options}
      value="ALL"
      onChange={vi.fn()}
      {...overrides}
    />,
  );

describe('FilterChips - BR-15', () => {
  it('is a named group of choices', () => {
    show();

    expect(screen.getByRole('radiogroup', { name: 'Paid with' })).toBeInTheDocument();
  });

  it('shows its label, because the design puts it beside the chips', () => {
    show();

    expect(screen.getByText('Paid with')).toBeVisible();
  });

  it('offers each payment method as a choice', () => {
    show();

    expect(screen.getAllByRole('radio')).toHaveLength(4);
    expect(screen.getByRole('radio', { name: 'Visa 4417' })).toBeInTheDocument();
  });

  it('marks the filter in force', () => {
    show({ value: 'visa' });

    expect(screen.getByRole('radio', { name: 'Visa 4417' })).toBeChecked();
    expect(screen.getByRole('radio', { name: 'All' })).not.toBeChecked();
  });

  it('reports the filter that was chosen', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    show({ onChange });

    await user.click(screen.getByRole('radio', { name: 'Wallet' }));

    expect(onChange).toHaveBeenCalledWith('wallet');
  });

  it('moves between chips with the arrow keys, being a radio group', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    show({ onChange });

    await user.click(screen.getByRole('radio', { name: 'All' }));
    await user.keyboard('{ArrowRight}');

    expect(onChange).toHaveBeenCalled();
  });
});
