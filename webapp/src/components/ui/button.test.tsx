import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import * as React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { Button } from './button';

describe('Button primitive', () => {
  it('renders a <button> by default', () => {
    render(<Button>Click me</Button>);
    const btn = screen.getByRole('button', { name: 'Click me' });
    expect(btn.tagName).toBe('BUTTON');
  });

  it('applies the default variant + size classes', () => {
    render(<Button>X</Button>);
    const btn = screen.getByRole('button');
    expect(btn.className).toMatch(/bg-primary/);
    expect(btn.className).toMatch(/h-9/);
    expect(btn.className).toMatch(/px-4/);
  });

  const variants = ['default', 'destructive', 'outline', 'secondary', 'ghost', 'link'] as const;
  for (const v of variants) {
    it(`renders variant="${v}" without error`, () => {
      render(<Button variant={v}>label-{v}</Button>);
      expect(screen.getByRole('button', { name: `label-${v}` })).toBeInTheDocument();
    });
  }

  const sizes = ['default', 'sm', 'lg', 'icon'] as const;
  for (const s of sizes) {
    it(`renders size="${s}" without error`, () => {
      render(<Button size={s}>label-{s}</Button>);
      expect(screen.getByRole('button', { name: `label-${s}` })).toBeInTheDocument();
    });
  }

  it('forwards ref to the underlying DOM node', () => {
    const ref = React.createRef<HTMLButtonElement>();
    render(<Button ref={ref}>Ref</Button>);
    expect(ref.current).toBeInstanceOf(HTMLButtonElement);
  });

  it('composes as child when asChild is set', () => {
    render(
      <Button asChild>
        <a href="https://example.com">link</a>
      </Button>,
    );
    const a = screen.getByRole('link', { name: 'link' });
    expect(a.tagName).toBe('A');
    expect(a).toHaveAttribute('href', 'https://example.com');
  });

  it('calls onClick when activated with mouse', async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();
    render(<Button onClick={onClick}>Go</Button>);
    await user.click(screen.getByRole('button', { name: 'Go' }));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('calls onClick when activated with Enter or Space from the keyboard', async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();
    render(<Button onClick={onClick}>Go</Button>);
    const btn = screen.getByRole('button', { name: 'Go' });
    btn.focus();
    await user.keyboard('{Enter}');
    expect(onClick).toHaveBeenCalledTimes(1);
    await user.keyboard(' ');
    expect(onClick).toHaveBeenCalledTimes(2);
  });

  it('disabled button does not fire onClick and is not focused by click', async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();
    render(
      <Button onClick={onClick} disabled>
        Go
      </Button>,
    );
    const btn = screen.getByRole('button', { name: 'Go' });
    await user.click(btn);
    expect(onClick).not.toHaveBeenCalled();
    expect(btn).toBeDisabled();
  });

  it('merges custom className with variant classes', () => {
    render(<Button className="custom-class">X</Button>);
    expect(screen.getByRole('button')).toHaveClass('custom-class');
  });
});
