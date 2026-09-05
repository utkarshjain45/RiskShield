import React, { useEffect, useState } from 'react';
import { Search, RefreshCw, ChevronLeft, ChevronRight, Eye, Filter } from 'lucide-react';
import { transactionsApi } from '../api';
import { Transaction, Page } from '../types';
import { StatusBadge, RiskScoreIndicator } from '../components/common/StatusBadge';
import { LoadingState } from '../components/common/LoadingState';
import { ErrorState } from '../components/common/ErrorState';
import { EmptyState } from '../components/common/EmptyState';

interface TransactionsPageProps {
  onInvestigateTransaction: (txId: string) => void;
}

export const TransactionsPage: React.FC<TransactionsPageProps> = ({
  onInvestigateTransaction,
}) => {
  const [pageData, setPageData] = useState<Page<Transaction>>({
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

  // Filter States
  const [searchQuery, setSearchQuery] = useState('');
  const [decisionFilter, setDecisionFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [page, setPage] = useState(0);
  const pageSize = 20;

  const fetchTransactions = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await transactionsApi.list({
        search: searchQuery.trim() || undefined,
        decision: decisionFilter !== 'ALL' ? decisionFilter : undefined,
        paymentStatus: statusFilter !== 'ALL' ? statusFilter : undefined,
        page,
        size: pageSize,
      });
      setPageData(res);
    } catch (err: any) {
      setError(err?.message || 'Failed to query transactions');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTransactions();
  }, [page, decisionFilter, statusFilter]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    fetchTransactions();
  };

  const formatInr = (amount?: number) => {
    if (amount === undefined || amount === null) return '₹0.00';
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 2,
    }).format(amount);
  };

  return (
    <div className="space-y-5">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-border-subtle">
        <div>
          <h1 className="text-2xl font-semibold text-text-primary tracking-tight">
            Transactions
          </h1>
          <p className="text-xs text-text-muted mt-0.5">
            Operational risk ledger with real-time scoring, decision filters, and deep-dive triage.
          </p>
        </div>
        <button
          type="button"
          onClick={fetchTransactions}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium bg-surface border border-border-subtle text-text-secondary hover:text-text-primary hover:bg-surface-subtle shadow-subtle transition-colors"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          <span>Refresh</span>
        </button>
      </div>

      {/* Clean Filter Toolbar */}
      <form onSubmit={handleSearchSubmit} className="flex items-center gap-3 flex-wrap">
        <div className="relative flex-1 min-w-[280px]">
          <Search className="w-3.5 h-3.5 text-text-muted absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
          <input
            type="text"
            className="w-full bg-surface border border-border-subtle rounded-md pl-8 pr-3 py-1.5 text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand/20 shadow-subtle transition-all"
            placeholder="Search by Transaction ID, Customer, IP, or Email..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>

        {/* Decision Filter */}
        <select
          className="bg-surface border border-border-subtle rounded-md px-3 py-1.5 text-xs text-text-primary focus:outline-none focus:border-brand shadow-subtle cursor-pointer transition-colors"
          value={decisionFilter}
          onChange={(e) => {
            setDecisionFilter(e.target.value);
            setPage(0);
          }}
        >
          <option value="ALL">All Decisions</option>
          <option value="ALLOW">ALLOW</option>
          <option value="REVIEW">REVIEW</option>
          <option value="BLOCK">BLOCK</option>
        </select>

        {/* Status Filter */}
        <select
          className="bg-surface border border-border-subtle rounded-md px-3 py-1.5 text-xs text-text-primary focus:outline-none focus:border-brand shadow-subtle cursor-pointer transition-colors"
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value);
            setPage(0);
          }}
        >
          <option value="ALL">All Payment Statuses</option>
          <option value="SUCCESS">SUCCESS</option>
          <option value="FAILED">FAILED</option>
          <option value="PENDING">PENDING</option>
          <option value="BLOCKED">BLOCKED</option>
        </select>

        <button
          type="submit"
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium bg-brand text-white hover:bg-brand-hover shadow-subtle transition-colors"
        >
          <Filter className="w-3.5 h-3.5" />
          <span>Filter</span>
        </button>

        {(searchQuery || decisionFilter !== 'ALL' || statusFilter !== 'ALL') && (
          <button
            type="button"
            onClick={() => {
              setSearchQuery('');
              setDecisionFilter('ALL');
              setStatusFilter('ALL');
              setPage(0);
            }}
            className="px-2.5 py-1.5 rounded-md text-xs font-medium bg-surface border border-border-subtle text-text-secondary hover:text-text-primary hover:bg-surface-subtle transition-colors"
          >
            Reset
          </button>
        )}
      </form>

      {/* Lightweight, High-Density Risk Table */}
      <div className="bg-surface rounded-xl border border-border-subtle overflow-hidden shadow-subtle">
        {loading ? (
          <LoadingState message="Fetching paginated transaction ledger..." />
        ) : error ? (
          <ErrorState message={error} onRetry={fetchTransactions} />
        ) : !pageData || pageData.content.length === 0 ? (
          <EmptyState
            title="No transactions match query"
            description="Try loosening your search terms or resetting decision filters."
            action={
              <button
                type="button"
                onClick={() => {
                  setSearchQuery('');
                  setDecisionFilter('ALL');
                  setStatusFilter('ALL');
                  setPage(0);
                }}
                className="px-3 py-1.5 rounded-md text-xs font-medium bg-surface border border-border-subtle text-text-secondary hover:text-text-primary hover:bg-surface-subtle transition-colors"
              >
                Reset Filters
              </button>
            }
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-border-subtle bg-surface-subtle/50 text-[11px] font-medium text-text-muted uppercase">
                    <th className="py-2.5 px-4">Transaction ID</th>
                    <th className="py-2.5 px-3">Timestamp</th>
                    <th className="py-2.5 px-3 text-right">Amount</th>
                    <th className="py-2.5 px-3">Customer</th>
                    <th className="py-2.5 px-3">Method</th>
                    <th className="py-2.5 px-3">Risk Score</th>
                    <th className="py-2.5 px-3">Decision</th>
                    <th className="py-2.5 px-3">Status</th>
                    <th className="py-2.5 px-4 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-light">
                  {pageData.content.map((tx) => (
                    <tr
                      key={tx.id}
                      onClick={() => onInvestigateTransaction(tx.id)}
                      className="cursor-pointer hover:bg-surface-subtle transition-colors"
                    >
                      <td className="py-2.5 px-4 font-mono font-medium text-brand">
                        {tx.id}
                      </td>
                      <td className="py-2.5 px-3 text-text-muted whitespace-nowrap font-mono text-[11px]">
                        {new Date(tx.createdAt).toLocaleTimeString([], {
                          hour: '2-digit',
                          minute: '2-digit',
                          second: '2-digit',
                        })}
                        <span className="ml-1.5 text-text-dim">
                          {new Date(tx.createdAt).toLocaleDateString()}
                        </span>
                      </td>
                      <td className="py-2.5 px-3 text-right font-mono font-medium text-text-primary whitespace-nowrap">
                        {formatInr(tx.amountInInr || tx.amountInPaise / 100.0)}
                      </td>
                      <td className="py-2.5 px-3">
                        <div className="font-medium text-text-primary">{tx.customerId || 'cust_anonymous'}</div>
                        <div className="text-[10px] text-text-muted font-mono">{tx.ipAddress || '127.0.0.1'}</div>
                      </td>
                      <td className="py-2.5 px-3 uppercase text-[11px] font-medium text-text-secondary">
                        {tx.paymentMethod}
                      </td>
                      <td className="py-2.5 px-3">
                        <RiskScoreIndicator score={tx.riskScore} size="sm" />
                      </td>
                      <td className="py-2.5 px-3">
                        <StatusBadge status={tx.decision} size="sm" />
                      </td>
                      <td className="py-2.5 px-3">
                        <span
                          className={`text-[11px] font-mono font-medium ${
                            tx.paymentStatus === 'SUCCESS'
                              ? 'text-success'
                              : tx.paymentStatus === 'BLOCKED'
                              ? 'text-danger'
                              : 'text-text-muted'
                          }`}
                        >
                          {tx.paymentStatus}
                        </span>
                      </td>
                      <td className="py-2.5 px-4 text-right">
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            onInvestigateTransaction(tx.id);
                          }}
                          className="inline-flex items-center gap-1 px-2.5 py-1 rounded text-xs font-medium text-text-secondary hover:text-brand hover:bg-brand-light/50 border border-transparent hover:border-brand-border transition-colors"
                        >
                          <Eye className="w-3.5 h-3.5" />
                          <span>Inspect</span>
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            <div className="px-5 py-3 border-t border-border-subtle bg-surface-subtle/30 flex items-center justify-between text-xs text-text-muted">
              <span>
                Showing page <strong className="text-text-primary font-medium">{pageData.number + 1}</strong> of{' '}
                <strong className="text-text-primary font-medium">{Math.max(1, pageData.totalPages)}</strong> ({pageData.totalElements} records)
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
                  disabled={pageData.last || page + 1 >= pageData.totalPages}
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
