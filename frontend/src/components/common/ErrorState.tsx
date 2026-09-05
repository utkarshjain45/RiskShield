import React from 'react';
import { AlertCircle, RefreshCw } from 'lucide-react';

interface ErrorStateProps {
  message?: string;
  onRetry?: () => void;
  minHeight?: string;
}

export const ErrorState: React.FC<ErrorStateProps> = ({
  message = 'Failed to load telemetry data from server.',
  onRetry,
  minHeight = '240px',
}) => {
  return (
    <div
      className="flex flex-col items-center justify-center p-6 text-center gap-3"
      style={{ minHeight }}
    >
      <div className="w-10 h-10 rounded-full bg-danger-light text-danger flex items-center justify-center">
        <AlertCircle className="w-5 h-5" />
      </div>
      <div>
        <h4 className="text-sm font-semibold text-text-primary mb-1">
          Network Connection Error
        </h4>
        <p className="text-xs text-text-muted max-w-sm leading-relaxed">{message}</p>
      </div>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium bg-white border border-border-subtle text-text-secondary hover:text-text-primary hover:bg-surface-subtle shadow-subtle transition-colors mt-1"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          <span>Retry Request</span>
        </button>
      )}
    </div>
  );
};
