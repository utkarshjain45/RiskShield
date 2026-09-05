import React, { useEffect, useState } from 'react';
import {
  CheckCircle,
  Clock,
  RefreshCw,
  Search,
  Scan,
  ShieldAlert,
  FileText,
  X,
} from 'lucide-react';
import { incidentsApi } from '../api';
import { FraudIncident, IncidentStatus, Page } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { LoadingState } from '../components/common/LoadingState';
import { ErrorState } from '../components/common/ErrorState';
import { EmptyState } from '../components/common/EmptyState';

export const FraudIncidentsPage: React.FC = () => {
  const [incidentsPage, setIncidentsPage] = useState<Page<FraudIncident>>({
    content: [],
    totalElements: 0,
    totalPages: 0,
    size: 15,
    number: 0,
    first: true,
    last: true,
    empty: true,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [merchantIdFilter, setMerchantIdFilter] = useState<string>('');
  const [page, setPage] = useState(0);

  // Selected Incident for Inspection / Resolution
  const [selectedIncident, setSelectedIncident] = useState<FraudIncident | null>(null);
  const [actionType, setActionType] = useState<'ACKNOWLEDGE' | 'RESOLVE' | null>(null);
  const [actorId, setActorId] = useState('ANALYST_SEC_OPS');
  const [resolutionNotes, setResolutionNotes] = useState('');
  const [actionInProgress, setActionInProgress] = useState(false);

  // On-demand spike scan modal
  const [scanModalOpen, setScanModalOpen] = useState(false);
  const [scanMerchant, setScanMerchant] = useState('mer_001');
  const [scanWindow, setScanWindow] = useState('15m');
  const [scanResult, setScanResult] = useState<any | null>(null);
  const [scanning, setScanning] = useState(false);

  const fetchIncidents = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await incidentsApi.list({
        merchantId: merchantIdFilter.trim() || undefined,
        status: statusFilter !== 'ALL' ? (statusFilter as IncidentStatus) : undefined,
        page,
        size: 15,
      });
      setIncidentsPage(res);
    } catch (err: any) {
      setError(err?.message || 'Failed to query fraud spike incidents');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchIncidents();
  }, [page, statusFilter]);

  const handleAcknowledge = async () => {
    if (!selectedIncident) return;
    setActionInProgress(true);
    try {
      const updated = await incidentsApi.acknowledge(selectedIncident.incident_id, actorId);
      setSelectedIncident(updated);
      setActionType(null);
      fetchIncidents();
    } catch (err: any) {
      alert(`Acknowledge failed: ${err?.message || err}`);
    } finally {
      setActionInProgress(false);
    }
  };

  const handleResolve = async () => {
    if (!selectedIncident) return;
    setActionInProgress(true);
    try {
      const updated = await incidentsApi.resolve(selectedIncident.incident_id, actorId, resolutionNotes);
      setSelectedIncident(updated);
      setActionType(null);
      fetchIncidents();
    } catch (err: any) {
      alert(`Resolve failed: ${err?.message || err}`);
    } finally {
      setActionInProgress(false);
    }
  };

  const handleRunScan = async () => {
    setScanning(true);
    setScanResult(null);
    try {
      const res = await incidentsApi.scanSpikes(scanMerchant, scanWindow);
      setScanResult(res);
      fetchIncidents();
    } catch (err: any) {
      alert(`Scan failed: ${err?.message || err}`);
    } finally {
      setScanning(false);
    }
  };

  const formatInr = (amountPaise?: number) => {
    if (!amountPaise) return '₹0';
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(amountPaise / 100.0);
  };

  return (
    <div className="space-y-5">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-border-subtle">
        <div>
          <h1 className="text-2xl font-semibold text-text-primary tracking-tight">
            Fraud Incidents
          </h1>
          <p className="text-xs text-text-muted mt-0.5">
            Statistical anomaly detection tracking multi-window fraud spikes, exposure capital, and root cause triage.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setScanModalOpen(true)}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold bg-brand text-white hover:bg-brand-hover shadow-subtle transition-colors"
          >
            <Scan className="w-3.5 h-3.5" />
            <span>Trigger Spike Scan</span>
          </button>
          <button
            type="button"
            onClick={fetchIncidents}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium bg-surface border border-border-subtle text-text-secondary hover:text-text-primary hover:bg-surface-subtle shadow-subtle transition-colors"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* Filter Toolbar */}
      <div className="flex items-center gap-3 flex-wrap">
        <select
          className="bg-surface border border-border-subtle rounded-md px-3 py-1.5 text-xs text-text-primary focus:outline-none focus:border-brand shadow-subtle cursor-pointer transition-colors"
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value);
            setPage(0);
          }}
        >
          <option value="ALL">All Statuses</option>
          <option value="OPEN">OPEN</option>
          <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
          <option value="RESOLVED">RESOLVED</option>
        </select>

        <div className="relative flex-1 max-w-xs">
          <Search className="w-3.5 h-3.5 text-text-muted absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
          <input
            type="text"
            className="w-full bg-surface border border-border-subtle rounded-md pl-8 pr-3 py-1.5 text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-brand shadow-subtle transition-all"
            placeholder="Filter by Merchant ID..."
            value={merchantIdFilter}
            onChange={(e) => setMerchantIdFilter(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                setPage(0);
                fetchIncidents();
              }
            }}
          />
        </div>
      </div>

      {/* Incidents Table Panel */}
      <div className="bg-surface rounded-xl border border-border-subtle overflow-hidden shadow-subtle">
        {loading ? (
          <LoadingState message="Querying statistical fraud incidents..." />
        ) : error ? (
          <ErrorState message={error} onRetry={fetchIncidents} />
        ) : incidentsPage.content.length === 0 ? (
          <EmptyState
            title="No active fraud incidents"
            description="All merchants are currently operating within expected historical baseline fraud variance."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-border-subtle bg-surface-subtle/50 text-[11px] font-medium text-text-muted uppercase">
                  <th className="py-2.5 px-4">Incident ID</th>
                  <th className="py-2.5 px-3">Merchant</th>
                  <th className="py-2.5 px-3">Severity</th>
                  <th className="py-2.5 px-3">Baseline</th>
                  <th className="py-2.5 px-3">Current</th>
                  <th className="py-2.5 px-3">Surge</th>
                  <th className="py-2.5 px-3">Affected</th>
                  <th className="py-2.5 px-4 text-right">Exposure</th>
                  <th className="py-2.5 px-3">Status</th>
                  <th className="py-2.5 px-4 text-right">Detected</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border-light">
                {incidentsPage.content.map((inc) => (
                  <tr
                    key={inc.incident_id}
                    onClick={() => setSelectedIncident(inc)}
                    className={`cursor-pointer transition-colors ${
                      inc.severity === 'CRITICAL' && inc.status === 'OPEN'
                        ? 'bg-danger-light/30 hover:bg-danger-light/50'
                        : 'hover:bg-surface-subtle'
                    }`}
                  >
                    <td className="py-2.5 px-4 font-mono font-medium text-brand">
                      {inc.incident_id}
                    </td>
                    <td className="py-2.5 px-3 font-medium text-text-primary">
                      {inc.merchant_name || inc.merchant_id}
                    </td>
                    <td className="py-2.5 px-3">
                      <StatusBadge status={inc.severity} size="sm" />
                    </td>
                    <td className="py-2.5 px-3 font-mono text-text-muted">
                      {(inc.baseline_rate * 100).toFixed(1)}%
                    </td>
                    <td
                      className={`py-2.5 px-3 font-mono font-semibold ${
                        inc.current_rate > inc.baseline_rate * 2 ? 'text-danger' : 'text-warning'
                      }`}
                    >
                      {(inc.current_rate * 100).toFixed(1)}%
                    </td>
                    <td className="py-2.5 px-3 font-mono font-bold text-danger">
                      +{inc.percentage_increase.toFixed(0)}%
                    </td>
                    <td className="py-2.5 px-3 font-mono text-text-secondary">
                      {inc.affected_transactions} txs
                    </td>
                    <td className="py-2.5 px-4 text-right font-mono font-semibold text-text-primary">
                      {formatInr(inc.estimated_exposure)}
                    </td>
                    <td className="py-2.5 px-3">
                      <StatusBadge status={inc.status} size="sm" />
                    </td>
                    <td className="py-2.5 px-4 text-right text-text-muted font-mono text-[11px] whitespace-nowrap">
                      {new Date(inc.detected_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Incident Details & Resolution Modal */}
      {selectedIncident && (
        <div className="fixed inset-0 bg-text-primary/30 backdrop-blur-xs flex items-center justify-center z-50 p-4 transition-opacity" role="dialog" aria-modal="true">
          <div className="bg-white border border-border-subtle rounded-xl max-w-xl w-full shadow-modal overflow-hidden flex flex-col animate-in fade-in zoom-in-95 duration-150">
            <div className="px-6 py-4 border-b border-border-subtle flex items-center justify-between bg-surface">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-lg bg-danger-light flex items-center justify-center text-danger">
                  <ShieldAlert className="w-4 h-4" />
                </div>
                <div>
                  <h2 className="text-sm font-semibold text-text-primary">
                    Incident Dossier: {selectedIncident.incident_id}
                  </h2>
                  <p className="text-xs text-text-muted">
                    Merchant: {selectedIncident.merchant_name || selectedIncident.merchant_id} · Detected {new Date(selectedIncident.detected_at).toLocaleString()}
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => {
                  setSelectedIncident(null);
                  setActionType(null);
                }}
                className="p-1 rounded-md text-text-muted hover:text-text-primary hover:bg-surface-subtle transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="p-6 flex flex-col gap-4 text-xs">
              {/* Metric Breakdown Grid */}
              <div className="grid grid-cols-4 gap-2 text-center">
                <div className="bg-surface-subtle p-2.5 rounded-lg border border-border-subtle">
                  <div className="text-[10px] text-text-muted uppercase">Severity</div>
                  <div className="mt-1"><StatusBadge status={selectedIncident.severity} size="sm" /></div>
                </div>
                <div className="bg-surface-subtle p-2.5 rounded-lg border border-border-subtle">
                  <div className="text-[10px] text-text-muted uppercase">Baseline</div>
                  <div className="font-mono font-semibold text-text-secondary mt-1">
                    {(selectedIncident.baseline_rate * 100).toFixed(1)}%
                  </div>
                </div>
                <div className="bg-surface-subtle p-2.5 rounded-lg border border-border-subtle">
                  <div className="text-[10px] text-text-muted uppercase">Spike Surge</div>
                  <div className="font-mono font-semibold text-danger mt-1">
                    +{selectedIncident.percentage_increase.toFixed(0)}%
                  </div>
                </div>
                <div className="bg-surface-subtle p-2.5 rounded-lg border border-border-subtle">
                  <div className="text-[10px] text-text-muted uppercase">Exposure</div>
                  <div className="font-mono font-semibold text-text-primary mt-1">
                    {formatInr(selectedIncident.estimated_exposure)}
                  </div>
                </div>
              </div>

              {/* Statistical Root Cause Analysis */}
              <div className="p-3.5 rounded-lg bg-surface-subtle border border-border-subtle space-y-1">
                <div className="text-[11px] font-semibold text-text-muted uppercase tracking-wider flex items-center gap-1.5">
                  <FileText className="w-3.5 h-3.5" /> Statistical Root Cause Analysis
                </div>
                <p className="text-xs text-text-primary leading-relaxed">
                  {selectedIncident.explanation_summary || (selectedIncident as any).explanation || 'Elevated velocity and card testing attacks exceeded the 3.0 standard deviation anomaly boundary.'}
                </p>
                <div className="text-[11px] text-text-muted font-mono pt-1">
                  Window: {selectedIncident.time_window || (selectedIncident as any).window_duration || '15m'} · Affected Transactions: {selectedIncident.affected_transactions} · Status: {selectedIncident.status}
                </div>
              </div>

              {/* Action Panels */}
              {actionType === 'ACKNOWLEDGE' && (
                <div className="p-3.5 rounded-lg bg-surface-subtle border border-border-subtle space-y-2">
                  <div className="font-medium text-xs text-text-primary">Confirm Analyst Acknowledgment</div>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      className="flex-1 px-3 py-1.5 rounded-md border border-border-subtle bg-white text-xs text-text-primary focus:outline-none focus:border-brand"
                      placeholder="Analyst ID"
                      value={actorId}
                      onChange={(e) => setActorId(e.target.value)}
                    />
                    <button
                      type="button"
                      onClick={handleAcknowledge}
                      disabled={actionInProgress}
                      className="px-3.5 py-1.5 rounded-md text-xs font-semibold bg-brand text-white hover:bg-brand-hover shadow-subtle transition-colors"
                    >
                      {actionInProgress ? 'Recording...' : 'Confirm'}
                    </button>
                  </div>
                </div>
              )}

              {actionType === 'RESOLVE' && (
                <div className="p-3.5 rounded-lg bg-surface-subtle border border-border-subtle space-y-2">
                  <div className="font-medium text-xs text-text-primary">Incident Resolution & Mitigation Notes</div>
                  <textarea
                    rows={3}
                    className="w-full p-2.5 rounded-md border border-border-subtle bg-white text-xs text-text-primary focus:outline-none focus:border-brand"
                    placeholder="Describe findings, IP blocks executed, or policy adjustments made..."
                    value={resolutionNotes}
                    onChange={(e) => setResolutionNotes(e.target.value)}
                  />
                  <div className="flex justify-end gap-2">
                    <button
                      type="button"
                      onClick={() => setActionType(null)}
                      className="px-3 py-1.5 rounded-md text-xs font-medium bg-white border border-border-subtle text-text-secondary hover:text-text-primary"
                    >
                      Cancel
                    </button>
                    <button
                      type="button"
                      onClick={handleResolve}
                      disabled={actionInProgress}
                      className="px-3.5 py-1.5 rounded-md text-xs font-semibold bg-success text-white hover:bg-success-hover shadow-subtle transition-colors"
                    >
                      {actionInProgress ? 'Resolving...' : 'Confirm Resolution'}
                    </button>
                  </div>
                </div>
              )}
            </div>

            <div className="px-6 py-3.5 border-t border-border-subtle bg-surface-subtle flex items-center justify-end gap-2.5">
              {selectedIncident.status === 'OPEN' && !actionType && (
                <button
                  type="button"
                  onClick={() => setActionType('ACKNOWLEDGE')}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium bg-white border border-border-subtle text-text-secondary hover:text-text-primary shadow-subtle transition-colors"
                >
                  <Clock className="w-3.5 h-3.5" />
                  <span>Acknowledge</span>
                </button>
              )}
              {selectedIncident.status !== 'RESOLVED' && !actionType && (
                <button
                  type="button"
                  onClick={() => setActionType('RESOLVE')}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold bg-brand text-white hover:bg-brand-hover shadow-subtle transition-colors"
                >
                  <CheckCircle className="w-3.5 h-3.5" />
                  <span>Resolve Incident</span>
                </button>
              )}
              <button
                type="button"
                onClick={() => {
                  setSelectedIncident(null);
                  setActionType(null);
                }}
                className="px-3 py-1.5 rounded-md text-xs font-medium bg-white border border-border-subtle text-text-secondary hover:text-text-primary"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Trigger Statistical Spike Scan Modal */}
      {scanModalOpen && (
        <div className="fixed inset-0 bg-text-primary/30 backdrop-blur-xs flex items-center justify-center z-50 p-4 transition-opacity" role="dialog" aria-modal="true">
          <div className="bg-white border border-border-subtle rounded-xl max-w-md w-full shadow-modal overflow-hidden flex flex-col animate-in fade-in zoom-in-95 duration-150">
            <div className="px-6 py-4 border-b border-border-subtle flex items-center justify-between bg-surface">
              <h2 className="text-sm font-semibold text-text-primary">Trigger Statistical Spike Check</h2>
              <button
                type="button"
                onClick={() => {
                  setScanModalOpen(false);
                  setScanResult(null);
                }}
                className="p-1 rounded-md text-text-muted hover:text-text-primary hover:bg-surface-subtle transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="p-6 flex flex-col gap-4 text-xs">
              <div>
                <label className="block text-xs font-medium text-text-secondary mb-1">Target Merchant</label>
                <select
                  className="w-full bg-surface-subtle border border-border-subtle rounded-md px-3 py-1.5 text-xs text-text-primary focus:outline-none focus:border-brand"
                  value={scanMerchant}
                  onChange={(e) => setScanMerchant(e.target.value)}
                >
                  <option value="mer_001">mer_001 (Razorpay Retail)</option>
                  <option value="mer_002">mer_002 (Express Payments)</option>
                  <option value="mer_demo_001">mer_demo_001 (Demo Tenant)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-medium text-text-secondary mb-1">Rolling Anomaly Window</label>
                <select
                  className="w-full bg-surface-subtle border border-border-subtle rounded-md px-3 py-1.5 text-xs text-text-primary focus:outline-none focus:border-brand"
                  value={scanWindow}
                  onChange={(e) => setScanWindow(e.target.value)}
                >
                  <option value="5m">5 Minutes (Ultra-Fast Velocity)</option>
                  <option value="15m">15 Minutes (Burst Surge)</option>
                  <option value="1h">1 Hour (Sustained Wave)</option>
                  <option value="24h">24 Hours (Baseline Shift)</option>
                </select>
              </div>

              {scanResult && (
                <div
                  className={`p-3 rounded-lg border text-xs ${
                    scanResult.spike_detected
                      ? 'bg-danger-light border-danger-border'
                      : 'bg-success-light border-success-border'
                  }`}
                >
                  <div className={`font-semibold mb-1 ${scanResult.spike_detected ? 'text-danger' : 'text-success'}`}>
                    {scanResult.spike_detected ? '⚠️ STATISTICAL SPIKE DETECTED' : '✓ NORMAL BASELINE'}
                  </div>
                  <div className="text-text-secondary">
                    Current Rate: {(scanResult.current_fraud_rate * 100).toFixed(2)}% vs Baseline {(scanResult.baseline_fraud_rate * 100).toFixed(2)}%
                  </div>
                  {scanResult.spike_detected && (
                    <div className="text-danger font-mono font-medium mt-0.5">
                      Z-Score: {scanResult.z_score?.toFixed(2)} (&gt; 3.0 threshold)
                    </div>
                  )}
                </div>
              )}
            </div>

            <div className="px-6 py-3.5 border-t border-border-subtle bg-surface-subtle flex items-center justify-end gap-2.5">
              <button
                type="button"
                onClick={() => {
                  setScanModalOpen(false);
                  setScanResult(null);
                }}
                className="px-3 py-1.5 rounded-md text-xs font-medium bg-white border border-border-subtle text-text-secondary hover:text-text-primary"
              >
                Close
              </button>
              <button
                type="button"
                onClick={handleRunScan}
                disabled={scanning}
                className="px-3.5 py-1.5 rounded-md text-xs font-semibold bg-brand text-white hover:bg-brand-hover disabled:opacity-50 shadow-subtle transition-colors"
              >
                {scanning ? 'Analyzing Windows...' : 'Execute Spike Scan'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
