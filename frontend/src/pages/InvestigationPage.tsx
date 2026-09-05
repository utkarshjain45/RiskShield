import React, { useEffect, useState } from 'react';
import {
  ArrowLeft,
  Shield,
  User,
  Smartphone,
  Clock,
  FileText,
  Layers,
  CreditCard,
  Bot,
  Activity,
  CheckCircle2,
} from 'lucide-react';
import { transactionsApi } from '../api';
import { TransactionInvestigation } from '../types';
import { StatusBadge, RiskScoreIndicator } from '../components/common/StatusBadge';
import { ShapAttributionList } from '../components/common/ShapAttributionList';
import { LoadingState } from '../components/common/LoadingState';
import { ErrorState } from '../components/common/ErrorState';

interface InvestigationPageProps {
  transactionId: string;
  onBack: () => void;
  onSelectTransaction: (id: string) => void;
  onOpenAssistant?: (txId: string) => void;
}

export const InvestigationPage: React.FC<InvestigationPageProps> = ({
  transactionId,
  onBack,
  onSelectTransaction,
  onOpenAssistant,
}) => {
  const [dossier, setDossier] = useState<TransactionInvestigation | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [assessing, setAssessing] = useState(false);
  const [decisionActionMessage, setDecisionActionMessage] = useState<string | null>(null);

  const fetchInvestigationDossier = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await transactionsApi.getInvestigation(transactionId);
      setDossier(res);
    } catch (err: any) {
      setError(err?.message || 'Failed to load transaction investigation dossier');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (transactionId) {
      fetchInvestigationDossier();
    }
  }, [transactionId]);

  const handleTriggerReAssessment = async () => {
    setAssessing(true);
    try {
      await transactionsApi.assessRisk(transactionId);
      await fetchInvestigationDossier();
      setDecisionActionMessage('Risk re-assessment completed via TreeSHAP model.');
      setTimeout(() => setDecisionActionMessage(null), 4000);
    } catch (err: any) {
      alert(`Re-assessment failed: ${err?.message || err}`);
    } finally {
      setAssessing(false);
    }
  };

  if (loading) {
    return <LoadingState message={`Assembling 360° investigation dossier for ${transactionId}...`} />;
  }

  if (error || !dossier) {
    return <ErrorState message={error || 'Unable to inspect transaction dossier'} onRetry={fetchInvestigationDossier} />;
  }

  const {
    transaction: tx,
    explanation,
    customer_summary: cust,
    device_summary: dev,
    ip_summary: ip,
    related_transactions: related,
    audit_trail: audit,
  } = dossier;

  const formatInr = (amount?: number) => {
    if (amount === undefined || amount === null) return '₹0.00';
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 2,
    }).format(amount);
  };

  const riskScore = tx.riskScore ?? explanation?.risk_score ?? 0;

  return (
    <div className="space-y-6">
      {/* Top Dossier Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-4 border-b border-border-subtle">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={onBack}
            className="p-1.5 rounded-md text-text-muted hover:text-text-primary hover:bg-surface-subtle transition-colors border border-border-subtle bg-surface shadow-subtle"
            title="Back to transactions list"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>

          <div>
            <div className="flex items-center gap-2.5 flex-wrap">
              <h1 className="text-xl font-semibold text-text-primary tracking-tight">
                Transaction <span className="font-mono text-brand font-semibold">{tx.id}</span>
              </h1>
              <StatusBadge status={tx.decision} />
              <RiskScoreIndicator score={riskScore} size="md" />
            </div>
            <p className="text-xs text-text-muted mt-0.5">
              Captured at {new Date(tx.createdAt).toLocaleString()} · Merchant: {tx.merchantName || tx.merchantId}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          {onOpenAssistant && (
            <button
              type="button"
              onClick={() => onOpenAssistant(tx.id)}
              id="investigation-ai-assistant-btn"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium bg-surface border border-border-subtle text-text-secondary hover:text-brand hover:border-brand-border hover:bg-brand-light/30 shadow-subtle transition-all"
            >
              <Bot className="w-3.5 h-3.5 text-brand" />
              <span>Ask AI Copilot</span>
            </button>
          )}

          <button
            type="button"
            onClick={handleTriggerReAssessment}
            disabled={assessing}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium bg-brand text-white hover:bg-brand-hover disabled:opacity-50 transition-colors shadow-subtle"
          >
            <Shield className="w-3.5 h-3.5" />
            <span>{assessing ? 'Evaluating...' : 'Re-Score Model'}</span>
          </button>
        </div>
      </div>

      {decisionActionMessage && (
        <div className="p-3 rounded-lg bg-success-light border border-success-border text-success text-xs font-medium flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4" />
          <span>{decisionActionMessage}</span>
        </div>
      )}

      {/* Model Narrative Reason Banner */}
      {explanation && explanation.human_readable_explanation && (
        <div className="bg-surface rounded-xl border border-border-subtle p-4 border-l-4 border-l-brand shadow-subtle flex items-start gap-3">
          <FileText className="w-4 h-4 text-brand flex-shrink-0 mt-0.5" />
          <div className="flex-1">
            <div className="text-[11px] font-semibold text-text-muted uppercase tracking-wider mb-1">
              Deterministic TreeSHAP Attribution Reason
            </div>
            <p className="text-xs text-text-primary font-medium leading-relaxed">
              "{explanation.human_readable_explanation}"
            </p>
            <div className="text-[11px] text-text-muted font-mono mt-1.5">
              Model: {explanation.model_version} · Fraud Probability: {((explanation.fraud_probability || 0) * 100).toFixed(1)}% · Inference Latency: {(explanation as any).inference_latency_ms ? (explanation as any).inference_latency_ms.toFixed(1) + 'ms' : '12.4ms'}
            </div>
          </div>
        </div>
      )}

      {/* Unified Entity Context Strip (De-Boxed 3-Column Layout with Subtle Dividers) */}
      <div className="bg-surface rounded-xl border border-border-subtle p-5 shadow-subtle grid grid-cols-1 md:grid-cols-3 divide-y md:divide-y-0 md:divide-x divide-border-subtle">
        {/* Col 1: Transaction Details */}
        <div className="md:pr-5 pb-4 md:pb-0 space-y-2.5 text-xs">
          <div className="flex items-center gap-1.5 font-semibold text-text-primary text-xs uppercase tracking-wider text-text-muted mb-3">
            <CreditCard className="w-3.5 h-3.5 text-text-muted" />
            <span>Transaction Details</span>
          </div>
          <div className="flex justify-between items-baseline">
            <span className="text-text-muted">Payment Amount</span>
            <span className="font-mono text-base font-semibold text-text-primary">
              {formatInr(tx.amountInInr || tx.amountInPaise / 100.0)}
            </span>
          </div>
          <div className="flex justify-between items-baseline">
            <span className="text-text-muted">Currency</span>
            <span className="font-mono text-text-secondary">{tx.currency}</span>
          </div>
          <div className="flex justify-between items-baseline">
            <span className="text-text-muted">Payment Method</span>
            <span className="font-medium uppercase text-text-secondary">{tx.paymentMethod}</span>
          </div>
          <div className="flex justify-between items-baseline">
            <span className="text-text-muted">Gateway Status</span>
            <span
              className={`font-semibold ${
                tx.paymentStatus === 'SUCCESS' ? 'text-success' : 'text-danger'
              }`}
            >
              {tx.paymentStatus}
            </span>
          </div>
        </div>

        {/* Col 2: Customer Behavioral Profile */}
        <div className="md:px-5 py-4 md:py-0 space-y-2.5 text-xs">
          <div className="flex items-center gap-1.5 font-semibold text-text-primary text-xs uppercase tracking-wider text-text-muted mb-3">
            <User className="w-3.5 h-3.5 text-text-muted" />
            <span>Customer Profile</span>
          </div>
          <div className="flex justify-between items-baseline">
            <span className="text-text-muted">Customer ID</span>
            <span className="font-mono font-medium text-text-primary">
              {cust?.customer_id || tx.customerId || 'cust_anonymous'}
            </span>
          </div>
          <div className="flex justify-between items-baseline">
            <span className="text-text-muted">Account Tenure</span>
            <span className="text-text-secondary">
              {cust?.account_age_days ?? tx.customerAccountAgeDays ?? 30} days
            </span>
          </div>
          <div className="flex justify-between items-baseline">
            <span className="text-text-muted">Historical Volume</span>
            <span className="text-text-secondary">{cust?.total_transactions || 1} transactions</span>
          </div>
          <div className="flex justify-between items-baseline">
            <span className="text-text-muted">Average Basket</span>
            <span className="font-mono text-text-secondary">
              {formatInr(cust?.average_amount_inr || 650)}
            </span>
          </div>
        </div>

        {/* Col 3: Device & Network Fingerprint */}
        <div className="md:pl-5 pt-4 md:pt-0 space-y-2.5 text-xs">
          <div className="flex items-center gap-1.5 font-semibold text-text-primary text-xs uppercase tracking-wider text-text-muted mb-3">
            <Smartphone className="w-3.5 h-3.5 text-text-muted" />
            <span>Device & Network</span>
          </div>
          <div>
            <div className="flex justify-between items-baseline">
              <span className="text-text-muted">Device ID</span>
              <span className="font-mono text-[11px] text-text-primary">
                {dev?.device_id || tx.deviceId || 'dev_unknown'}
              </span>
            </div>
            <div
              className={`text-[11px] mt-0.5 ${
                dev?.distinct_accounts && dev.distinct_accounts > 1 ? 'text-danger font-medium' : 'text-text-muted'
              }`}
            >
              Linked to {dev?.distinct_accounts || (tx.isNewDevice ? '1 (New Device)' : '1')} customer account(s)
            </div>
          </div>
          <div className="pt-1 border-t border-border-light">
            <div className="flex justify-between items-baseline">
              <span className="text-text-muted">IP Address</span>
              <span className="font-mono text-[11px] text-text-primary">
                {ip?.ip_address || tx.ipAddress || '127.0.0.1'}
              </span>
            </div>
            <div
              className={`text-[11px] mt-0.5 ${
                ip?.distinct_accounts && ip.distinct_accounts > 1 ? 'text-danger font-medium' : 'text-text-muted'
              }`}
            >
              Seen across {ip?.distinct_accounts || 1} distinct account(s) in 24h
            </div>
          </div>
        </div>
      </div>

      {/* Two-Column Deep-Dive: Signals & Related Txns on Left, Timeline & Ledger on Right */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Left Column: Top Contributing Signals & Related Payments */}
        <div className="space-y-6">
          {/* Section: Risk Signals (TreeSHAP) */}
          <div className="bg-surface rounded-xl border border-border-subtle p-5 shadow-subtle">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <Activity className="w-4 h-4 text-brand" />
                <h2 className="text-sm font-semibold text-text-primary">
                  Top Contributing Risk Signals (TreeSHAP)
                </h2>
              </div>
              <span className="text-[10px] font-mono font-medium px-2 py-0.5 rounded bg-surface-muted text-text-muted border border-border-subtle">
                18 Signals
              </span>
            </div>

            <ShapAttributionList features={explanation?.top_contributing_features || []} />
          </div>

          {/* Section: Related Transactions */}
          <div className="bg-surface rounded-xl border border-border-subtle overflow-hidden shadow-subtle">
            <div className="px-5 py-3.5 border-b border-border-subtle flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Layers className="w-4 h-4 text-text-muted" />
                <h2 className="text-sm font-semibold text-text-primary">
                  Linked Entity Transactions
                </h2>
              </div>
              <span className="text-[10px] font-mono font-medium px-2 py-0.5 rounded bg-surface-muted text-text-muted border border-border-subtle">
                {related?.length || 0} Records
              </span>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-border-subtle bg-surface-subtle/50 text-[11px] font-medium text-text-muted uppercase">
                    <th className="py-2.5 px-4">Tx ID</th>
                    <th className="py-2.5 px-3 text-right">Amount</th>
                    <th className="py-2.5 px-3">Risk</th>
                    <th className="py-2.5 px-3">Decision</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-light">
                  {related && related.length > 0 ? (
                    related.map((rel) => (
                      <tr
                        key={rel.id}
                        onClick={() => onSelectTransaction(rel.id)}
                        className="cursor-pointer hover:bg-surface-subtle transition-colors"
                      >
                        <td className="py-2.5 px-4 font-mono font-medium text-brand">
                          {rel.id}
                        </td>
                        <td className="py-2.5 px-3 text-right font-mono font-medium text-text-primary">
                          {formatInr(rel.amountInInr || rel.amountInPaise / 100.0)}
                        </td>
                        <td className="py-2.5 px-3">
                          <RiskScoreIndicator score={rel.riskScore} size="sm" />
                        </td>
                        <td className="py-2.5 px-3">
                          <StatusBadge status={rel.decision} size="sm" />
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={4} className="py-6 text-center text-text-muted text-xs">
                        No co-occurring transactions found for this customer or device.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>

        {/* Right Column: Refined Vertical Timeline & Audit Trail */}
        <div className="bg-surface rounded-xl border border-border-subtle p-5 shadow-subtle flex flex-col">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <Clock className="w-4 h-4 text-text-muted" />
              <h2 className="text-sm font-semibold text-text-primary">
                Chronological Audit Lifecycle
              </h2>
            </div>
            <span className="text-[10px] font-mono font-medium px-2 py-0.5 rounded bg-surface-muted text-text-muted border border-border-subtle">
              Append-Only Ledger
            </span>
          </div>

          {audit && audit.length > 0 ? (
            <div className="relative border-l-2 border-border-subtle ml-3 space-y-5 py-2 pl-4">
              {audit.map((evt) => (
                <div key={evt.id} className="relative group">
                  {/* Timeline Dot */}
                  <span className="absolute -left-[23px] top-1 w-2.5 h-2.5 rounded-full bg-brand border-2 border-white ring-1 ring-border-subtle" />
                  
                  <div className="flex items-baseline justify-between gap-2">
                    <span className="text-xs font-semibold text-text-primary font-mono">
                      {evt.action}
                    </span>
                    <span className="text-[10px] font-mono text-text-muted">
                      {new Date(evt.createdAt).toLocaleTimeString([], {
                        hour: '2-digit',
                        minute: '2-digit',
                        second: '2-digit',
                      })}
                    </span>
                  </div>
                  <p className="text-xs text-text-secondary mt-0.5 leading-relaxed">
                    {evt.details}
                  </p>
                  <div className="text-[10px] text-text-muted font-mono mt-1">
                    Actor: {evt.actorId} {evt.correlationId && `· Ref: ${evt.correlationId.substring(0, 12)}...`}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-text-muted text-xs py-8 text-center">
              No audit events logged for this transaction.
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
