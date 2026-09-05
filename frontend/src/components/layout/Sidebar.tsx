import React from 'react';
import {
  Shield,
  LayoutDashboard,
  Receipt,
  SearchCheck,
  AlertOctagon,
  Cpu,
  ShieldCheck,
  Sliders,
  CheckCircle2,
} from 'lucide-react';

export type NavigationTab =
  | 'dashboard'
  | 'transactions'
  | 'investigation'
  | 'incidents'
  | 'model'
  | 'audit'
  | 'policies';

interface SidebarProps {
  currentTab: NavigationTab;
  onSelectTab: (tab: NavigationTab) => void;
  openIncidentsCount?: number;
}

export const Sidebar: React.FC<SidebarProps> = ({
  currentTab,
  onSelectTab,
  openIncidentsCount = 0,
}) => {
  const navItems = [
    { id: 'dashboard' as NavigationTab, label: 'Overview', icon: <LayoutDashboard className="w-4 h-4" /> },
    { id: 'transactions' as NavigationTab, label: 'Transactions', icon: <Receipt className="w-4 h-4" /> },
    { id: 'investigation' as NavigationTab, label: 'Risk Analysis', icon: <SearchCheck className="w-4 h-4" /> },
    {
      id: 'incidents' as NavigationTab,
      label: 'Fraud Incidents',
      icon: <AlertOctagon className="w-4 h-4" />,
      badge: openIncidentsCount > 0 ? openIncidentsCount : undefined,
    },
    { id: 'model' as NavigationTab, label: 'Model Evaluation', icon: <Cpu className="w-4 h-4" /> },
    { id: 'audit' as NavigationTab, label: 'Audit Center', icon: <ShieldCheck className="w-4 h-4" /> },
    { id: 'policies' as NavigationTab, label: 'Risk Policies', icon: <Sliders className="w-4 h-4" /> },
  ];

  return (
    <aside
      className="w-60 min-w-[240px] bg-surface border-r border-border-subtle flex flex-col fixed top-0 bottom-0 left-0 z-40 select-none"
      aria-label="Main Navigation"
    >
      {/* Brand Header */}
      <div className="h-14 px-5 flex items-center gap-3 border-b border-border-subtle">
        <div className="w-7 h-7 rounded-md bg-brand flex items-center justify-center text-white shadow-subtle flex-shrink-0">
          <Shield className="w-4 h-4" />
        </div>
        <div>
          <div className="flex items-center gap-1.5">
            <span className="font-semibold text-sm tracking-tight text-text-primary">RiskShield</span>
            <span className="text-[10px] font-mono font-bold px-1.5 py-0.5 rounded bg-brand-light text-brand border border-brand-border leading-none">
              AI
            </span>
          </div>
          <p className="text-[11px] text-text-muted font-normal">Risk Intelligence</p>
        </div>
      </div>

      {/* Navigation Items */}
      <nav className="p-3 flex flex-col gap-1 flex-1 overflow-y-auto">
        <div className="px-2.5 py-1 text-[11px] font-semibold text-text-muted uppercase tracking-wider">
          Platform
        </div>
        {navItems.map((item) => {
          const isActive = currentTab === item.id;
          return (
            <button
              key={item.id}
              type="button"
              className={`w-full flex items-center gap-3 px-3 py-2 rounded-md text-xs transition-colors duration-150 text-left ${
                isActive
                  ? 'font-semibold text-brand bg-brand-light/70 shadow-subtle border-l-2 border-brand'
                  : 'font-medium text-text-secondary hover:text-text-primary hover:bg-surface-subtle'
              }`}
              onClick={() => onSelectTab(item.id)}
              aria-current={isActive ? 'page' : undefined}
            >
              <span className={isActive ? 'text-brand' : 'text-text-muted'}>{item.icon}</span>
              <span className="flex-1 truncate">{item.label}</span>
              {item.badge !== undefined && (
                <span
                  className="text-[10px] font-mono font-semibold px-1.5 py-0.5 rounded bg-danger-light text-danger border border-danger-border leading-none"
                  title={`${item.badge} open incident(s)`}
                >
                  {item.badge}
                </span>
              )}
            </button>
          );
        })}
      </nav>

      {/* Architectural Operational Footer */}
      <div className="p-4 border-t border-border-subtle bg-surface-subtle/50 text-xs text-text-muted flex flex-col gap-2">
        <div className="flex items-center justify-between text-[11px]">
          <span>Engine</span>
          <span className="font-mono text-text-secondary font-medium">XGBoost + SHAP</span>
        </div>
        <div className="flex items-center justify-between text-[11px]">
          <span>Environment</span>
          <span className="text-success font-medium flex items-center gap-1">
            <CheckCircle2 className="w-3 h-3" /> Razorpay Test
          </span>
        </div>
        <div className="flex items-center justify-between text-[10px] text-text-muted pt-1 border-t border-border-light">
          <span>Version</span>
          <span className="font-mono text-text-muted">v1.2.0</span>
        </div>
      </div>
    </aside>
  );
};
