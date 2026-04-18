import * as React from 'react';
import { cn } from '@/lib/utils';

/**
 * Form label. Uses design-system tokens so colour contrast holds in both
 * themes. Pair every input with one; the `htmlFor` attribute is the
 * accessible association.
 */
export const Label = React.forwardRef<HTMLLabelElement, React.LabelHTMLAttributes<HTMLLabelElement>>(
  ({ className, ...props }, ref) => (
    <label
      ref={ref}
      className={cn(
        'text-sm font-medium leading-none text-foreground',
        'peer-disabled:cursor-not-allowed peer-disabled:opacity-70',
        className,
      )}
      {...props}
    />
  ),
);
Label.displayName = 'Label';
