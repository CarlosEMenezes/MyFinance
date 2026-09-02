import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { EmptyState } from './EmptyState';

describe('EmptyState', () => {
  it('says what is missing', () => {
    render(<EmptyState title="No cards yet" message="Add a card to see its billing cycle." />);

    expect(screen.getByRole('heading', { name: 'No cards yet' })).toBeInTheDocument();
    expect(screen.getByText('Add a card to see its billing cycle.')).toBeInTheDocument();
  });

  it('offers the way out of the empty state when there is one', () => {
    render(
      <EmptyState
        title="No cards yet"
        message="Add a card to see its billing cycle."
        action={<button type="button">Add a card</button>}
      />,
    );

    expect(screen.getByRole('button', { name: 'Add a card' })).toBeInTheDocument();
  });

  it('renders without an action, for the states there is nothing to do about', () => {
    render(<EmptyState title="Nothing due" message="No payments are coming up." />);

    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('wears the blueprint frame like every other panel', () => {
    const { container } = render(<EmptyState title="Nothing due" message="All clear." />);

    expect(container.firstElementChild).toHaveClass('blueprint');
    expect(container.querySelectorAll('.corner')).toHaveLength(4);
  });
});
