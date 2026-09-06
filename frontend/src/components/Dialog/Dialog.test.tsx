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

describe('Dialog - the box itself', () => {
  it('does not take the dialog class from the design system', () => {
    open();

    // Regression: `.card, .dialog { background: transparent }` in tokens.css
    // has the same specificity as this module's opaque rule, so stylesheet
    // order decided the winner and the modal rendered see-through with the
    // page readable behind it. The frame comes from `.blueprint`; everything
    // else is the module's (gotcha 19).
    const dialog = screen.getByRole('dialog');
    expect(dialog.classList.contains('dialog')).toBe(false);
    expect(dialog.classList.contains('blueprint')).toBe(true);
  });

  it('scrolls its body, not the whole box', () => {
    open();

    // The title and the actions must stay put as the content grows, and the
    // blueprint corner marks are drawn outside the box, so a scroll container
    // on the box itself would clip them.
    const dialog = screen.getByRole('dialog');
    const title = screen.getByRole('heading', { name: 'Log entry' });
    const body = screen.getByLabelText('Amount').closest('div');

    expect(title.parentElement).toBe(dialog);
    expect(body?.className).toContain('body');
  });

  it('keeps the actions outside the scrolling region', () => {
    open({ actions: <button type="button">Save entry</button> });

    const save = screen.getByRole('button', { name: 'Save entry' });
    expect(save.parentElement?.className).toContain('actions');
    expect(save.parentElement?.parentElement).toBe(screen.getByRole('dialog'));
  });
});

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
