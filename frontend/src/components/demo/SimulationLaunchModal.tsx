import React, { useState } from 'react';
import {
  Play,
  Square,
  X,
  Zap,
  Smartphone,
  Globe,
  TrendingUp,
  CheckCircle,
  ShieldAlert,
  Sliders,
} from 'lucide-react';
import { SimulationMode, StartSimulationRequest, SimulationStatusDto } from '../../types';

interface SimulationLaunchModalProps {
  isOpen: boolean;
  onClose: () => void;
  onLaunch: (req: StartSimulationRequest) => Promise<void>;
  isLaunching: boolean;
  activeSimulation?: SimulationStatusDto | null;
  onStopSimulation?: () => Promise<void>;
  isStopping?: boolean;
}

interface ScenarioOption {
  mode: SimulationMode;
  name: string;
  category: string;
  icon: React.ReactNode;
  description: string;
  mechanism: string;
  expectedResult: string;
}

const SCENARIOS: ScenarioOption[] = [
  {
    mode: 'COORDINATED_FRAUD_SPIKE',
    name: 'Coordinated Fraud Spike',
    category: 'CRITICAL MULTI-VECTOR',
    icon: <ShieldAlert className="w-4 h-4 text-danger" />,
    description: 'Coordinated botnet attack sharing rooted emulator farms, Tor proxies, and abnormal amounts.',
    mechanism: 'Rapid account cycling, emulator hardware flags, datacenter proxy IPs, basket surges (₹75k–₹2.5L).',
    expectedResult: 'Fraud rate surges > 35%, Fraud Spike Detector fires CRITICAL incident, ₹20L+ exposure recorded.',
  },
  {
    mode: 'VELOCITY_ATTACK',
    name: 'Velocity Attack',
    category: 'CARDING SCRIPT',
    icon: <Zap className="w-4 h-4 text-warning" />,
    description: 'High-frequency script testing stolen payment credentials in rapid succession.',
    mechanism: 'Single customer and card firing 10+ payments within seconds to trigger Redis sliding-window limits.',
    expectedResult: 'Redis tx_5m counter spikes, SHAP velocity signal exceeds +0.25, policy triggers REVIEW and BLOCK.',
  },
  {
    mode: 'DEVICE_ABUSE',
    name: 'Device Abuse',
    category: 'EMULATOR FARM',
    icon: <Smartphone className="w-4 h-4 text-brand" />,
    description: 'Single rooted Android emulator hardware instance cycling through 15+ synthetic accounts.',
    mechanism: 'Persistent device_id with isEmulator: true generating transactions across diverse 1-day-old accounts.',
    expectedResult: 'Redis device_account_count hits 8+, TreeSHAP flags emulator signature, triggers immediate BLOCK.',
  },
  {
    mode: 'IP_CLUSTER_ATTACK',
    name: 'IP Cluster Attack',
    category: 'PROXY SYNDICATE',
    icon: <Globe className="w-4 h-4 text-info" />,
    description: 'Datacenter proxy or Tor exit node distributing attempts across diverse customer accounts.',
    mechanism: 'Shared bulletproof IP address originating payment attempts across synthetic cards and identities.',
    expectedResult: 'Redis ip_account_count escalates, SHAP flags IP cluster risk, triggers REVIEW and BLOCK.',
  },
  {
    mode: 'AMOUNT_ANOMALY',
    name: 'Amount Anomaly',
    category: 'OUTLIER VALUE',
    icon: <TrendingUp className="w-4 h-4 text-warning" />,
    description: 'Sudden massive luxury purchase from an account with low historical average spend.',
    mechanism: 'Low baseline user (historical avg ₹600) suddenly attempting ₹1,50,000 – ₹4,50,000 purchases.',
    expectedResult: 'Amount deviation ratio exceeds 200x, violates single-transaction policy limits, triggers BLOCK.',
  },
  {
    mode: 'NORMAL_TRAFFIC',
    name: 'Normal Traffic',
    category: 'ORGANIC BENCHMARK',
    icon: <CheckCircle className="w-4 h-4 text-success" />,
    description: 'Legitimate consumers across Indian metros with typical basket sizes.',
    mechanism: 'Natural basket sizes (₹450–₹3,500), residential IP addresses, verified devices, established accounts.',
    expectedResult: '95%+ ALLOW decisions, risk scores < 25, zero alerts, maintains healthy baseline fraud rate (~1.8%).',
  },
];

