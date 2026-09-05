import React from 'react';
import { ContributingFeature } from '../../types';
import { ArrowUpRight, ArrowDownRight } from 'lucide-react';

interface ShapAttributionListProps {
  features?: ContributingFeature[];
}

export const ShapAttributionList: React.FC<ShapAttributionListProps> = ({ features = [] }) => {
  if (!features || features.length === 0) {
    return (
      <div className="text-text-muted text-xs p-3 text-center bg-surface-subtle rounded-md border border-border-subtle">
        No significant risk signals flagged by the feature attribution engine.
      </div>
    );
  }

  const maxAbsImpact = Math.max(...features.map((f) => Math.abs(f.impact || 0)), 0.01);

  return (
    <div className="flex flex-col gap-2.5">
      {features.map((feature, idx) => {
        const isRiskIncrease = feature.direction === 'INCREASES_RISK' || feature.impact > 0;
        const barWidth = Math.min(100, Math.max(6, (Math.abs(feature.impact) / maxAbsImpact) * 100));

        return (
          <div
            key={idx}
            className="p-3 rounded-lg border border-border-subtle bg-surface hover:border-border-medium transition-colors"
          >
            <div className="flex items-center justify-between mb-1">
              <div className="flex items-center gap-1.5 min-w-0">
                {isRiskIncrease ? (
                  <ArrowUpRight className="w-3.5 h-3.5 text-danger flex-shrink-0" />
                ) : (
                  <ArrowDownRight className="w-3.5 h-3.5 text-success flex-shrink-0" />
                )}
                <span className="font-medium text-xs text-text-primary truncate">
                  {feature.display_name || feature.feature_name}
                </span>
              </div>
              <span
                className={`font-mono text-xs font-semibold px-1.5 py-0.5 rounded leading-none ${
                  isRiskIncrease
                    ? 'bg-danger-light text-danger border border-danger-border'
                    : 'bg-success-light text-success border border-success-border'
                }`}
              >
                {feature.formatted_impact ||
                  (feature.impact >= 0 ? `+${feature.impact.toFixed(2)}` : feature.impact.toFixed(2))}
              </span>
            </div>

            {feature.description && (
              <p className="text-[11px] text-text-muted mb-2 leading-tight">
                {feature.description}
              </p>
            )}

            {/* Clean Thin Contribution Bar */}
            <div className="h-1.5 w-full bg-surface-muted rounded-full overflow-hidden">
              <div
                className={`h-full rounded-full transition-all duration-300 ${
                  isRiskIncrease ? 'bg-danger' : 'bg-success'
                }`}
                style={{ width: `${barWidth}%` }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
};
