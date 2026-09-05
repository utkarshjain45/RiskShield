import React from 'react';
import { RiskDecision, IncidentSeverity, IncidentStatus } from '../../types';

interface StatusBadgeProps {
  status?: RiskDecision | IncidentSeverity | IncidentStatus | string | null;
  size?: 'sm' | 'md';
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status, size = 'md' }) => {
  if (!status) {
    return (
      <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-[11px] font-mono text-text-muted bg-surface-muted border border-border-subtle">
        UNKNOWN
      </span>
    );
  }

  const s = status.toUpperCase();
  const textClasses = size === 'sm' ? 'text-[10px] px-1.5 py-0.5' : 'text-[11px] px-2 py-0.5';

  switch (s) {
    case 'ALLOW':
    case 'NORMAL':
    case 'RESOLVED':
    case 'SAFE':
      return (
        <span
          className={`inline-flex items-center gap-1.5 rounded font-mono font-medium bg-success-light text-success border border-success-border leading-none ${textClasses}`}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-success flex-shrink-0" />
          {s}
        </span>
      );

    case 'REVIEW':
    case 'ELEVATED':
    case 'ACKNOWLEDGED':
    case 'MEDIUM':
      return (
        <span
          className={`inline-flex items-center gap-1.5 rounded font-mono font-medium bg-warning-light text-warning border border-warning-border leading-none ${textClasses}`}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-warning flex-shrink-0" />
          {s}
        </span>
      );

    case 'BLOCK':
    case 'BLOCKED':
    case 'CRITICAL':
    case 'OPEN':
    case 'FAILED':
      return (
        <span
          className={`inline-flex items-center gap-1.5 rounded font-mono font-medium bg-danger-light text-danger border border-danger-border leading-none ${textClasses}`}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-danger flex-shrink-0" />
          {s}
        </span>
      );

    default:
      return (
        <span
          className={`inline-flex items-center gap-1.5 rounded font-mono font-medium bg-surface-muted text-text-secondary border border-border-subtle leading-none ${textClasses}`}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-text-muted flex-shrink-0" />
          {s}
        </span>
      );
  }
};

/**
 * Reusable Numerical Risk Score Indicator
 * e.g. "● 94 High", "● 72 Elevated", "● 18 Low"
 */
interface RiskScoreIndicatorProps {
  score?: number | null;
  size?: 'sm' | 'md' | 'lg';
  showLabel?: boolean;
}

export const RiskScoreIndicator: React.FC<RiskScoreIndicatorProps> = ({
  score = 0,
  size = 'md',
  showLabel = true,
}) => {
  const num = typeof score === 'number' ? Math.round(score) : 0;

  let tier = 'LOW';
  let dotColor = 'bg-success';
  let textColor = 'text-success';
  let bgColor = 'bg-success-light';
  let borderColor = 'border-success-border';

  if (num >= 85) {
    tier = 'CRITICAL';
    dotColor = 'bg-danger';
    textColor = 'text-danger';
    bgColor = 'bg-danger-light';
    borderColor = 'border-danger-border';
  } else if (num >= 65) {
    tier = 'HIGH';
    dotColor = 'bg-danger';
    textColor = 'text-danger';
    bgColor = 'bg-danger-light';
    borderColor = 'border-danger-border';
  } else if (num >= 35) {
    tier = 'ELEVATED';
    dotColor = 'bg-warning';
    textColor = 'text-warning';
    bgColor = 'bg-warning-light';
    borderColor = 'border-warning-border';
  }

  const sizeClasses =
    size === 'sm'
      ? 'text-[11px] px-1.5 py-0.5'
      : size === 'lg'
      ? 'text-sm px-2.5 py-1'
      : 'text-xs px-2 py-0.5';

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded font-mono font-semibold ${bgColor} ${textColor} border ${borderColor} leading-none ${sizeClasses}`}
    >
      <span className={`w-1.5 h-1.5 rounded-full ${dotColor} flex-shrink-0`} />
      <span>{num}</span>
      {showLabel && <span className="font-normal text-[10px] opacity-90 uppercase">{tier}</span>}
    </span>
  );
};
