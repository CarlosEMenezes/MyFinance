import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { Panel } from './Panel';

describe('Panel', () => {
  it('renders its children', () => {
    render(<Panel>Where it went</Panel>);

    expect(screen.getByText('Where it went')).toBeInTheDocument();
  });

  it('always draws four registration marks, because a framed element without them is off-system', () => {
    const { container } = render(<Panel>content</Panel>);

    expect(container.querySelectorAll('.corner')).toHaveLength(4);
    expect(container.querySelector('.corner.tl')).toBeInTheDocument();
    expect(container.querySelector('.corner.tr')).toBeInTheDocument();
    expect(container.querySelector('.corner.bl')).toBeInTheDocument();
    expect(container.querySelector('.corner.br')).toBeInTheDocument();
  });

  it('hides the registration marks from assistive technology', () => {
    const { container } = render(<Panel>content</Panel>);

    for (const corner of container.querySelectorAll('.corner')) {
      expect(corner).toHaveAttribute('aria-hidden', 'true');
    }
  });

  it('wears the design system frame class the registration marks hang off', () => {
    const { container } = render(<Panel>content</Panel>);

    expect(container.firstElementChild).toHaveClass('blueprint');
  });

  it('is comfortably padded by default', () => {
    const { container } = render(<Panel>content</Panel>);

    expect(container.firstElementChild?.className).toMatch(/comfortable/);
  });

  it('takes the tighter KPI density when asked, without a consumer out-specifying anything', () => {
    const { container } = render(<Panel density="compact">content</Panel>);

    expect(container.firstElementChild?.className).toMatch(/compact/);
    expect(container.firstElementChild?.className).not.toMatch(/comfortable/);
  });

  it('renders the title as a heading below the page title', () => {
    render(<Panel title="Upcoming">content</Panel>);

    expect(screen.getByRole('heading', { level: 2, name: 'Upcoming' })).toBeInTheDocument();
  });

  it('renders no heading when it has no title', () => {
    render(<Panel>content</Panel>);

    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });

  it('renders the meta note beside the title', () => {
    render(
      <Panel title="Breakdown — planned against real" meta="Ghost row = planned">
        content
      </Panel>,
    );

    expect(screen.getByText('Ghost row = planned')).toBeInTheDocument();
  });

  it('renders a meta note even with no title', () => {
    render(<Panel meta="Cash price against financed total">content</Panel>);

    expect(screen.getByText('Cash price against financed total')).toBeInTheDocument();
  });

  it('renders the subtitle under the title', () => {
    render(
      <Panel title="Jobs" subtitle="Rate per job or per hour">
        content
      </Panel>,
    );

    expect(screen.getByText('Rate per job or per hour')).toBeInTheDocument();
  });

  it('accepts layout classes without losing the frame', () => {
    const { container } = render(<Panel className="gwide">content</Panel>);
    const panel = container.firstElementChild;

    expect(panel).toHaveClass('gwide');
    expect(panel).toHaveClass('blueprint');
  });
});
