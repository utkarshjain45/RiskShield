import React from 'react';

interface StatCardProps {
  title: string;
  value: string | number;
  subtext?: string;
  icon?: React.ReactNode;
  trend?: {
    value: string;
    isPositive?: boolean;
    isNeutral?: boolean;
  };
  accentColor?: string;
  isStripItem?: boolean;
}

export const StatCard: React.FC<StatCardProps> = ({
  title,
  value,
  subtext,
  icon,
  trend,
  accentColor,
  isStripItem = false,
}) => {
  if (isStripItem) {
    return (
      <div className="flex flex-col py-3 px-4">
        <div className="flex items-center justify-between gap-2 mb-1">
          <span className="text-[11px] font-medium text-text-muted uppercase tracking-wider">
            {title}
          </span>
          {icon && <div className="text-text-muted">{icon}</div>}
        </div>
        <div className="text-2xl font-semibold text-text-primary font-mono tracking-tight">
          {value}
        </div>
        {(subtext || trend) && (
          <div className="flex items-center gap-2 mt-1 text-xs text-text-muted">
            {trend && (
              <span
                className={`font-mono text-[10px] font-medium px-1.5 py-0.2 rounded leading-none ${
                  trend.isNeutral
                    ? 'bg-surface-muted text-text-secondary'
                    : trend.isPositive
                    ? 'bg-success-light text-success'
                    : 'bg-danger-light text-danger'
                }`}
              >
                {trend.value}
              </span>
            )}
            {subtext && <span className="text-[11px] truncate">{subtext}</span>}
          </div>
        )}
      </div>
    );
  }

  return (
    <div
      className="bg-surface rounded-lg border border-border-subtle p-4 flex flex-col justify-between transition-all duration-150 hover:border-border-medium shadow-subtle relative"
      style={accentColor ? { borderLeft: `3px solid ${accentColor}` } : undefined}
    >
      <div className="flex items-center justify-between gap-2 mb-2">
        <span className="text-[11px] font-medium text-text-muted uppercase tracking-wider">
          {title}
        </span>
        {icon && <div className="text-text-muted">{icon}</div>}
      </div>

      <div className="text-2xl font-semibold text-text-primary font-mono tracking-tight mb-1">
        {value}
      </div>

      {(subtext || trend) && (
        <div className="flex items-center gap-2 text-xs text-text-muted pt-1">
          {trend && (
            <span
              className={`font-mono text-[10px] font-medium px-1.5 py-0.5 rounded leading-none ${
                trend.isNeutral
                  ? 'bg-surface-muted text-text-secondary'
                  : trend.isPositive
                  ? 'bg-success-light text-success'
                  : 'bg-danger-light text-danger'
              }`}
            >
              {trend.value}
            </span>
          )}
          {subtext && <span className="text-[11px] truncate">{subtext}</span>}
        </div>
      )}
    </div>
  );
};
