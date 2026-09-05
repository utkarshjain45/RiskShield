import React, { useEffect, useState } from 'react';
import {
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  ChevronUp,
  ShieldCheck,
  Cpu,
  User,
  Server,
  Key,
} from 'lucide-react';
import { auditApi } from '../api';
import { AuditEvent, Page } from '../types';
import { LoadingState } from '../components/common/LoadingState';
import { ErrorState } from '../components/common/ErrorState';
import { EmptyState } from '../components/common/EmptyState';
import { AuditEventDetailsView } from '../components/audit/AuditEventDetailsView';

export const AuditCenterPage: React.FC = () => {
  const [pageData, setPageData] = useState<Page<AuditEvent>>({
    content: [],
    totalElements: 0,
    totalPages: 0,
    size: 20,
    number: 0,
    first: true,
    last: true,
    empty: true,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [eventTypeFilter, setEventTypeFilter] = useState('ALL');
  const [entityFilter, setEntityFilter] = useState('ALL');
  const [page, setPage] = useState(0);

  // Expanded Event ID for raw JSON details
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const fetchAuditEvents = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await auditApi.list({
        eventType: eventTypeFilter !== 'ALL' ? eventTypeFilter : undefined,
        entityType: entityFilter !== 'ALL' ? entityFilter : undefined,
        page,
        size: 20,
      });
      setPageData(res);
    } catch (err: any) {
      setError(err?.message || 'Failed to query audit ledger');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAuditEvents();
  }, [page, eventTypeFilter, entityFilter]);

  const toggleExpand = (id: number) => {
    setExpandedId(expandedId === id ? null : id);
  };

  const getActionBadgeClass = (action: string) => {
    if (action.includes('RECEIVED') || action.includes('CREATED') || action.includes('SUCCESS') || action.includes('ALLOW')) {
      return 'bg-success-light text-success border-success-border';
    }
    if (action.includes('EVALUATED') || action.includes('SCORED') || action.includes('GENERATED')) {
      return 'bg-brand-light text-brand border-brand-border';
    }
    if (action.includes('BLOCK') || action.includes('CRITICAL') || action.includes('REJECTED') || action.includes('FAIL')) {
      return 'bg-danger-light text-danger border-danger-border';
    }
    return 'bg-warning-light text-warning border-warning-border';
  };

  const getActorIcon = (actorType?: string) => {
    switch (actorType) {
      case 'LLM':
        return <Cpu className="w-3.5 h-3.5 text-brand" />;
      case 'ML_SERVICE':
        return <Cpu className="w-3.5 h-3.5 text-info" />;
      case 'USER':
        return <User className="w-3.5 h-3.5 text-success" />;
      case 'MERCHANT':
        return <Key className="w-3.5 h-3.5 text-warning" />;
      default:
        return <Server className="w-3.5 h-3.5 text-text-muted" />;
    }
  };

  return (
    <div className="space-y-5">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-border-subtle">
        <div>
          <h1 className="text-2xl font-semibold text-text-primary tracking-tight">
            Audit Center
          </h1>
          <p className="text-xs text-text-muted mt-0.5">
            Cryptographically-hashed, append-only chronological ledger tracking all transaction evaluations and security interventions.
          </p>
        </div>
        <button
          type="button"
          onClick={fetchAuditEvents}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium bg-surface border border-border-subtle text-text-secondary hover:text-text-primary hover:bg-surface-subtle shadow-subtle transition-colors"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          <span>Refresh</span>
        </button>
      </div>

      {/* Filter Toolbar */}
      <div className="flex items-center gap-3 flex-wrap">
        <select
          className="bg-surface border border-border-subtle rounded-md px-3 py-1.5 text-xs text-text-primary focus:outline-none focus:border-brand shadow-subtle cursor-pointer transition-colors"
          value={eventTypeFilter}
          onChange={(e) => {
            setEventTypeFilter(e.target.value);
            setPage(0);
          }}
        >
          <option value="ALL">All Event Types (14 Total)</option>
          <option value="TRANSACTION_RECEIVED">TRANSACTION_RECEIVED</option>
          <option value="FEATURES_GENERATED">FEATURES_GENERATED</option>
          <option value="MODEL_SCORED">MODEL_SCORED</option>
          <option value="POLICY_EVALUATED">POLICY_EVALUATED</option>
          <option value="RISK_DECISION_CREATED">RISK_DECISION_CREATED</option>
          <option value="ALERT_CREATED">ALERT_CREATED</option>
          <option value="INCIDENT_CREATED">INCIDENT_CREATED</option>
          <option value="INCIDENT_ACKNOWLEDGED">INCIDENT_ACKNOWLEDGED</option>
          <option value="INCIDENT_RESOLVED">INCIDENT_RESOLVED</option>
          <option value="AI_INVESTIGATION_STARTED">AI_INVESTIGATION_STARTED</option>
          <option value="AI_TOOL_CALLED">AI_TOOL_CALLED</option>
          <option value="AI_RESPONSE_GENERATED">AI_RESPONSE_GENERATED</option>
          <option value="WEBHOOK_RECEIVED">WEBHOOK_RECEIVED</option>
          <option value="WEBHOOK_REJECTED">WEBHOOK_REJECTED</option>
        </select>

        <select
          className="bg-surface border border-border-subtle rounded-md px-3 py-1.5 text-xs text-text-primary focus:outline-none focus:border-brand shadow-subtle cursor-pointer transition-colors"
          value={entityFilter}
          onChange={(e) => {
            setEntityFilter(e.target.value);
            setPage(0);
          }}
        >
          <option value="ALL">All Entity Types</option>
          <option value="Transaction">Transaction</option>
          <option value="FeatureSnapshot">Feature Snapshot</option>
          <option value="RiskAssessment">Risk Assessment</option>
          <option value="RiskPolicy">Risk Policy</option>
          <option value="RiskDecision">Risk Decision</option>
          <option value="Alert">Alert</option>
          <option value="FraudIncident">Fraud Incident</option>
          <option value="AiInvestigation">AI Investigation</option>
          <option value="WebhookReceipt">Webhook Receipt</option>
        </select>
      </div>

      {/* Audit Events Panel */}
      <div className="bg-surface rounded-xl border border-border-subtle overflow-hidden shadow-subtle">
        {loading ? (
          <LoadingState message="Reading chronological audit event stream..." />
        ) : error ? (
          <ErrorState message={error} onRetry={fetchAuditEvents} />
        ) : pageData.content.length === 0 ? (
          <EmptyState
            title="No audit events found"
            description="No logged events match the selected filter criteria."
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-border-subtle bg-surface-subtle/50 text-[11px] font-medium text-text-muted uppercase">
                    <th className="py-2.5 px-4">Audit ID</th>
                    <th className="py-2.5 px-3">Event Type</th>
                    <th className="py-2.5 px-3">Service</th>
                    <th className="py-2.5 px-3">Entity / Ref</th>
                    <th className="py-2.5 px-3">Actor</th>
                    <th className="py-2.5 px-3">Timestamp</th>
                    <th className="py-2.5 px-3">SHA-256 Digest</th>
                    <th className="py-2.5 px-4 text-right">Details</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-light">
                  {pageData.content.map((evt) => {
                    const isExpanded = expandedId === evt.id;
                    const eventName = evt.event_type || evt.eventType || evt.action;
                    const actionClass = getActionBadgeClass(eventName);
                    const auditId = evt.audit_id || evt.auditId || `aud_${evt.id}`;
                    const actorType = evt.actor_type || evt.actorType || 'SYSTEM';

                    return (
                      <React.Fragment key={evt.id}>
                        <tr
                          onClick={() => toggleExpand(evt.id)}
                          className="cursor-pointer hover:bg-surface-subtle transition-colors"
                        >
                          <td className="py-2.5 px-4 font-mono text-text-muted text-[11px]">
                            {auditId.length > 18 ? auditId.substring(0, 18) + '…' : auditId}
                          </td>
                          <td className="py-2.5 px-3">
                            <span
                              className={`inline-block px-2 py-0.5 rounded text-[10px] font-mono font-medium border leading-none ${actionClass}`}
                            >
                              {eventName}
                            </span>
                          </td>
                          <td className="py-2.5 px-3 text-text-muted font-mono text-[11px]">
                            {evt.service || 'backend-api'}
                          </td>
                          <td className="py-2.5 px-3">
                            <span className="font-medium text-text-primary block">{evt.entity_type || evt.entityType}</span>
                            <span className="font-mono text-brand text-[11px]">
                              {evt.entity_id || evt.entityId}
                            </span>
                          </td>
                          <td className="py-2.5 px-3">
                            <div className="flex items-center gap-1.5">
                              {getActorIcon(actorType)}
                              <span className="font-mono font-medium text-text-secondary text-[11px]">
                                {evt.actor_id || evt.actorId}
                              </span>
                            </div>
                          </td>
                          <td className="py-2.5 px-3 text-text-muted font-mono text-[11px] whitespace-nowrap">
                            {new Date(evt.created_at || evt.createdAt || evt.timestamp || '').toLocaleTimeString([], {
                              hour: '2-digit',
                              minute: '2-digit',
                              second: '2-digit',
                            })}
                          </td>
                          <td className="py-2.5 px-3 font-mono text-success text-[11px]">
                            {evt.payload_hash || evt.payloadHash ? (
                              <span
                                title={evt.payload_hash || evt.payloadHash}
                                className="inline-flex items-center gap-1"
                              >
                                <ShieldCheck className="w-3 h-3" />
                                {(evt.payload_hash || evt.payloadHash || '').substring(0, 8)}…
                              </span>
                            ) : (
                              '—'
                            )}
                          </td>
                          <td className="py-2.5 px-4 text-right">
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                toggleExpand(evt.id);
                              }}
                              className="p-1 rounded text-text-muted hover:text-text-primary hover:bg-surface-muted transition-colors"
                            >
                              {isExpanded ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
                            </button>
                          </td>
                        </tr>

                        {isExpanded && (
                          <tr>
                            <td colSpan={8} className="p-3 bg-slate-50/50 border-t border-slate-200">
                              <AuditEventDetailsView event={evt} />
                            </td>
                          </tr>
                        )}
                      </React.Fragment>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            <div className="px-5 py-3 border-t border-border-subtle bg-surface-subtle/30 flex items-center justify-between text-xs text-text-muted">
              <span>
                Showing page <strong className="text-text-primary font-medium">{pageData.number + 1}</strong> of{' '}
                <strong className="text-text-primary font-medium">{Math.max(1, pageData.totalPages)}</strong> ({pageData.totalElements} total events)
              </span>
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  disabled={pageData.first || page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded text-xs font-medium bg-white border border-border-subtle text-text-secondary hover:text-text-primary disabled:opacity-40 disabled:cursor-not-allowed transition-colors shadow-subtle"
                >
                  <ChevronLeft className="w-3.5 h-3.5" />
                  <span>Previous</span>
                </button>
                <button
                  type="button"
                  disabled={pageData.last || page >= pageData.totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded text-xs font-medium bg-white border border-border-subtle text-text-secondary hover:text-text-primary disabled:opacity-40 disabled:cursor-not-allowed transition-colors shadow-subtle"
                >
                  <span>Next</span>
                  <ChevronRight className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
};
