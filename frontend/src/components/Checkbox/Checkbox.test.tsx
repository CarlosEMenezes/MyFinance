import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { Checkbox } from './Checkbox';

describe('Checkbox', () => {
  it('is a real checkbox, reachable by its label', () => {
    render(<Checkbox label="Count in total money now" checked onChange={vi.fn()} />);

    expect(screen.getByRole('checkbox', { name: /Count in total money now/ })).toBeChecked();
  });

  it('is unchecked when it is off', () => {
    render(<Checkbox label="Email" checked={false} onChange={vi.fn()} />);

    expect(screen.getByRole('checkbox', { name: /Email/ })).not.toBeChecked();
  });

  it('reports the new state when clicked', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<Checkbox label="Email" checked={false} onChange={onChange} />);

    await user.click(screen.getByRole('checkbox'));

    expect(onChange).toHaveBeenCalledWith(true);
  });

  it('reports being switched off', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<Checkbox label="Push notification" checked onChange={onChange} />);

    await user.click(screen.getByRole('checkbox'));

    expect(onChange).toHaveBeenCalledWith(false);
  });

  it('toggles from the keyboard, because it is a real control', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<Checkbox label="Email" checked={false} onChange={onChange} />);

    await user.tab();
    await user.keyboard(' ');

    expect(onChange).toHaveBeenCalledWith(true);
  });

  it('shows a hint under the label when the choice needs explaining', () => {
    render(
      <Checkbox
        label="Round goal contributions up"
        hint="To the nearest euro, so the target is never short"
        checked
        onChange={vi.fn()}
      />,
    );

    expect(
      screen.getByText('To the nearest euro, so the target is never short'),
    ).toBeInTheDocument();
  });

  it('includes the hint in the accessible name, so it is not lost to a screen reader', () => {
    render(<Checkbox label="Email" hint="Sent to your inbox" checked onChange={vi.fn()} />);

    expect(
      screen.getByRole('checkbox', { name: /Email.*Sent to your inbox/s }),
    ).toBeInTheDocument();
  });

  it('shows a trailing note, such as how many items a setting affects', () => {
    render(<Checkbox label="10 days before due" meta="3 items" checked onChange={vi.fn()} />);

    expect(screen.getByText('3 items')).toBeInTheDocument();
  });

  it('draws the box, which is decorative because the input carries the state', () => {
    const { container } = render(<Checkbox label="Email" checked onChange={vi.fn()} />);

    expect(container.querySelector('[aria-hidden="true"]')).toBeInTheDocument();
  });
});
