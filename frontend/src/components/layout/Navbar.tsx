import React, { useEffect, useState } from 'react';
import { Database, Cpu, Search, Sparkles, UserCheck } from 'lucide-react';
import { healthApi } from '../../api';

interface NavbarProps {
  onSearch?: (query: string) => void;
  activeMerchant?: string;
  onMerchantChange?: (merchantId: string) => void;
  onOpenAssistant?: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  onSearch,
  activeMerchant = 'ALL',
  onMerchantChange,
  onOpenAssistant,
}) => {
  const [backendHealth, setBackendHealth] = useState<'ONLINE' | 'OFFLINE' | 'CHECKING'>('CHECKING');
  const [mlHealth, setMlHealth] = useState<'ONLINE' | 'OFFLINE' | 'CHECKING'>('CHECKING');

  useEffect(() => {
    const checkHealth = () => {
      healthApi
        .getBackendHealth()
        .then(() => setBackendHealth('ONLINE'))
        .catch(() => setBackendHealth('OFFLINE'));

      healthApi
        .getMlHealth()
        .then((res) => setMlHealth(res?.status === 'UP' ? 'ONLINE' : 'OFFLINE'))
        .catch(() => setMlHealth('OFFLINE'));
    };

    checkHealth();
    const interval = setInterval(checkHealth, 30000);
    return () => clearInterval(interval);
  }, []);

  return (
    <header className="h-14 bg-surface border-b border-border-subtle sticky top-0 z-30 px-6 flex items-center justify-between select-none">
      {/* Search & Scope */}
      <div className="flex items-center gap-5">
        <div className="relative w-80">
          <Search className="w-3.5 h-3.5 text-text-muted absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
          <input
            type="text"
            className="w-full bg-surface-subtle border border-border-subtle rounded-md pl-8 pr-3 py-1.5 text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-brand focus:bg-surface transition-all"
            placeholder="Search tx ID, customer, device, IP..."
            onChange={(e) => onSearch && onSearch(e.target.value)}
          />
        </div>

        {/* Merchant Scope Selector */}
        <div className="flex items-center gap-2 text-xs text-text-secondary">
          <span className="text-text-muted font-medium">Merchant:</span>
          <select
            className="bg-surface-subtle border border-border-subtle rounded-md px-2.5 py-1 text-xs text-text-primary focus:outline-none focus:border-brand cursor-pointer transition-colors"
            value={activeMerchant}
            onChange={(e) => onMerchantChange && onMerchantChange(e.target.value)}
          >
            <option value="ALL">All Merchants (Aggregated)</option>
            <option value="mer_001">mer_001 (Razorpay Retail)</option>
            <option value="mer_002">mer_002 (Express Payments)</option>
            <option value="mer_demo_001">mer_demo_001 (Demo Tenant)</option>
          </select>
        </div>
      </div>

      {/* System Health Indicators & Quick Actions */}
      <div className="flex items-center gap-4">
        {/* Backend Node Status */}
        <div
          className="flex items-center gap-1.5 text-xs"
          title="Spring Boot REST & Webhook Ingestion Service"
        >
          <Database className="w-3 h-3 text-text-muted" />
          <span className="text-text-muted text-[11px]">Backend:</span>
          <span
            className={`font-mono text-[11px] font-semibold flex items-center gap-1 ${
              backendHealth === 'ONLINE'
                ? 'text-success'
                : backendHealth === 'CHECKING'
                ? 'text-warning'
                : 'text-danger'
            }`}
          >
            <span
              className={`w-1.5 h-1.5 rounded-full ${
                backendHealth === 'ONLINE'
                  ? 'bg-success'
                  : backendHealth === 'CHECKING'
                  ? 'bg-warning'
                  : 'bg-danger'
              }`}
            />
            {backendHealth}
          </span>
        </div>

        {/* ML Node Status */}
        <div
          className="flex items-center gap-1.5 text-xs"
          title="Python FastAPI XGBoost & TreeSHAP Node"
        >
          <Cpu className="w-3 h-3 text-text-muted" />
          <span className="text-text-muted text-[11px]">ML Node:</span>
          <span
            className={`font-mono text-[11px] font-semibold flex items-center gap-1 ${
              mlHealth === 'ONLINE'
                ? 'text-success'
                : mlHealth === 'CHECKING'
                ? 'text-warning'
                : 'text-danger'
            }`}
          >
            <span
              className={`w-1.5 h-1.5 rounded-full ${
                mlHealth === 'ONLINE'
                  ? 'bg-success'
                  : mlHealth === 'CHECKING'
                  ? 'bg-warning'
                  : 'bg-danger'
              }`}
            />
            {mlHealth}
          </span>
        </div>

        <div className="h-4 w-px bg-border-subtle" />

        {/* AI Investigation Assistant Trigger */}
        {onOpenAssistant && (
          <button
            type="button"
            onClick={onOpenAssistant}
            id="navbar-ai-assistant-btn"
            className="inline-flex items-center gap-1.5 px-3 py-1 text-xs font-medium rounded-md bg-surface border border-border-subtle text-text-secondary hover:text-brand hover:border-brand-border hover:bg-brand-light/40 transition-all shadow-subtle"
            title="Open AI Investigation Assistant Copilot"
          >
            <Sparkles className="w-3.5 h-3.5 text-brand" />
            <span>Ask RiskShield</span>
          </button>
        )}

        {/* Analyst Identity */}
        <div className="flex items-center gap-1.5 text-xs text-text-secondary bg-surface-subtle px-2 py-1 rounded border border-border-subtle font-mono">
          <UserCheck className="w-3.5 h-3.5 text-text-muted" />
          <span>SEC-OPS</span>
        </div>
      </div>
    </header>
  );
};