export const SimulationLaunchModal: React.FC<SimulationLaunchModalProps> = ({
  isOpen,
  onClose,
  onLaunch,
  isLaunching,
  activeSimulation,
  onStopSimulation,
  isStopping,
}) => {
  const [selectedMode, setSelectedMode] = useState<SimulationMode>(
    activeSimulation?.mode || 'COORDINATED_FRAUD_SPIKE'
  );
  const [transactionCount, setTransactionCount] = useState<number>(30);
  const [intervalMs, setIntervalMs] = useState<number>(350);

  if (!isOpen) return null;

  const isRunning = activeSimulation?.status === 'RUNNING';
  const selectedScenario = SCENARIOS.find((s) => s.mode === selectedMode) || SCENARIOS[0];

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    await onLaunch({
      mode: selectedMode,
      transaction_count: transactionCount,
      interval_ms: intervalMs,
      merchant_id: 'mer_demo_001',
    });
  };

  return (
    <div
      className="fixed inset-0 bg-text-primary/30 backdrop-blur-xs flex items-center justify-center z-50 p-4 transition-opacity"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
    >
      <div className="bg-white border border-border-subtle rounded-xl max-w-2xl w-full shadow-modal overflow-hidden flex flex-col animate-in fade-in zoom-in-95 duration-150">
        {/* Modal Header */}
        <div className="px-6 py-4 border-b border-border-subtle flex items-center justify-between bg-surface">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-brand-light flex items-center justify-center text-brand">
              <Sliders className="w-4 h-4" />
            </div>
            <div>
              <h2 id="modal-title" className="text-sm font-semibold text-text-primary">
                Demo Simulation Engine
              </h2>
              <p className="text-xs text-text-muted">
                Inject realistic payment attacks directly into the live risk engine pipeline.
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-md text-text-muted hover:text-text-primary hover:bg-surface-muted transition-colors"
            aria-label="Close dialog"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col">
          <div className="p-6 flex flex-col gap-4 overflow-y-auto max-h-[75vh]">
            {/* Live Simulation Monitor if Running */}
            {isRunning && activeSimulation && (
              <div className="p-3.5 rounded-lg bg-danger-light border border-danger-border">
                <div className="flex justify-between items-center mb-2.5">
                  <div className="flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-danger animate-ping" />
                    <span className="text-xs font-semibold text-danger tracking-wide uppercase">
                      Active Simulation in Progress
                    </span>
                  </div>
                  <span className="font-mono text-xs font-medium text-text-primary">
                    {activeSimulation.generated_count} / {activeSimulation.target_count} txs
                  </span>
                </div>

                <div className="grid grid-cols-4 gap-2 text-center font-mono text-xs">
                  <div className="bg-white/80 p-2 rounded border border-danger-border/60">
                    <div className="text-[10px] text-text-muted uppercase">Allowed</div>
                    <div className="font-semibold text-success">{activeSimulation.allowed_count}</div>
                  </div>
                  <div className="bg-white/80 p-2 rounded border border-danger-border/60">
                    <div className="text-[10px] text-text-muted uppercase">Review</div>
                    <div className="font-semibold text-warning">{activeSimulation.review_count}</div>
                  </div>
                  <div className="bg-white/80 p-2 rounded border border-danger-border/60">
                    <div className="text-[10px] text-text-muted uppercase">Blocked</div>
                    <div className="font-semibold text-danger">{activeSimulation.blocked_count}</div>
                  </div>
                  <div className="bg-white/80 p-2 rounded border border-danger-border/60">
                    <div className="text-[10px] text-text-muted uppercase">Fraud Rate</div>
                    <div
                      className={`font-semibold ${
                        Number(activeSimulation.fraud_rate) > 0.2 ? 'text-danger' : 'text-text-primary'
                      }`}
                    >
                      {((activeSimulation.fraud_rate || 0) * 100).toFixed(1)}%
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* Scenario Selection Cards */}
            <div>
              <div className="text-[11px] font-semibold text-text-muted uppercase tracking-wider mb-2">
                Select Fraud Attack Scenario
              </div>

              <div className="grid grid-cols-1 gap-2 max-h-64 overflow-y-auto pr-1">
                {SCENARIOS.map((scenario) => {
                  const isSelected = selectedMode === scenario.mode;
                  return (
                    <div
                      key={scenario.mode}
                      onClick={() => setSelectedMode(scenario.mode)}
                      className={`p-3 rounded-lg border cursor-pointer transition-all flex items-start gap-3 text-left ${
                        isSelected
                          ? 'border-brand bg-brand-light/60 shadow-subtle ring-1 ring-brand/20'
                          : 'border-border-subtle bg-surface hover:border-border-medium hover:bg-surface-subtle'
                      }`}
                      role="radio"
                      aria-checked={isSelected}
                      tabIndex={0}
                      onKeyDown={(e) => {
                        if (e.key === ' ' || e.key === 'Enter') {
                          e.preventDefault();
                          setSelectedMode(scenario.mode);
                        }
                      }}
                    >
                      <div className="pt-0.5">
                        <div
                          className={`w-4 h-4 rounded-full border flex items-center justify-center transition-colors ${
                            isSelected ? 'border-brand' : 'border-border-medium'
                          }`}
                        >
                          {isSelected && <div className="w-2 h-2 rounded-full bg-brand" />}
                        </div>
                      </div>

                      <div className="flex-1 min-w-0">
                        <div className="flex items-center justify-between gap-2 mb-1">
                          <div className="flex items-center gap-1.5">
                            {scenario.icon}
                            <span className="font-medium text-xs text-text-primary">{scenario.name}</span>
                          </div>
                          <span className="text-[10px] font-mono font-medium px-1.5 py-0.5 rounded bg-surface-muted text-text-muted border border-border-subtle">
                            {scenario.category}
                          </span>
                        </div>
                        <p className="text-[11px] text-text-secondary line-clamp-2 leading-relaxed">
                          {scenario.description}
                        </p>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Selected Scenario Details & Mechanics */}
            <div className="p-3 rounded-lg bg-surface-subtle border border-border-subtle text-xs space-y-1.5">
              <div className="flex gap-2">
                <span className="text-text-muted font-medium min-w-[90px]">Mechanisms:</span>
                <span className="text-text-secondary">{selectedScenario.mechanism}</span>
              </div>
              <div className="flex gap-2">
                <span className="text-text-muted font-medium min-w-[90px]">Pipeline Impact:</span>
                <span className="text-success font-medium">{selectedScenario.expectedResult}</span>
              </div>
            </div>

            {/* Parameters Grid */}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-text-secondary mb-1">
                  Transaction Volume
                </label>
                <input
                  type="number"
                  min={5}
                  max={100}
                  value={transactionCount}
                  onChange={(e) => setTransactionCount(Math.max(5, parseInt(e.target.value) || 30))}
                  className="w-full px-3 py-1.5 rounded-md border border-border-subtle bg-white text-xs text-text-primary focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand/20 transition-colors"
                />
                <span className="text-[11px] text-text-muted mt-0.5 block">Transactions to inject (5 - 100)</span>
              </div>

              <div>
                <label className="block text-xs font-medium text-text-secondary mb-1">
                  Pacing Interval (ms)
                </label>
                <input
                  type="number"
                  min={100}
                  max={2000}
                  step={50}
                  value={intervalMs}
                  onChange={(e) => setIntervalMs(Math.max(100, parseInt(e.target.value) || 350))}
                  className="w-full px-3 py-1.5 rounded-md border border-border-subtle bg-white text-xs text-text-primary focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand/20 transition-colors"
                />
                <span className="text-[11px] text-text-muted mt-0.5 block">Delay between events (100ms - 2000ms)</span>
              </div>
            </div>
          </div>

          {/* Modal Footer with Aligned Actions */}
          <div className="px-6 py-3.5 border-t border-border-subtle bg-surface-subtle flex items-center justify-end gap-2.5">
            <button
              type="button"
              onClick={onClose}
              className="px-3.5 py-1.5 rounded-md text-xs font-medium bg-white border border-border-subtle text-text-secondary hover:text-text-primary hover:bg-surface-subtle transition-colors"
            >
              Cancel
            </button>

            {isRunning && onStopSimulation && (
              <button
                type="button"
                onClick={onStopSimulation}
                disabled={isStopping}
                className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-md text-xs font-semibold bg-danger text-white hover:bg-danger-hover transition-colors shadow-subtle"
              >
                <Square className="w-3.5 h-3.5 fill-current" />
                <span>{isStopping ? 'Stopping...' : 'Stop Active Simulation'}</span>
              </button>
            )}

            <button
              type="submit"
              disabled={isLaunching || isRunning}
              className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-md text-xs font-semibold bg-brand text-white hover:bg-brand-hover disabled:opacity-50 disabled:cursor-not-allowed transition-colors shadow-subtle"
            >
              <Play className="w-3.5 h-3.5 fill-current" />
              <span>{isLaunching ? 'Injecting Traffic...' : 'Start Simulation'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
