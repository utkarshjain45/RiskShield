import React from 'react';

interface RiskScoreGaugeProps {
  score?: number | null;
  showLabel?: boolean;
  size?: 'sm' | 'md' | 'lg';
}

export const RiskScoreGauge: React.FC<RiskScoreGaugeProps> = ({
  score,
  showLabel = true,
  size = 'md',
}) => {
  if (score === null || score === undefined) {
    return <span style={{ color: 'var(--text-dim)', fontSize: '0.82rem' }}>Pending</span>;
  }

  const s = Math.min(100, Math.max(0, score));

  // Color gradient logic:
  // 0 - 30: Green
  // 31 - 70: Amber
  // 71 - 89: Orange
  // 90 - 100: Crimson
  let color = 'var(--allow)';
  let bgFill = 'linear-gradient(90deg, #10b981 0%, #34d399 100%)';
  let tier = 'LOW';

  if (s >= 90) {
    color = 'var(--block)';
    bgFill = 'linear-gradient(90deg, #ef4444 0%, #dc2626 100%)';
    tier = 'CRITICAL';
  } else if (s >= 71) {
    color = '#f97316';
    bgFill = 'linear-gradient(90deg, #f59e0b 0%, #ea580c 100%)';
    tier = 'HIGH';
  } else if (s >= 31) {
    color = 'var(--review)';
    bgFill = 'linear-gradient(90deg, #f59e0b 0%, #fbbf24 100%)';
    tier = 'MEDIUM';
  }

  const height = size === 'sm' ? 6 : size === 'lg' ? 12 : 8;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem', minWidth: '110px' }}>
      {showLabel && (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, fontSize: size === 'lg' ? '1.25rem' : '0.88rem', color }}>
            {s.toFixed(1)}
          </span>
          <span style={{ fontSize: '0.72rem', color: 'var(--text-dim)', fontWeight: 600 }}>{tier}</span>
        </div>
      )}
      <div className="risk-meter" style={{ height }}>
        <div
          className="risk-meter-fill"
          style={{
            width: `${s}%`,
            background: bgFill,
          }}
        />
      </div>
    </div>
  );
};
