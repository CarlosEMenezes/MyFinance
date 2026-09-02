import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { LeadTimeToggle } from './LeadTimeToggle';

describe('LeadTimeToggle - BR-12', () => {
  it('says how far ahead it warns', () => {
    render(<LeadTimeToggle days={10} enabled itemCount={3} onChange={vi.fn()} />);

    expect(screen.getByRole('checkbox', { name: /10 days before due/ })).toBeInTheDocument();
  });

  it('reflects whether the lead time is on', () => {
    render(<LeadTimeToggle days={5} enabled={false} itemCount={1} onChange={vi.fn()} />);

    expect(screen.getByRole('checkbox')).not.toBeChecked();
  });

  it('reports being switched on', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<LeadTimeToggle days={2} enabled={false} itemCount={0} onChange={onChange} />);

    await user.click(screen.getByRole('checkbox'));

    expect(onChange).toHaveBeenCalledWith(true);
  });

  it('says how many items the lead time currently catches', () => {
    render(<LeadTimeToggle days={10} enabled itemCount={3} onChange={vi.fn()} />);

    expect(screen.getByText('3 items')).toBeInTheDocument();
  });

  it('counts a single item in the singular', () => {
    render(<LeadTimeToggle days={5} enabled itemCount={1} onChange={vi.fn()} />);

    expect(screen.getByText('1 item')).toBeInTheDocument();
  });

  it('says none rather than 0 items when nothing is due that soon', () => {
    render(<LeadTimeToggle days={2} enabled itemCount={0} onChange={vi.fn()} />);

    expect(screen.getByText('nothing yet')).toBeInTheDocument();
  });
});
