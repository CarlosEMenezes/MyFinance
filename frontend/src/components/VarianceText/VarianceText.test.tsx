import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { fromDecimal } from '../../lib/money';

import { VarianceText } from './VarianceText';

describe('VarianceText — the figure', () => {
  it('shows real minus planned for an earning', () => {
    render(
      <VarianceText type="EARNING" planned={fromDecimal('1200')} real={fromDecimal('1010')} />,
    );

    expect(screen.getByText('\u2212€190.00')).toBeInTheDocument();
  });

  it('shows real minus planned for an expense too, never flipped', () => {
    render(
      <VarianceText type="EXPENSE" planned={fromDecimal('60')} real={fromDecimal('318.40')} />,
    );

    expect(screen.getByText('+€258.40')).toBeInTheDocument();
  });

  it('shows zero unsigned', () => {
    render(<VarianceText type="EXPENSE" planned={fromDecimal('780')} real={fromDecimal('780')} />);

    expect(screen.getByText('€0.00')).toBeInTheDocument();
  });
});

describe('VarianceText — the colour convention (BR-9)', () => {
  it('reads earning over plan as good', () => {
    render(<VarianceText type="EARNING" planned={fromDecimal('160')} real={fromDecimal('672')} />);

    expect(screen.getByText('+€512.00').className).toMatch(/good/);
  });

  it('reads earning under plan as bad', () => {
    render(
      <VarianceText type="EARNING" planned={fromDecimal('1200')} real={fromDecimal('1010')} />,
    );

    expect(screen.getByText('\u2212€190.00').className).toMatch(/bad/);
  });

  it('reads spending over plan as bad', () => {
    render(
      <VarianceText type="EXPENSE" planned={fromDecimal('60')} real={fromDecimal('318.40')} />,
    );

    expect(screen.getByText('+€258.40').className).toMatch(/bad/);
  });

  it('reads spending under plan as good', () => {
    render(<VarianceText type="EXPENSE" planned={fromDecimal('45')} real={fromDecimal('44.98')} />);

    expect(screen.getByText('\u2212€0.02').className).toMatch(/good/);
  });

  it('reads no difference as neutral, on either side', () => {
    const { rerender } = render(
      <VarianceText type="EXPENSE" planned={fromDecimal('29')} real={fromDecimal('29')} />,
    );
    expect(screen.getByText('€0.00').className).toMatch(/neutral/);

    rerender(<VarianceText type="EARNING" planned={fromDecimal('29')} real={fromDecimal('29')} />);
    expect(screen.getByText('€0.00').className).toMatch(/neutral/);
  });

  it('gives the same figure but the opposite colour for the same numbers', () => {
    const { rerender } = render(
      <VarianceText type="EARNING" planned={fromDecimal('100')} real={fromDecimal('150')} />,
    );
    expect(screen.getByText('+€50.00').className).toMatch(/good/);

    rerender(
      <VarianceText type="EXPENSE" planned={fromDecimal('100')} real={fromDecimal('150')} />,
    );
    expect(screen.getByText('+€50.00').className).toMatch(/bad/);
  });
});

describe('VarianceText — presentation', () => {
  it('uses tabular numerals', () => {
    render(<VarianceText type="EXPENSE" planned={fromDecimal('10')} real={fromDecimal('12')} />);

    expect(screen.getByText('+€2.00')).toHaveClass('tabular');
  });

  it('formats in another currency', () => {
    render(
      <VarianceText
        type="EARNING"
        planned={fromDecimal('100')}
        real={fromDecimal('150')}
        currency="GBP"
      />,
    );

    expect(screen.getByText('+£50.00')).toBeInTheDocument();
  });
});
