import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { PageHeader } from './PageHeader';

describe('PageHeader', () => {
  it('titles the page as its only level-one heading', () => {
    render(<PageHeader kicker="Fig. 03 — Outgoings and card timing" title="Expenses" />);

    expect(screen.getByRole('heading', { level: 1, name: 'Expenses' })).toBeInTheDocument();
  });

  it('carries the figure kicker above the title', () => {
    render(<PageHeader kicker="Fig. 03 — Outgoings and card timing" title="Expenses" />);

    expect(screen.getByText('Fig. 03 — Outgoings and card timing')).toBeInTheDocument();
  });

  it('holds the period controls a page gives it', () => {
    render(
      <PageHeader kicker="Fig. 01" title="Overview">
        <button type="button">Month</button>
      </PageHeader>,
    );

    expect(screen.getByRole('button', { name: 'Month' })).toBeInTheDocument();
  });

  it('renders without controls, for the pages that have none', () => {
    render(<PageHeader kicker="Fig. 09" title="Settings" />);

    expect(screen.getByRole('heading', { level: 1, name: 'Settings' })).toBeInTheDocument();
  });

  it('is a banner region, so the page title can be jumped to', () => {
    render(<PageHeader kicker="Fig. 01" title="Overview" />);

    expect(screen.getByRole('banner')).toBeInTheDocument();
  });
});
