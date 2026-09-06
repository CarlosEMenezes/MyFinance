import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { Field } from './Field';

describe('Field', () => {
  it('labels the control it wraps', () => {
    render(<Field label="Name">{({ id }) => <input id={id} />}</Field>);

    expect(screen.getByLabelText('Name')).toBeInTheDocument();
  });

  it('keeps the hint out of the accessible name', () => {
    render(
      <Field label="Name" hint="As it appears on your statement">
        {({ id, describedBy }) => <input id={id} aria-describedby={describedBy} />}
      </Field>,
    );

    // Gotcha 26: a hint nested in the label would make the announced name the
    // whole paragraph, and `getByLabelText('Name')` would find nothing.
    const input = screen.getByLabelText('Name');
    expect(input).toHaveAccessibleDescription('As it appears on your statement');
  });

  it('announces an error rather than only showing it', () => {
    render(
      <Field label="Closing day" error="Must be between 1 and 28">
        {({ id, describedBy }) => <input id={id} aria-describedby={describedBy} />}
      </Field>,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Must be between 1 and 28');
    expect(screen.getByLabelText('Closing day')).toHaveAccessibleDescription(
      'Must be between 1 and 28',
    );
  });

  it('describes the control with both the hint and the error', () => {
    render(
      <Field label="Closing day" hint="The day the statement closes" error="Must be 1 to 28">
        {({ id, describedBy }) => <input id={id} aria-describedby={describedBy} />}
      </Field>,
    );

    expect(screen.getByLabelText('Closing day')).toHaveAccessibleDescription(
      'The day the statement closes Must be 1 to 28',
    );
  });

  it('describes nothing when there is nothing to say', () => {
    render(
      <Field label="Name">{({ describedBy, id }) => <input id={id} data-d={describedBy} />}</Field>,
    );

    expect(screen.getByLabelText('Name')).not.toHaveAttribute('data-d');
  });
});
