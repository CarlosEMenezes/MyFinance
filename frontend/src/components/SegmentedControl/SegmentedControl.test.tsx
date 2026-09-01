import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { SegmentedControl } from './SegmentedControl';

const periods = [
  { value: 'DAY', label: 'Day' },
  { value: 'WEEK', label: 'Week' },
  { value: 'MONTH', label: 'Month' },
] as const;

describe('SegmentedControl', () => {
  it('is a labelled radio group', () => {
    render(
      <SegmentedControl
        name="period"
        label="Period"
        options={periods}
        value="MONTH"
        onChange={vi.fn()}
      />,
    );

    expect(screen.getByRole('radiogroup', { name: 'Period' })).toBeInTheDocument();
  });

  it('offers every option as a radio', () => {
    render(
      <SegmentedControl
        name="period"
        label="Period"
        options={periods}
        value="MONTH"
        onChange={vi.fn()}
      />,
    );

    expect(screen.getAllByRole('radio')).toHaveLength(3);
    expect(screen.getByRole('radio', { name: 'Week' })).toBeInTheDocument();
  });

  it('checks the current value and only that one', () => {
    render(
      <SegmentedControl
        name="period"
        label="Period"
        options={periods}
        value="MONTH"
        onChange={vi.fn()}
      />,
    );

    expect(screen.getByRole('radio', { name: 'Month' })).toBeChecked();
    expect(screen.getByRole('radio', { name: 'Day' })).not.toBeChecked();
  });

  it('reports the value that was picked', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(
      <SegmentedControl
        name="period"
        label="Period"
        options={periods}
        value="MONTH"
        onChange={onChange}
      />,
    );

    await user.click(screen.getByRole('radio', { name: 'Week' }));

    expect(onChange).toHaveBeenCalledWith('WEEK');
  });

  it('moves between options with the arrow keys, as a radio group should', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(
      <SegmentedControl
        name="period"
        label="Period"
        options={periods}
        value="MONTH"
        onChange={onChange}
      />,
    );

    await user.click(screen.getByRole('radio', { name: 'Month' }));
    await user.keyboard('{ArrowRight}');

    expect(onChange).toHaveBeenCalled();
  });

  it('shows its label, because the design puts it beside the control', () => {
    render(
      <SegmentedControl
        name="group"
        label="Group"
        options={periods}
        value="DAY"
        onChange={vi.fn()}
      />,
    );

    expect(screen.getByText('Group')).toBeVisible();
  });

  it('can hide the label from view while keeping it for assistive technology', () => {
    render(
      <SegmentedControl
        name="period"
        label="Period"
        hideLabel
        options={periods}
        value="DAY"
        onChange={vi.fn()}
      />,
    );

    expect(screen.getByRole('radiogroup', { name: 'Period' })).toBeInTheDocument();
    expect(screen.getByText('Period').className).toMatch(/hidden/i);
  });

  it('keeps two controls on one page independent', () => {
    render(
      <>
        <SegmentedControl
          name="group"
          label="Group"
          options={periods}
          value="DAY"
          onChange={vi.fn()}
        />
        <SegmentedControl
          name="sort"
          label="Sort"
          options={periods}
          value="WEEK"
          onChange={vi.fn()}
        />
      </>,
    );

    expect(screen.getByRole('radiogroup', { name: 'Group' })).toBeInTheDocument();
    expect(screen.getByRole('radiogroup', { name: 'Sort' })).toBeInTheDocument();
  });
});
