import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { fromDecimal } from '../../lib/money';

import { MoneyText } from './MoneyText';

describe('MoneyText', () => {
  it('formats an amount in the default currency', () => {
    render(<MoneyText amount={fromDecimal('1234.56')} />);

    expect(screen.getByText('€1,234.56')).toBeInTheDocument();
  });

  it('formats an amount in another currency', () => {
    render(<MoneyText amount={fromDecimal('12.99')} currency="USD" />);

    expect(screen.getByText('$12.99')).toBeInTheDocument();
  });

  it('places the sign before the symbol for a negative amount', () => {
    render(<MoneyText amount={fromDecimal('-386.40')} />);

    expect(screen.getByText('-€386.40')).toBeInTheDocument();
  });

  it('shows an explicit sign when asked, for a delta rather than a balance', () => {
    render(<MoneyText amount={fromDecimal('450')} signed />);

    expect(screen.getByText('+€450.00')).toBeInTheDocument();
  });

  it('uses a true minus rather than a hyphen when signed', () => {
    render(<MoneyText amount={fromDecimal('-450')} signed />);

    expect(screen.getByText('\u2212€450.00')).toBeInTheDocument();
  });

  it('always uses tabular numerals, so figures line up in a column', () => {
    render(<MoneyText amount={fromDecimal('9.99')} />);

    expect(screen.getByText('€9.99')).toHaveClass('tabular');
  });

  it('accepts layout classes without losing the tabular numerals', () => {
    render(<MoneyText amount={fromDecimal('9.99')} className="right" />);
    const figure = screen.getByText('€9.99');

    expect(figure).toHaveClass('tabular');
    expect(figure).toHaveClass('right');
  });
});
