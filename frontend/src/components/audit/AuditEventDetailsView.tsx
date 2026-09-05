import React, { useState } from 'react';
import {
  CreditCard,
  Cpu,
  ShieldCheck,
  Clock,
  FileText,
  Code,
  Copy,
  Check,
  Activity,
  ArrowUpRight,
  ArrowDownRight,
} from 'lucide-react';
import { AuditEvent } from '../../types';

interface AuditEventDetailsViewProps {
  event: AuditEvent;
}

export const AuditEventDetailsView: React.FC<AuditEventDetailsViewProps> = ({ event }) => {
  const [viewMode, setViewMode] = useState<'structured' | 'raw'>('structured');
  const [copied, setCopied] = useState(false);

  // Parse JSON metadata safely
  let parsedMetadata: any = null;
  if (event.metadata) {
    try {
      parsedMetadata = typeof event.metadata === 'string' ? JSON.parse(event.metadata) : event.metadata;
    } catch {
      parsedMetadata = null;
    }
  }

  const rawPayloadText = event.metadata
    ? typeof event.metadata === 'string'
      ? (() => {
          try {
            return JSON.stringify(JSON.parse(event.metadata), null, 2);
          } catch {
            return event.metadata;
          }
        })()
      : JSON.stringify(event.metadata, null, 2)
    : event.details || 'No additional payload context recorded.';

  const handleCopy = () => {
    navigator.clipboard.writeText(rawPayloadText);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  // Helper formatters
  const formatInr = (paise?: number) => {
    if (paise === undefined || paise === null) return null;
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 2,
    }).format(paise / 100);
  };

  const auditId = event.audit_id || event.auditId || `aud_${event.id}`;

  // Extract common fields from metadata if present
  const meta = parsedMetadata || {};
  const tx = meta.transaction || {};
  const cust = tx.customer || meta.customer;
  const dev = tx.device || meta.device;
  const decision = meta.decision || meta.action;
  const riskScore = meta.riskScore !== undefined ? meta.riskScore : meta.risk_score;
  const amountInr = tx.amountInPaise ? formatInr(tx.amountInPaise) : meta.amountInPaise ? formatInr(meta.amountInPaise) : null;
  const topSignals = meta.top_risk_signals || [];
  const velocity = meta.customer_velocity || meta.velocity;

  return (
    <div className="bg-slate-50/80 border border-slate-200 rounded-lg p-4 text-xs shadow-sm space-y-4">
      {/* Header Bar with Toggle & Copy */}
      <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-slate-200">
        <div className="flex items-center gap-2">
          <span className="font-semibold text-slate-800 text-sm">Audit Record Dossier</span>
          <span className="text-slate-400 font-mono text-[11px]">• {auditId}</span>
        </div>

        <div className="flex items-center gap-2">
          {/* View Mode Toggle */}
          <div className="inline-flex rounded-md border border-slate-200 bg-white p-0.5 shadow-sm">
            <button
              type="button"
              onClick={() => setViewMode('structured')}
              className={`inline-flex items-center gap-1 px-2.5 py-1 text-[11px] font-medium rounded transition-colors ${
                viewMode === 'structured'
                  ? 'bg-indigo-600 text-white shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
              }`}
            >
              <FileText className="w-3 h-3" />
              <span>Human Readable</span>
            </button>
            <button
              type="button"
              onClick={() => setViewMode('raw')}
              className={`inline-flex items-center gap-1 px-2.5 py-1 text-[11px] font-medium rounded transition-colors ${
                viewMode === 'raw'
                  ? 'bg-indigo-600 text-white shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
              }`}
            >
              <Code className="w-3 h-3" />
              <span>Raw JSON</span>
            </button>
          </div>

          {/* Copy Button */}
          <button
            type="button"
            onClick={handleCopy}
            className="inline-flex items-center gap-1 px-2.5 py-1 text-[11px] font-medium rounded border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 transition-colors shadow-sm"
          >
            {copied ? <Check className="w-3 h-3 text-emerald-600" /> : <Copy className="w-3 h-3 text-slate-400" />}
            <span>{copied ? 'Copied' : 'Copy'}</span>
          </button>
        </div>
      </div>

      {/* Human-Readable Structured View */}
      {viewMode === 'structured' ? (
        <div className="space-y-3">
          {/* Narrative Summary Alert */}
          {event.details && (
            <div className="bg-white border border-indigo-100 rounded-md p-3 flex items-start gap-2.5 shadow-xs">
              <Activity className="w-4 h-4 text-indigo-600 shrink-0 mt-0.5" />
              <div>
                <span className="text-[10px] font-semibold text-indigo-700 uppercase tracking-wider block mb-0.5">
                  Action Narrative
                </span>
                <p className="text-slate-700 font-medium leading-relaxed">{event.details}</p>
              </div>
            </div>
          )}

          {/* 4-Column Metadata Grid */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 bg-white p-3 rounded-md border border-slate-200">
            <div>
              <span className="text-[10px] text-slate-400 font-medium uppercase tracking-wider block">Service Origin</span>
              <span className="font-mono text-slate-700 font-medium text-[11px] mt-0.5 block">{event.service || 'backend-api'}</span>
            </div>
            <div>
              <span className="text-[10px] text-slate-400 font-medium uppercase tracking-wider block">Actor / Agent</span>
              <span className="font-mono text-slate-700 font-medium text-[11px] mt-0.5 block">{event.actor_id || event.actorId || 'SYSTEM'}</span>
            </div>
            <div>
              <span className="text-[10px] text-slate-400 font-medium uppercase tracking-wider block">Target Entity</span>
              <span className="text-slate-800 font-medium text-[11px] mt-0.5 block">
                {event.entity_type || event.entityType} ({event.entity_id || event.entityId})
              </span>
            </div>
            <div>
              <span className="text-[10px] text-slate-400 font-medium uppercase tracking-wider block">Cryptographic Hash</span>
              <span className="font-mono text-emerald-700 font-medium text-[11px] mt-0.5 block truncate" title={event.payload_hash || event.payloadHash}>
                {event.payload_hash || event.payloadHash ? `SHA256: ${(event.payload_hash || event.payloadHash || '').substring(0, 12)}…` : 'Unhashed'}
              </span>
            </div>
          </div>

          {/* Context Card 1: Decision & Risk Score (if present) */}
          {(decision || riskScore !== undefined || meta.reason) && (
            <div className="bg-white p-3.5 rounded-md border border-slate-200 space-y-2">
              <div className="flex items-center gap-1.5 text-slate-700 font-semibold">
                <ShieldCheck className="w-3.5 h-3.5 text-indigo-600" />
                <span>Risk Decision Assessment</span>
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 pt-1">
                {decision && (
                  <div>
                    <span className="text-[10px] text-slate-400 block uppercase">Decision</span>
                    <span
                      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-semibold mt-0.5 ${
                        decision === 'ALLOW'
                          ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                          : decision === 'BLOCK'
                          ? 'bg-rose-50 text-rose-700 border border-rose-200'
                          : 'bg-amber-50 text-amber-700 border border-amber-200'
                      }`}
                    >
                      ● {decision}
                    </span>
                  </div>
                )}
                {riskScore !== undefined && (
                  <div>
                    <span className="text-[10px] text-slate-400 block uppercase">Calculated Risk Score</span>
                    <span className="text-slate-900 font-bold text-sm mt-0.5 block">{Number(riskScore).toFixed(1)} / 100</span>
                  </div>
                )}
                {meta.policyName && (
                  <div>
                    <span className="text-[10px] text-slate-400 block uppercase">Policy Applied</span>
                    <span className="text-slate-700 font-medium text-xs mt-0.5 block">{meta.policyName}</span>
                  </div>
                )}
              </div>
              {meta.reason && (
                <div className="text-xs text-slate-600 bg-slate-50 p-2 rounded border border-slate-100 mt-2">
                  <span className="font-medium text-slate-800">Policy Evaluation Rule: </span>
                  {meta.reason}
                </div>
              )}
            </div>
          )}

          {/* Context Card 2: Transaction & Customer Details (if present) */}
          {(amountInr || cust || dev || tx.ipAddress || meta.ipAddress) && (
            <div className="bg-white p-3.5 rounded-md border border-slate-200 space-y-2">
              <div className="flex items-center gap-1.5 text-slate-700 font-semibold">
                <CreditCard className="w-3.5 h-3.5 text-indigo-600" />
                <span>Payment & Identity Profile</span>
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-1">
                {amountInr && (
                  <div>
                    <span className="text-[10px] text-slate-400 block uppercase">Amount</span>
                    <span className="text-slate-900 font-bold text-sm text-indigo-700">{amountInr}</span>
                  </div>
                )}
                {tx.paymentMethod && (
                  <div>
                    <span className="text-[10px] text-slate-400 block uppercase">Payment Channel</span>
                    <span className="text-slate-700 font-mono text-xs capitalize">{tx.paymentMethod.replace('_', ' ')}</span>
                  </div>
                )}
                {cust && (
                  <div>
                    <span className="text-[10px] text-slate-400 block uppercase">Customer</span>
                    <span className="text-slate-700 font-medium text-xs truncate block">{cust.email || cust.id}</span>
                  </div>
                )}
                {(tx.ipAddress || meta.ipAddress) && (
                  <div>
                    <span className="text-[10px] text-slate-400 block uppercase">IP Address</span>
                    <span className="font-mono text-slate-700 text-xs">{tx.ipAddress || meta.ipAddress}</span>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Context Card 3: Top Risk Signals & SHAP Contributions (if present) */}
          {topSignals.length > 0 && (
            <div className="bg-white p-3.5 rounded-md border border-slate-200 space-y-2.5">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-1.5 text-slate-700 font-semibold">
                  <Cpu className="w-3.5 h-3.5 text-indigo-600" />
                  <span>Explainable AI Signals (TreeSHAP)</span>
                </div>
                {meta.model_version && (
                  <span className="font-mono text-[10px] text-slate-400">Model: {meta.model_version}</span>
                )}
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 pt-1">
                {topSignals.map((sig: any, idx: number) => {
                  const isPositive = sig.direction === 'INCREASES_RISK' || sig.shap_impact > 0;
                  return (
                    <div
                      key={idx}
                      className={`p-2 rounded border flex items-center justify-between text-xs ${
                        isPositive
                          ? 'bg-rose-50/70 border-rose-200 text-rose-900'
                          : 'bg-emerald-50/70 border-emerald-200 text-emerald-900'
                      }`}
                    >
                      <div className="flex items-center gap-1.5">
                        {isPositive ? (
                          <ArrowUpRight className="w-3.5 h-3.5 text-rose-600 shrink-0" />
                        ) : (
                          <ArrowDownRight className="w-3.5 h-3.5 text-emerald-600 shrink-0" />
                        )}
                        <span className="font-medium">{sig.display_name || sig.feature_name}</span>
                      </div>
                      <span className="font-mono font-bold text-[11px] ml-2">
                        {sig.formatted_impact || (sig.shap_impact > 0 ? `+${sig.shap_impact}` : sig.shap_impact)}
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Context Card 4: Velocity Profile (if present) */}
          {velocity && (
            <div className="bg-white p-3.5 rounded-md border border-slate-200 space-y-2">
              <div className="flex items-center gap-1.5 text-slate-700 font-semibold">
                <Clock className="w-3.5 h-3.5 text-indigo-600" />
                <span>Behavioral Velocity Profile</span>
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1">
                {velocity.transactions_5m !== undefined && (
                  <div className="p-2 bg-slate-50 rounded border border-slate-100">
                    <span className="text-[10px] text-slate-400 block uppercase">Burst (5m)</span>
                    <span className="font-mono font-bold text-slate-800">{velocity.transactions_5m} payments</span>
                  </div>
                )}
                {velocity.transactions_1h !== undefined && (
                  <div className="p-2 bg-slate-50 rounded border border-slate-100">
                    <span className="text-[10px] text-slate-400 block uppercase">Velocity (1h)</span>
                    <span className="font-mono font-bold text-slate-800">{velocity.transactions_1h} payments</span>
                  </div>
                )}
                {meta.device_account_count !== undefined && (
                  <div className="p-2 bg-slate-50 rounded border border-slate-100">
                    <span className="text-[10px] text-slate-400 block uppercase">Device Reuse</span>
                    <span className="font-mono font-bold text-slate-800">{meta.device_account_count} accounts</span>
                  </div>
                )}
                {meta.is_new_device !== undefined && (
                  <div className="p-2 bg-slate-50 rounded border border-slate-100">
                    <span className="text-[10px] text-slate-400 block uppercase">New Device Flag</span>
                    <span className={`font-medium ${meta.is_new_device ? 'text-rose-600' : 'text-slate-600'}`}>
                      {meta.is_new_device ? 'Yes (First Seen)' : 'No (Known)'}
                    </span>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      ) : (
        /* Raw JSON Viewer */
        <div className="space-y-1.5">
          <div className="flex items-center justify-between text-slate-500 text-[11px]">
            <span>Indented JSON Payload</span>
            <span className="font-mono text-[10px]">{rawPayloadText.length} characters</span>
          </div>
          <pre className="bg-white border border-slate-200 p-3 rounded-md text-xs font-mono text-slate-800 overflow-x-auto whitespace-pre-wrap leading-relaxed max-h-96 shadow-xs">
            {rawPayloadText}
          </pre>
        </div>
      )}
    </div>
  );
};
