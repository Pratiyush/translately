import { describe, expect, it } from 'vitest';
import { cn } from './utils';

describe('cn()', () => {
  it('joins string args with a space', () => {
    expect(cn('a', 'b', 'c')).toBe('a b c');
  });

  it('ignores falsy values (null, undefined, false, 0, empty)', () => {
    expect(cn('a', null, undefined, false, 0, '', 'b')).toBe('a b');
  });

  it('flattens arrays', () => {
    expect(cn(['a', ['b', ['c']]])).toBe('a b c');
  });

  it('handles conditional objects', () => {
    expect(cn('base', { active: true, disabled: false })).toBe('base active');
  });

  it('deduplicates conflicting Tailwind classes — latest wins', () => {
    expect(cn('p-2', 'p-4')).toBe('p-4');
    expect(cn('bg-red-500', 'bg-blue-500')).toBe('bg-blue-500');
  });

  it('preserves distinct-namespace classes', () => {
    expect(cn('p-2', 'm-2')).toBe('p-2 m-2');
  });

  it('merges responsive variants of the same property', () => {
    expect(cn('p-2 md:p-6', 'md:p-8')).toBe('p-2 md:p-8');
  });
});
