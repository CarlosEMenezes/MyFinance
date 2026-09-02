import { useCallback, useEffect, useId, useRef } from 'react';
import { createPortal } from 'react-dom';

import styles from './Dialog.module.css';
import type { DialogProps } from './Dialog.types';

/**
 * A modal dialog.
 *
 * The prototype is a styled backdrop and box, which looks right and behaves
 * badly: Tab walks out into the page behind, Escape does nothing, and focus is
 * left wherever it was when the dialog closes. Those are not polish - a modal
 * that leaks focus is unusable by keyboard.
 *
 * So the four obligations are met explicitly: focus moves in on open, Tab is
 * trapped, Escape closes, and focus returns to whatever opened it. It is a
 * portal so a dialog opened from inside a panel is not clipped by it.
 */

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

export function Dialog({ open, title, onClose, children, actions }: DialogProps) {
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const openerRef = useRef<Element | null>(null);

  // Captured before focus moves in, restored after the dialog is gone, so the
  // keyboard lands back where the user left off rather than at the top of the
  // document.
  useEffect(() => {
    if (!open) {
      return;
    }
    openerRef.current = document.activeElement;
    dialogRef.current?.focus();

    return () => {
      if (openerRef.current instanceof HTMLElement) {
        openerRef.current.focus();
      }
    };
  }, [open]);

  const handleKeyDown = useCallback(
    (event: React.KeyboardEvent<HTMLDivElement>) => {
      if (event.key === 'Escape') {
        onClose();
        return;
      }
      if (event.key !== 'Tab') {
        return;
      }

      const focusable = dialogRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE);
      if (focusable === undefined || focusable.length === 0) {
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (first === undefined || last === undefined) {
        return;
      }

      // Only the two edges need handling; the browser walks the middle.
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    },
    [onClose],
  );

  if (!open) {
    return null;
  }

  return createPortal(
    <div
      className={styles.backdrop}
      data-testid="backdrop"
      onClick={onClose}
      onKeyDown={handleKeyDown}
      role="presentation"
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={['dialog', 'blueprint', styles.dialog].join(' ')}
        onClick={(event) => {
          // The backdrop closes on click; the dialog is inside it, so a click
          // on the dialog must not travel up and dismiss what it landed on.
          event.stopPropagation();
        }}
      >
        <i className="corner tl" aria-hidden="true" />
        <i className="corner tr" aria-hidden="true" />
        <i className="corner bl" aria-hidden="true" />
        <i className="corner br" aria-hidden="true" />

        <h2 id={titleId} className={styles.title}>
          {title}
        </h2>

        {children}

        {actions !== undefined && <div className={styles.actions}>{actions}</div>}
      </div>
    </div>,
    document.body,
  );
}
