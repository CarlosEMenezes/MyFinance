import { describe, expect, it } from 'vitest';

import { arrangeRows, type ArrangeableRow } from './planRows';

const rows: readonly ArrangeableRow[] = [
  {
    id: 'b',
    category: 'Tutoring',
    group: 'Self-employed',
    frequency: 'WEEKLY',
    planned: 22500,
    real: 12000,
    variance: -10500,
  },
  {
    id: 'a',
    category: 'Freelance design',
    group: 'Self-employed',
    frequency: 'MONTHLY',
    planned: 120000,
    real: 101000,
    variance: -19000,
  },
  {
    id: 'c',
    category: 'Part-time café',
    group: 'Employment',
    frequency: 'WEEKLY',
    planned: 64000,
    real: 67200,
    variance: 3200,
  },
];

const names = (result: readonly { readonly category: string }[]) => result.map((r) => r.category);

describe('arrangeRows - sorting (BR-15)', () => {
  it('sorts by category name', () => {
    expect(names(arrangeRows(rows, { groupBy: 'NONE', sortBy: 'CATEGORY' }))).toEqual([
      'Freelance design',
      'Part-time café',
      'Tutoring',
    ]);
  });

  it('sorts by planned, largest first', () => {
    expect(names(arrangeRows(rows, { groupBy: 'NONE', sortBy: 'PLANNED' }))[0]).toBe(
      'Freelance design',
    );
  });

  it('sorts by real, largest first', () => {
    expect(names(arrangeRows(rows, { groupBy: 'NONE', sortBy: 'REAL' }))[0]).toBe(
      'Freelance design',
    );
  });

  it('sorts by the size of the variance, not its sign', () => {
    // -190.00 is a bigger miss than +32.00, so it leads whichever way it points.
    expect(names(arrangeRows(rows, { groupBy: 'NONE', sortBy: 'VARIANCE' }))[0]).toBe(
      'Freelance design',
    );
  });

  it('does not mutate the rows it was given', () => {
    const before = names(rows);
    arrangeRows(rows, { groupBy: 'NONE', sortBy: 'PLANNED' });
    expect(names(rows)).toEqual(before);
  });
});

describe('arrangeRows - grouping (BR-15)', () => {
  it('adds no headings when ungrouped', () => {
    const result = arrangeRows(rows, { groupBy: 'NONE', sortBy: 'CATEGORY' });
    expect(result.every((row) => row.groupHeading === undefined)).toBe(true);
  });

  it('heads the first row of each group and no other', () => {
    const result = arrangeRows(rows, { groupBy: 'GROUP', sortBy: 'CATEGORY' });
    const headings = result.map((row) => row.groupHeading);

    // Three rows in two groups: a heading on the first of each, none on the
    // second Self-employed row.
    expect(headings).toEqual(['Employment', 'Self-employed', undefined]);
  });

  it('keeps rows together by group, sorted within it', () => {
    const result = arrangeRows(rows, { groupBy: 'GROUP', sortBy: 'CATEGORY' });

    expect(names(result)).toEqual(['Part-time café', 'Freelance design', 'Tutoring']);
  });

  it('groups by frequency when asked', () => {
    const result = arrangeRows(rows, { groupBy: 'FREQUENCY', sortBy: 'CATEGORY' });

    expect(result[0]?.groupHeading).toBe('Monthly');
    expect(names(result)[0]).toBe('Freelance design');
  });

  it('groups by payment method when asked', () => {
    const withMethods: readonly ArrangeableRow[] = [
      {
        id: 'x',
        category: 'Transport',
        group: 'Variable',
        frequency: 'WEEKLY',
        planned: 10500,
        real: 7420,
        variance: -3080,
        paidWith: 'Wallet',
      },
      {
        id: 'y',
        category: 'Rent',
        group: 'Fixed',
        frequency: 'MONTHLY',
        planned: 78000,
        real: 78000,
        variance: 0,
        paidWith: 'Revolut Current',
      },
    ];
    const result = arrangeRows(withMethods, { groupBy: 'ACCOUNT', sortBy: 'CATEGORY' });

    expect(result[0]?.groupHeading).toBe('Revolut Current');
  });
});
