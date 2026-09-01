import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { fromDecimal } from '../../lib/money';

import { EditablePlanCell } from './EditablePlanCell';

describe('EditablePlanCell', () => {
  it('shows the planned amount as a plain decimal the user can edit', () => {
    render(
      <EditablePlanCell
        label="Planned amount for Groceries"
        value={fromDecimal('60')}
        onChange={vi.fn()}
      />,
    );

    expect(screen.getByRole('textbox', { name: 'Planned amount for Groceries' })).toHaveValue(
      '60.00',
    );
  });

  it('is reachable by its accessible name rather than by position', () => {
    render(
      <EditablePlanCell
        label="Planned amount for Rent"
        value={fromDecimal('780')}
        onChange={vi.fn()}
      />,
    );

    expect(screen.getByRole('textbox', { name: 'Planned amount for Rent' })).toBeInTheDocument();
  });

  it('commits the parsed amount when focus leaves the field', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<EditablePlanCell label="Planned" value={fromDecimal('60')} onChange={onChange} />);

    const field = screen.getByRole('textbox');
    await user.clear(field);
    await user.type(field, '75.50');
    await user.tab();

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith(fromDecimal('75.50'));
  });

  it('commits when Enter is pressed, without waiting for focus to move', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<EditablePlanCell label="Planned" value={fromDecimal('60')} onChange={onChange} />);

    const field = screen.getByRole('textbox');
    await user.clear(field);
    await user.type(field, '21{Enter}');

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith(fromDecimal('21'));
  });

  it('does not commit while the user is still typing', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<EditablePlanCell label="Planned" value={fromDecimal('60')} onChange={onChange} />);

    await user.clear(screen.getByRole('textbox'));
    await user.type(screen.getByRole('textbox'), '7');

    // "7" is a valid number, but committing mid-keystroke would rewrite the
    // plan on the way to "75.50".
    expect(onChange).not.toHaveBeenCalled();
  });

  it('lets a partly typed value exist without rejecting it', async () => {
    const user = userEvent.setup();
    render(<EditablePlanCell label="Planned" value={fromDecimal('60')} onChange={vi.fn()} />);

    const field = screen.getByRole('textbox');
    await user.clear(field);
    await user.type(field, '75.');

    expect(field).toHaveValue('75.');
  });

  it('restores the last good value when the entry is not a number', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<EditablePlanCell label="Planned" value={fromDecimal('60')} onChange={onChange} />);

    const field = screen.getByRole('textbox');
    await user.clear(field);
    await user.type(field, 'abc');
    await user.tab();

    expect(onChange).not.toHaveBeenCalled();
    expect(field).toHaveValue('60.00');
  });

  it('restores the last good value when the field is left empty', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<EditablePlanCell label="Planned" value={fromDecimal('60')} onChange={onChange} />);

    const field = screen.getByRole('textbox');
    await user.clear(field);
    await user.tab();

    expect(onChange).not.toHaveBeenCalled();
    expect(field).toHaveValue('60.00');
  });

  it('does not write the plan back when focus leaves an untouched field', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<EditablePlanCell label="Planned" value={fromDecimal('60')} onChange={onChange} />);

    await user.click(screen.getByRole('textbox'));
    await user.tab();

    expect(onChange).not.toHaveBeenCalled();
  });

  it('does not write the plan back when the value is retyped identically', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<EditablePlanCell label="Planned" value={fromDecimal('60')} onChange={onChange} />);

    const field = screen.getByRole('textbox');
    await user.clear(field);
    await user.type(field, '60.00');
    await user.tab();

    expect(onChange).not.toHaveBeenCalled();
  });

  it('follows the amount when the plan is changed from elsewhere', () => {
    const { rerender } = render(
      <EditablePlanCell label="Planned" value={fromDecimal('60')} onChange={vi.fn()} />,
    );
    rerender(<EditablePlanCell label="Planned" value={fromDecimal('95')} onChange={vi.fn()} />);

    expect(screen.getByRole('textbox')).toHaveValue('95.00');
  });

  it('asks for a numeric keypad on a phone', () => {
    render(<EditablePlanCell label="Planned" value={fromDecimal('60')} onChange={vi.fn()} />);

    expect(screen.getByRole('textbox')).toHaveAttribute('inputmode', 'decimal');
  });
});
