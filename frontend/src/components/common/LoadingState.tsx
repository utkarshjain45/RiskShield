import React from 'react';
import { Loader2 } from 'lucide-react';

interface LoadingStateProps {
  message?: string;
  minHeight?: string;
}

export const LoadingState: React.FC<LoadingStateProps> = ({
  message = 'Loading live telemetry...',
  minHeight = '240px',
}) => {
  return (
    <div
      className="flex flex-col items-center justify-center gap-3 text-text-muted select-none"
      style={{ minHeight }}
    >
      <Loader2 className="w-6 h-6 text-brand animate-spin" />
      <span className="text-xs font-medium text-text-secondary">{message}</span>
    </div>
  );
};
