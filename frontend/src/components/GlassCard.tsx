import React from 'react';
import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

interface GlassCardProps extends React.HTMLAttributes<HTMLDivElement> {
  noPadding?: boolean;
}

export const GlassCard: React.FC<GlassCardProps> = ({
  className,
  noPadding = false,
  children,
  ...props
}) => {
  return (
    <div
      className={cn(
        'glass rounded-2xl stripe-top transition-all duration-200',
        !noPadding && 'p-6',
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
};
