import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import App from './App';

describe('App shell', () => {
  it('renders the brand in both the sidebar and the mobile top bar', () => {
    render(<App />);

    // One in .side (desktop), one in .mtop (below 940px). CSS decides which is
    // visible; both are always in the tree so the breakpoint needs no JS.
    expect(screen.getAllByText('BUDGET TRACKER')).toHaveLength(2);
  });

  it('renders the page heading as a level-one heading', () => {
    render(<App />);

    expect(screen.getByRole('heading', { level: 1, name: 'Budget Tracker' })).toBeInTheDocument();
  });
});
