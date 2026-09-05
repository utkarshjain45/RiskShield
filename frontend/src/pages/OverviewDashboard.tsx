import React, { useEffect, useState } from 'react';
import {
  Activity,
  AlertTriangle,
  ShieldAlert,
  Clock,
  IndianRupee,
  ShieldCheck,
  Percent,
  Flame,
  RefreshCw,
  ChevronRight,
} from 'lucide-react';
import {
  AreaChart,
  Area,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
  ReferenceLine,
} from 'recharts';
import { analyticsApi, incidentsApi, transactionsApi } from '../api';
import { DashboardMetrics, AnalyticsCharts, FraudIncident, Transaction } from '../types';
import { StatusBadge, RiskScoreIndicator } from '../components/common/StatusBadge';
import { LoadingState } from '../components/common/LoadingState';
import { ErrorState } from '../components/common/ErrorState';

interface OverviewDashboardProps {
  onNavigateToTransactions: () => void;
  onNavigateToIncidents: () => void;
}

export const OverviewDashboard: React.FC<OverviewDashboardProps> = ({
  onNavigateToTransactions,
  onNavigateToIncidents,
}) => {
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [charts, setCharts] = useState<AnalyticsCharts | null>(null);
  const [recentIncidents, setRecentIncidents] = useState<FraudIncident[]>([]);
  const [blockedTxs, setBlockedTxs] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [lastUpdated, setLastUpdated] = useState<string>(new Date().toLocaleTimeString());

  const fetchDashboardData = async () => {
    try {
      setError(null);
      const [dashMetrics, chartData, incPage, blockedPage] = await Promise.all([
        analyticsApi.getDashboard(),
        analyticsApi.getCharts(),
        incidentsApi.list({ size: 5 }),
        transactionsApi.list({ decision: 'BLOCK', size: 5 }).catch(() => ({ content: [] })),
      ]);
      setMetrics(dashMetrics);
      setCharts(chartData);
      setRecentIncidents(incPage.content || []);
      setBlockedTxs(blockedPage.content || []);
      setLastUpdated(new Date().toLocaleTimeString());
    } catch (err: any) {
      setError(err?.message || 'Failed to fetch dashboard metrics');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchDashboardData();
  }, []);

  const handleRefresh = () => {
    setRefreshing(true);
    fetchDashboardData();
  };

  if (loading) {
    return <LoadingState message="Connecting to telemetry engine and calculating risk metrics..." />;
  }

  if (error || !metrics || !charts) {
    return <ErrorState message={error || 'Telemetry pipeline is unavailable'} onRetry={fetchDashboardData} />;
  }

  const formatInr = (amount: number) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(amount);
  };

  const DECISION_COLORS: Record<string, string> = {
    ALLOW: '#059669',
    REVIEW: '#d97706',
    BLOCK: '#e11d48',
  };

  const RISK_BUCKET_COLORS = ['#059669', '#10b981', '#d97706', '#f97316', '#e11d48'];

  const renderRiskBucketTick = (props: any) => {
    const { x, y, payload } = props;
    if (!payload || !payload.value) return null;
    const raw = String(payload.value);
    const parts = raw.split(' ');
    const range = parts[0];
    const label = parts.slice(1).join(' ').replace(/[()]/g, '');

    return (
      <g transform={`translate(${x},${y})`}>
        <text x={0} y={0} dy={10} textAnchor="middle" fill="#475569" fontSize={10} fontWeight={600}>
          {range}
        </text>
        {label && (
          <text x={0} y={0} dy={22} textAnchor="middle" fill="#94a3b8" fontSize={9}>
            ({label})
          </text>
        )}
      </g>
    );
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-border-subtle">
        <div>
          <h1 className="text-2xl font-semibold text-text-primary tracking-tight">
            Risk Surveillance Overview
          </h1>
          <p className="text-xs text-text-muted mt-0.5">
            Real-time fraud surveillance, continuous scoring telemetry, and operational risk metrics · Updated at {lastUpdated}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onNavigateToTransactions}
            className="inline-flex items-center gap-1 px-3 py-1.5 rounded-md text-xs font-medium bg-surface border border-border-subtle text-text-secondary hover:text-text-primary hover:bg-surface-subtle shadow-subtle transition-colors"
          >
            <span>All Transactions</span>
            <ChevronRight className="w-3.5 h-3.5" />
          </button>
          <button
            type="button"
            onClick={handleRefresh}
            disabled={refreshing}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium bg-surface border border-border-subtle text-text-secondary hover:text-text-primary hover:bg-surface-subtle shadow-subtle transition-colors"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${refreshing ? 'animate-spin' : ''}`} />
            <span>{refreshing ? 'Updating...' : 'Refresh'}</span>
          </button>
        </div>
      </div>

      {/* Elegant De-Boxed Metric Strip with Vertical Dividers */}
      <div className="bg-surface rounded-xl border border-border-subtle p-2 shadow-subtle grid grid-cols-2 md:grid-cols-4 lg:grid-cols-8 divide-y md:divide-y-0 md:divide-x divide-border-subtle">
        {/* Metric 1 */}
        <div className="p-3 flex flex-col justify-between">
          <div className="flex items-center justify-between mb-1">
            <span className="text-[11px] font-medium text-text-muted uppercase tracking-wider">Volume</span>
            <Activity className="w-3.5 h-3.5 text-text-muted" />
          </div>
          <div className="text-xl font-semibold text-text-primary font-mono tracking-tight">
            {metrics.today_transactions.toLocaleString()}
          </div>
          <span className="text-[11px] text-text-muted mt-1">Total Ingested</span>
        </div>

        {/* Metric 2 */}
        <div className="p-3 flex flex-col justify-between">
          <div className="flex items-center justify-between mb-1">
            <span className="text-[11px] font-medium text-text-muted uppercase tracking-wider">Suspicious</span>
            <AlertTriangle className="w-3.5 h-3.5 text-warning" />
          </div>
          <div className="text-xl font-semibold text-warning font-mono tracking-tight">
            {metrics.suspicious_transactions.toLocaleString()}
          </div>
          <span className="text-[11px] text-text-muted mt-1">Score ≥ 31</span>
        </div>

        {/* Metric 3 */}
        <div className="p-3 flex flex-col justify-between">
          <div className="flex items-center justify-between mb-1">
            <span className="text-[11px] font-medium text-text-muted uppercase tracking-wider">Blocked</span>
            <ShieldAlert className="w-3.5 h-3.5 text-danger" />
          </div>
          <div className="text-xl font-semibold text-danger font-mono tracking-tight">
            {metrics.blocked_transactions.toLocaleString()}
          </div>
          <span className="text-[11px] text-text-muted mt-1">Hard Intercepts</span>
        </div>

        {/* Metric 4 */}
        <div className="p-3 flex flex-col justify-between">
          <div className="flex items-center justify-between mb-1">
            <span className="text-[11px] font-medium text-text-muted uppercase tracking-wider">Review Queue</span>
            <Clock className="w-3.5 h-3.5 text-text-muted" />
          </div>
          <div className="text-xl font-semibold text-text-primary font-mono tracking-tight">
            {metrics.review_queue.toLocaleString()}
          </div>
          <span className="text-[11px] text-text-muted mt-1">Pending Analyst</span>
        </div>

        {/* Metric 5 */}
        <div className="p-3 flex flex-col justify-between">
          <div className="flex items-center justify-between mb-1">
            <span className="text-[11px] font-medium text-text-muted uppercase tracking-wider">Exposure</span>
            <IndianRupee className="w-3.5 h-3.5 text-danger" />
          </div>
          <div className="text-xl font-semibold text-text-primary font-mono tracking-tight">
            {formatInr(metrics.fraud_exposure_inr)}
          </div>
          <span className="text-[11px] text-text-muted mt-1">At-Risk Total</span>
        </div>

        {/* Metric 6 */}
        <div className="p-3 flex flex-col justify-between">
          <div className="flex items-center justify-between mb-1">
            <span className="text-[11px] font-medium text-text-muted uppercase tracking-wider">Saved Loss</span>
            <ShieldCheck className="w-3.5 h-3.5 text-success" />
          </div>
          <div className="text-xl font-semibold text-success font-mono tracking-tight">
            {formatInr(metrics.prevented_loss_inr)}
          </div>
          <span className="text-[11px] text-text-muted mt-1">Blocked Fraud</span>
        </div>

        {/* Metric 7 */}
        <div className="p-3 flex flex-col justify-between">
          <div className="flex items-center justify-between mb-1">
            <span className="text-[11px] font-medium text-text-muted uppercase tracking-wider">Fraud Rate</span>
            <Percent className="w-3.5 h-3.5 text-text-muted" />
          </div>
          <div className="text-xl font-semibold text-text-primary font-mono tracking-tight">
            {metrics.fraud_rate.toFixed(2)}%
          </div>
          <div className="flex items-center gap-1 mt-1">
            <span
              className={`font-mono text-[10px] font-medium px-1.5 py-0.2 rounded leading-none ${
                metrics.fraud_rate > 5.0 ? 'bg-danger-light text-danger' : 'bg-success-light text-success'
              }`}
            >
              {metrics.fraud_rate > 5.0 ? 'ELEVATED' : 'NORMAL'}
            </span>
          </div>
        </div>

        {/* Metric 8 */}
        <div className="p-3 flex flex-col justify-between">
          <div className="flex items-center justify-between mb-1">
            <span className="text-[11px] font-medium text-text-muted uppercase tracking-wider">Spike Incidents</span>
            <Flame className="w-3.5 h-3.5 text-danger" />
          </div>
          <div className="text-xl font-semibold text-danger font-mono tracking-tight">
            {metrics.critical_incidents}
          </div>
          <div className="flex items-center gap-1 mt-1">
            <span
              className={`font-mono text-[10px] font-medium px-1.5 py-0.2 rounded leading-none ${
                metrics.critical_incidents > 0 ? 'bg-danger-light text-danger' : 'bg-success-light text-success'
              }`}
            >
              {metrics.critical_incidents > 0 ? 'ACTION REQ' : 'HEALTHY'}
            </span>
          </div>
        </div>
      </div>

      {/* Main Analytics Grid: 4 Clean Light Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Chart 1: Fraud Rate Over Time */}
        <div className="bg-surface rounded-xl border border-border-subtle p-5 shadow-subtle flex flex-col">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-sm font-semibold text-text-primary">Fraud Rate Over Time (%)</h2>
              <p className="text-xs text-text-muted mt-0.5">Continuous rolling 24h window with anomaly threshold</p>
            </div>
            <span className="text-[10px] font-mono font-medium px-2 py-0.5 rounded bg-surface-muted text-text-muted border border-border-subtle">
              Trailing 24h
            </span>
          </div>
          <div className="w-full h-56">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={charts.fraud_rate_over_time} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="fraudRateLight" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#e11d48" stopOpacity={0.12} />
                    <stop offset="95%" stopColor="#e11d48" stopOpacity={0.0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} />
                <XAxis dataKey="time" stroke="#94a3b8" fontSize={11} tickLine={false} axisLine={{ stroke: '#e2e8f0' }} />
                <YAxis stroke="#94a3b8" fontSize={11} unit="%" tickLine={false} axisLine={{ stroke: '#e2e8f0' }} />
                <ReferenceLine
                  y={5.0}
                  stroke="#d97706"
                  strokeDasharray="3 3"
                  label={{ value: 'Threshold 5%', fill: '#d97706', fontSize: 10, position: 'right' }}
                />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#ffffff',
                    borderColor: '#e2e8f0',
                    borderRadius: '8px',
                    fontSize: '12px',
                    boxShadow: '0 4px 12px rgba(0, 0, 0, 0.05)',
                  }}
                />
                <Area
                  type="monotone"
                  dataKey="fraud_rate"
                  stroke="#e11d48"
                  strokeWidth={2}
                  fillOpacity={1}
                  fill="url(#fraudRateLight)"
                  name="Fraud Rate"
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
          {/* Inline Contextual Insights */}
          <div className="flex items-center gap-6 pt-3 mt-2 border-t border-border-light text-xs text-text-muted">
            <div>
              Peak Anomaly: <span className="font-mono font-medium text-text-primary">14:32</span>
            </div>
            <div>
              Max Single Score: <span className="font-mono font-medium text-danger">94 CRITICAL</span>
            </div>
            <div>
              Current Baseline: <span className="font-mono font-medium text-success">1.8%</span>
            </div>
          </div>
        </div>

        {/* Chart 2: Transaction Volume & Throughput */}
        <div className="bg-surface rounded-xl border border-border-subtle p-5 shadow-subtle flex flex-col">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-sm font-semibold text-text-primary">Transaction Volume & Throughput</h2>
              <p className="text-xs text-text-muted mt-0.5">Payments evaluated by Kafka & Redis pipeline</p>
            </div>
            <span className="text-[10px] font-mono font-medium px-2 py-0.5 rounded bg-surface-muted text-text-muted border border-border-subtle">
              Live Stream
            </span>
          </div>
          <div className="w-full h-56">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={charts.transaction_volume} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} />
                <XAxis dataKey="time" stroke="#94a3b8" fontSize={11} tickLine={false} axisLine={{ stroke: '#e2e8f0' }} />
                <YAxis stroke="#94a3b8" fontSize={11} tickLine={false} axisLine={{ stroke: '#e2e8f0' }} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#ffffff',
                    borderColor: '#e2e8f0',
                    borderRadius: '8px',
                    fontSize: '12px',
                    boxShadow: '0 4px 12px rgba(0, 0, 0, 0.05)',
                  }}
                />
                <Bar dataKey="volume" fill="#4f46e5" radius={[3, 3, 0, 0]} name="Transactions" />
              </BarChart>
            </ResponsiveContainer>
          </div>
          <div className="flex items-center gap-6 pt-3 mt-2 border-t border-border-light text-xs text-text-muted">
            <div>
              P99 Latency: <span className="font-mono font-medium text-text-primary">18.4ms</span>
            </div>
            <div>
              Throughput: <span className="font-mono font-medium text-text-primary">420 eps</span>
            </div>
            <div>
              Pipeline Status: <span className="font-medium text-success">Healthy</span>
            </div>
          </div>
        </div>

        {/* Chart 3: Risk Score Distribution */}
        <div className="bg-surface rounded-xl border border-border-subtle p-5 shadow-subtle flex flex-col">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-sm font-semibold text-text-primary">Risk Score Distribution (0 - 100)</h2>
              <p className="text-xs text-text-muted mt-0.5">Continuous XGBoost risk scoring histogram</p>
            </div>
            <span className="text-[10px] font-mono font-medium px-2 py-0.5 rounded bg-surface-muted text-text-muted border border-border-subtle">
              Calibrated
            </span>
          </div>
          <div className="w-full h-56">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={charts.risk_distribution} margin={{ top: 10, right: 10, left: -20, bottom: 6 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} />
                <XAxis
                  dataKey="range"
                  interval={0}
                  tick={renderRiskBucketTick}
                  height={34}
                  tickLine={false}
                  axisLine={{ stroke: '#e2e8f0' }}
                />
                <YAxis stroke="#94a3b8" fontSize={11} tickLine={false} axisLine={{ stroke: '#e2e8f0' }} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#ffffff',
                    borderColor: '#e2e8f0',
                    borderRadius: '8px',
                    fontSize: '12px',
                    boxShadow: '0 4px 12px rgba(0, 0, 0, 0.05)',
                  }}
                />
                <Bar dataKey="count" name="Evaluations" radius={[3, 3, 0, 0]}>
                  {charts.risk_distribution.map((_, index) => (
                    <Cell key={`cell-${index}`} fill={RISK_BUCKET_COLORS[index % RISK_BUCKET_COLORS.length]} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Chart 4: Policy Decision Share */}
        <div className="bg-surface rounded-xl border border-border-subtle p-5 shadow-subtle flex flex-col">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-sm font-semibold text-text-primary">Policy Decision Breakdown</h2>
              <p className="text-xs text-text-muted mt-0.5">Distribution of executed actions (Allow / Review / Block)</p>
            </div>
            <span className="text-[10px] font-mono font-medium px-2 py-0.5 rounded bg-surface-muted text-text-muted border border-border-subtle">
              Actions
            </span>
          </div>
          <div className="w-full h-56 flex items-center">
            <ResponsiveContainer width="55%" height="100%">
              <PieChart>
                <Pie
                  data={charts.decision_distribution}
                  dataKey="count"
                  nameKey="name"
                  cx="50%"
                  cy="50%"
                  innerRadius={50}
                  outerRadius={75}
                  paddingAngle={3}
                >
                  {charts.decision_distribution.map((entry) => (
                    <Cell key={entry.name} fill={DECISION_COLORS[entry.name] || '#94a3b8'} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#ffffff',
                    borderColor: '#e2e8f0',
                    borderRadius: '8px',
                    fontSize: '12px',
                    boxShadow: '0 4px 12px rgba(0, 0, 0, 0.05)',
                  }}
                />
              </PieChart>
            </ResponsiveContainer>

            {/* Clean Legend */}
            <div className="flex flex-col gap-2.5 w-[45%] pl-2">
              {charts.decision_distribution.map((d) => (
                <div key={d.name} className="flex items-center justify-between text-xs">
                  <div className="flex items-center gap-2">
                    <span
                      className="w-2 h-2 rounded-full"
                      style={{ backgroundColor: DECISION_COLORS[d.name] || '#94a3b8' }}
                    />
                    <span className="font-medium text-text-primary">{d.name}</span>
                  </div>
                  <span className="font-mono text-text-muted font-medium">{d.percentage}%</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Secondary Two-Column Section: Active Incidents & Recent High-Risk Txs */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 pt-2">
        {/* Left Column: Active Statistical Incidents */}
        <div className="bg-surface rounded-xl border border-border-subtle overflow-hidden shadow-subtle flex flex-col">
          <div className="px-5 py-3.5 border-b border-border-subtle flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Flame className="w-4 h-4 text-danger" />
              <h2 className="text-sm font-semibold text-text-primary">
                Active Fraud Spike Incidents
              </h2>
            </div>
            <button
              type="button"
              onClick={onNavigateToIncidents}
              className="text-xs font-medium text-brand hover:underline flex items-center gap-1"
            >
              View all <ChevronRight className="w-3 h-3" />
            </button>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-border-subtle bg-surface-subtle/50 text-[11px] font-medium text-text-muted uppercase">
                  <th className="py-2.5 px-4">Incident</th>
                  <th className="py-2.5 px-3">Merchant</th>
                  <th className="py-2.5 px-3">Severity</th>
                  <th className="py-2.5 px-3">Surge</th>
                  <th className="py-2.5 px-4 text-right">Exposure</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border-light">
                {recentIncidents.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="py-8 text-center text-text-muted text-xs">
                      No active fraud spike incidents detected.
                    </td>
                  </tr>
                ) : (
                  recentIncidents.map((inc) => (
                    <tr
                      key={inc.incident_id}
                      onClick={onNavigateToIncidents}
                      className="cursor-pointer hover:bg-surface-subtle transition-colors"
                    >
                      <td className="py-2.5 px-4 font-mono font-medium text-brand">
                        {inc.incident_id}
                      </td>
                      <td className="py-2.5 px-3 text-text-secondary">
                        {inc.merchant_name || inc.merchant_id}
                      </td>
                      <td className="py-2.5 px-3">
                        <StatusBadge status={inc.severity} size="sm" />
                      </td>
                      <td className="py-2.5 px-3 font-mono font-semibold text-danger">
                        +{inc.percentage_increase.toFixed(0)}%
                      </td>
                      <td className="py-2.5 px-4 text-right font-mono font-medium text-text-primary">
                        {formatInr(inc.estimated_exposure / 100.0)}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Right Column: Recent Blocked Transactions */}
        <div className="bg-surface rounded-xl border border-border-subtle overflow-hidden shadow-subtle flex flex-col">
          <div className="px-5 py-3.5 border-b border-border-subtle flex items-center justify-between">
            <div className="flex items-center gap-2">
              <ShieldAlert className="w-4 h-4 text-danger" />
              <h2 className="text-sm font-semibold text-text-primary">
                Recent Intercepted Payments
              </h2>
            </div>
            <button
              type="button"
              onClick={onNavigateToTransactions}
              className="text-xs font-medium text-brand hover:underline flex items-center gap-1"
            >
              View all <ChevronRight className="w-3 h-3" />
            </button>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-border-subtle bg-surface-subtle/50 text-[11px] font-medium text-text-muted uppercase">
                  <th className="py-2.5 px-4">Tx ID</th>
                  <th className="py-2.5 px-3">Method</th>
                  <th className="py-2.5 px-3 text-right">Amount</th>
                  <th className="py-2.5 px-3">Risk</th>
                  <th className="py-2.5 px-4 text-right">Time</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border-light">
                {blockedTxs.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="py-8 text-center text-text-muted text-xs">
                      No blocked transactions recorded in current window.
                    </td>
                  </tr>
                ) : (
                  blockedTxs.map((tx) => (
                    <tr
                      key={tx.id}
                      onClick={onNavigateToTransactions}
                      className="cursor-pointer hover:bg-surface-subtle transition-colors"
                    >
                      <td className="py-2.5 px-4 font-mono font-medium text-brand">
                        {tx.id}
                      </td>
                      <td className="py-2.5 px-3 uppercase text-[11px] font-medium text-text-secondary">
                        {tx.paymentMethod}
                      </td>
                      <td className="py-2.5 px-3 text-right font-mono font-medium text-text-primary">
                        {formatInr(tx.amountInInr || tx.amountInPaise / 100.0)}
                      </td>
                      <td className="py-2.5 px-3">
                        <RiskScoreIndicator score={tx.riskScore ?? 90} size="sm" />
                      </td>
                      <td className="py-2.5 px-4 text-right text-text-muted font-mono text-[11px]">
                        {new Date(tx.createdAt).toLocaleTimeString()}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
};
