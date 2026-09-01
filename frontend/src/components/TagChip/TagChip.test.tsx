import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { TagChip } from './TagChip';

describe('TagChip', () => {
  it('renders its content', () => {
    render(<TagChip>Credit</TagChip>);

    expect(screen.getByText('Credit')).toBeInTheDocument();
  });

  it('is a neutral chip by default', () => {
    render(<TagChip>Debit</TagChip>);

    expect(screen.getByText('Debit')).toHaveClass('tag', 'tag-neutral');
  });

  it('tints with the accent when the chip carries meaning', () => {
    render(<TagChip variant="accent">On pace</TagChip>);

    expect(screen.getByText('On pace')).toHaveClass('tag', 'tag-accent');
  });

  it('outlines for a currency tag, which marks a converted amount', () => {
    render(<TagChip variant="outline">USD 456.00</TagChip>);

    expect(screen.getByText('USD 456.00')).toHaveClass('tag', 'tag-outline');
  });

  it('accepts layout classes without losing the chip styling', () => {
    render(<TagChip className="dense">Fixed</TagChip>);
    const chip = screen.getByText('Fixed');

    expect(chip).toHaveClass('tag');
    expect(chip).toHaveClass('dense');
  });
});
