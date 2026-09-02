import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { Dialog } from './Dialog';

const open = (overrides = {}) =>
  render(
    <Dialog open title="Log entry" onClose={vi.fn()} {...overrides}>
      <label>
        Amount
        <input />
      </label>
    </Dialog>,
  );

describe('Dialog - presence', () => {
  it('renders nothing while closed', () => {
    render(
      <Dialog open={false} title="Log entry" onClose={vi.fn()}>
        content
      </Dialog>,
    );

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('is a modal dialog named by its title', () => {
    open();

    expect(screen.getByRole('dialog', { name: 'Log entry' })).toHaveAttribute('aria-modal', 'true');
  });

  it('renders its content and its actions', () => {
    open({ actions: <button type="button">Save entry</button> });

    expect(screen.getByLabelText('Amount')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save entry' })).toBeInTheDocument();
  });

  it('wears the blueprint frame and its registration marks', () => {
    open();
    const dialog = screen.getByRole('dialog');

    expect(dialog).toHaveClass('blueprint');
    expect(dialog.querySelectorAll('.corner')).toHaveLength(4);
  });
});

describe('Dialog - dismissing', () => {
  it('closes on Escape', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    open({ onClose });

    await user.keyboard('{Escape}');

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('closes when the backdrop is clicked', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    open({ onClose });

    await user.click(screen.getByTestId('backdrop'));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('stays open when the dialog itself is clicked', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    open({ onClose });

    await user.click(screen.getByRole('dialog'));

    expect(onClose).not.toHaveBeenCalled();
  });
});

describe('Dialog - focus', () => {
  it('moves focus into the dialog when it opens', () => {
    open();

    expect(screen.getByRole('dialog')).toHaveFocus();
  });

  it('keeps Tab inside the dialog, wrapping from the last control to the first', async () => {
    const user = userEvent.setup();
    open({ actions: <button type="button">Save entry</button> });

    await user.tab();
    expect(screen.getByLabelText('Amount')).toHaveFocus();

    await user.tab();
    expect(screen.getByRole('button', { name: 'Save entry' })).toHaveFocus();

    await user.tab();
    expect(screen.getByLabelText('Amount')).toHaveFocus();
  });

  it('wraps backwards from the first control to the last', async () => {
    const user = userEvent.setup();
    open({ actions: <button type="button">Save entry</button> });

    await user.tab();
    expect(screen.getByLabelText('Amount')).toHaveFocus();

    await user.tab({ shift: true });
    expect(screen.getByRole('button', { name: 'Save entry' })).toHaveFocus();
  });

  it('does nothing on Tab when there is nothing focusable to trap', async () => {
    const user = userEvent.setup();
    render(
      <Dialog open title="Rate unavailable" onClose={vi.fn()}>
        The exchange rate could not be fetched.
      </Dialog>,
    );

    await user.tab();

    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('gives focus back to whatever opened it', async () => {
    const user = userEvent.setup();

    function Harness() {
      const [isOpen, setOpen] = useState(false);
      return (
        <>
          <button
            type="button"
            onClick={() => {
              setOpen(true);
            }}
          >
            Log entry
          </button>
          <Dialog
            open={isOpen}
            title="Log entry"
            onClose={() => {
              setOpen(false);
            }}
          >
            <input aria-label="Amount" />
          </Dialog>
        </>
      );
    }

    render(<Harness />);
    const trigger = screen.getByRole('button', { name: 'Log entry' });
    await user.click(trigger);
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    await user.keyboard('{Escape}');

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });
});
