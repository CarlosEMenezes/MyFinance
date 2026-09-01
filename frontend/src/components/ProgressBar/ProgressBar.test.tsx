import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { ProgressBar } from './ProgressBar';

describe('ProgressBar', () => {
  it('is a labelled progress bar', () => {
    render(<ProgressBar label="MacBook Air M4 progress" value={30} />);

    expect(
      screen.getByRole('progressbar', { name: 'MacBook Air M4 progress' }),
    ).toBeInTheDocument();
  });

  it('reports its value to assistive technology', () => {
    render(<ProgressBar label="Saved" value={30} />);
    const bar = screen.getByRole('progressbar');

    expect(bar).toHaveAttribute('aria-valuenow', '30');
    expect(bar).toHaveAttribute('aria-valuemin', '0');
    expect(bar).toHaveAttribute('aria-valuemax', '100');
  });

  it('fills to the value', () => {
    const { container } = render(<ProgressBar label="Saved" value={30} />);

    expect(container.querySelector('[data-testid="fill"]')).toHaveStyle({ width: '30%' });
  });

  it('clamps a value above the maximum, so the fill cannot overflow its frame', () => {
    const { container } = render(<ProgressBar label="Saved" value={150} />);

    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100');
    expect(container.querySelector('[data-testid="fill"]')).toHaveStyle({ width: '100%' });
  });

  it('clamps a negative value to nothing', () => {
    render(<ProgressBar label="Saved" value={-20} />);

    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '0');
  });

  it('draws no pace marker unless it is given one', () => {
    const { container } = render(<ProgressBar label="Saved" value={30} />);

    expect(container.querySelector('[data-testid="pace"]')).not.toBeInTheDocument();
  });

  it('draws the pace marker where the plan says it should have reached', () => {
    const { container } = render(<ProgressBar label="Saved" value={30} paceMarker={45} />);

    expect(container.querySelector('[data-testid="pace"]')).toHaveStyle({ left: '45%' });
  });

  it('describes the pace marker rather than leaving it as an unexplained line', () => {
    render(<ProgressBar label="Saved" value={30} paceMarker={45} />);

    expect(screen.getByRole('progressbar')).toHaveAttribute(
      'aria-valuetext',
      '30% saved, 45% expected by now',
    );
  });

  it('turns to the over-plan tone when a credit card is running hot', () => {
    const { container } = render(<ProgressBar label="Card usage" value={82} tone="negative" />);

    expect(container.querySelector('[data-testid="fill"]')?.className).toMatch(/negative/);
  });

  it('uses the accent tone by default', () => {
    const { container } = render(<ProgressBar label="Saved" value={30} />);

    expect(container.querySelector('[data-testid="fill"]')?.className).toMatch(/accent/);
  });
});
