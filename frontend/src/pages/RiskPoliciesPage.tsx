import React, { useEffect, useState } from 'react';
import {
  Play,
  RefreshCw,
  Edit2,
} from 'lucide-react';
import { policiesApi } from '../api';
import { RiskPolicy, PolicyEvaluationResult } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { Modal } from '../components/common/Modal';
import { LoadingState } from '../components/common/LoadingState';
import { ErrorState } from '../components/common/ErrorState';

export const RiskPoliciesPage: React.FC = () => {
  const [policies, setPolicies] = useState<RiskPolicy[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Simulation Tool State
  const [simulationScore, setSimulationScore] = useState(85.0);
  const [selectedPolicyId, setSelectedPolicyId] = useState<string>('pol_default_baseline');
  const [simulationResult, setSimulationResult] = useState<PolicyEvaluationResult | null>(null);
  const [simulating, setSimulating] = useState(false);

  // Edit / Details Modal State
  const [viewPolicy, setViewPolicy] = useState<RiskPolicy | null>(null);

  const fetchPolicies = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await policiesApi.list();
      setPolicies(res);
      if (res.length > 0 && !selectedPolicyId) {
        setSelectedPolicyId(res[0].id);
      }
    } catch (err: any) {
      setError(err?.message || 'Failed to query risk policies');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPolicies();
  }, []);

  const handleRunSimulation = async () => {
    if (!selectedPolicyId) return;
    setSimulating(true);
    try {
      const res = await policiesApi.evaluate(selectedPolicyId, simulationScore);
      setSimulationResult(res);
    } catch (err: any) {
      alert(`Policy evaluation failed: ${err?.message || err}`);
    } finally {
      setSimulating(false);
    }
  };

  if (loading) {
    return <LoadingState message="Loading deterministic policy configurations and threshold tables..." />;
  }

  if (error) {
    return <ErrorState message={error} onRetry={fetchPolicies} />;
  }

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-border-subtle">
        <div>
          <h1 className="text-2xl font-semibold text-text-primary tracking-tight">
            Risk Policies
          </h1>
          <p className="text-xs text-text-muted mt-0.5">
            Deterministic rule engine mapping continuous ML risk scores (0-100) into discrete actions (ALLOW, REVIEW, BLOCK).
          </p>
        </div>
        <button
          type="button"
          onClick={fetchPolicies}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium bg-surface border border-border-subtle text-text-secondary hover:text-text-primary hover:bg-surface-subtle shadow-subtle transition-colors"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          <span>Refresh</span>
        </button>
      </div>

      {/* Interactive Policy Simulation Playground */}
      <div className="bg-surface rounded-xl border border-border-subtle p-5 shadow-subtle flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-sm font-semibold text-text-primary flex items-center gap-2">
              <Play className="w-4 h-4 text-brand" />
              Deterministic Policy Evaluation Simulator
            </h2>
            <p className="text-xs text-text-muted mt-0.5">
              Test how a configured policy evaluates any hypothetical risk score against deterministic thresholds
            </p>
          </div>
          <span className="text-[10px] font-mono font-medium px-2 py-0.5 rounded bg-surface-muted text-text-muted border border-border-subtle">
            DETERMINISTIC / ZERO LLM
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 items-end pt-2 border-t border-border-light">
          {/* Policy Selector */}
          <div>
            <label className="block text-xs font-medium text-text-secondary mb-1">
              Select Active Policy:
            </label>
            <select
              className="w-full bg-surface-subtle border border-border-subtle rounded-md px-3 py-1.5 text-xs text-text-primary focus:outline-none focus:border-brand"
              value={selectedPolicyId}
              onChange={(e) => setSelectedPolicyId(e.target.value)}
            >
              {policies.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.policyName} ({p.version}) {p.merchantId ? `· ${p.merchantId}` : '· Global'}
                </option>
              ))}
            </select>
          </div>

          {/* Score Slider */}
          <div>
            <div className="flex justify-between items-baseline mb-1">
              <label className="text-xs font-medium text-text-secondary">Simulated Risk Score:</label>
              <span className="font-mono text-xs font-bold text-brand">
                {simulationScore.toFixed(1)} / 100
              </span>
            </div>
            <input
              type="range"
              min="0"
              max="100"
              step="0.5"
              value={simulationScore}
              onChange={(e) => setSimulationScore(parseFloat(e.target.value))}
              className="w-full accent-brand cursor-pointer"
            />
          </div>

          <button
            type="button"
            onClick={handleRunSimulation}
            disabled={simulating}
            className="inline-flex items-center justify-center gap-1.5 px-4 py-1.5 rounded-md text-xs font-semibold bg-brand text-white hover:bg-brand-hover disabled:opacity-50 shadow-subtle transition-colors"
          >
            <Play className="w-3.5 h-3.5 fill-current" />
            <span>{simulating ? 'Evaluating...' : 'Evaluate Decision'}</span>
          </button>
        </div>

        {/* Simulation Output Card */}
        {simulationResult && (
          <div className="p-4 rounded-lg bg-surface-subtle border border-border-subtle flex items-center justify-between gap-4 mt-2">
            <div>
              <div className="flex items-center gap-2 mb-1">
                <span className="text-xs text-text-muted font-medium">Evaluated Action:</span>
                <StatusBadge status={simulationResult.decision} />
              </div>
              <div className="text-xs font-medium text-text-primary">
                "{simulationResult.reason}"
              </div>
            </div>
            <div className="text-right text-[11px] font-mono text-text-muted whitespace-nowrap">
              Evaluated at {new Date(simulationResult.evaluatedAt).toLocaleTimeString()}
            </div>
          </div>
        )}
      </div>

      {/* Configured Policies List */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
        {policies.map((pol) => (
          <div
            key={pol.id}
            className="bg-surface rounded-xl border border-border-subtle p-5 shadow-subtle flex flex-col justify-between"
          >
            <div>
              <div className="flex items-start justify-between gap-2 mb-2">
                <div>
                  <h3 className="text-sm font-semibold text-text-primary">{pol.policyName}</h3>
                  <div className="text-[11px] font-mono text-text-muted">
                    {pol.id} · {pol.version}
                  </div>
                </div>
                <span
                  className={`text-[10px] font-mono font-medium px-2 py-0.5 rounded leading-none ${
                    pol.enabled ? 'bg-success-light text-success border border-success-border' : 'bg-surface-muted text-text-muted'
                  }`}
                >
                  {pol.enabled ? 'ACTIVE' : 'DISABLED'}
                </span>
              </div>

              <p className="text-xs text-text-secondary line-clamp-2 mb-4 leading-relaxed">
                {pol.description || 'Deterministic rule set configured for payment transactions.'}
              </p>

              {/* Thresholds Grid */}
              <div className="grid grid-cols-3 gap-2 bg-surface-subtle p-2.5 rounded-lg text-center mb-4 text-xs">
                <div>
                  <div className="text-[10px] font-semibold text-success uppercase">Allow</div>
                  <div className="font-mono font-bold text-text-primary mt-0.5">
                    0 - {pol.lowRiskThreshold}
                  </div>
                </div>
                <div>
                  <div className="text-[10px] font-semibold text-warning uppercase">Review</div>
                  <div className="font-mono font-bold text-text-primary mt-0.5">
                    {pol.lowRiskThreshold + 1} - {pol.blockThreshold - 1}
                  </div>
                </div>
                <div>
                  <div className="text-[10px] font-semibold text-danger uppercase">Block</div>
                  <div className="font-mono font-bold text-text-primary mt-0.5">
                    ≥ {pol.blockThreshold}
                  </div>
                </div>
              </div>

              <div className="flex justify-between text-[11px] text-text-muted mb-3">
                <span>Scope: {pol.merchantId ? `Merchant (${pol.merchantId})` : 'Global Baseline'}</span>
                <span>{pol.rules?.length || 0} discrete rules</span>
              </div>
            </div>

            <div className="flex gap-2 pt-2 border-t border-border-light">
              <button
                type="button"
                onClick={() => {
                  setSelectedPolicyId(pol.id);
                  window.scrollTo({ top: 0, behavior: 'smooth' });
                }}
                className="flex-1 inline-flex items-center justify-center gap-1 px-3 py-1.5 rounded-md text-xs font-medium bg-white border border-border-subtle text-text-secondary hover:text-text-primary hover:bg-surface-subtle shadow-subtle transition-colors"
              >
                <Play className="w-3 h-3" />
                <span>Test in Simulator</span>
              </button>
              <button
                type="button"
                onClick={() => setViewPolicy(pol)}
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-md text-xs font-medium bg-white border border-border-subtle text-text-secondary hover:text-text-primary hover:bg-surface-subtle shadow-subtle transition-colors"
              >
                <Edit2 className="w-3 h-3" />
                <span>Rules</span>
              </button>
            </div>
          </div>
        ))}
      </div>

      {/* Policy Rules Details Modal */}
      {viewPolicy && (
        <Modal
          isOpen={true}
          onClose={() => setViewPolicy(null)}
          title={`Policy Rules: ${viewPolicy.policyName}`}
          maxWidth="640px"
          footer={
            <button
              type="button"
              onClick={() => setViewPolicy(null)}
              className="px-3.5 py-1.5 rounded-md text-xs font-medium bg-white border border-border-subtle text-text-secondary hover:text-text-primary"
            >
              Close
            </button>
          }
        >
          <div className="space-y-4">
            <p className="text-xs text-text-muted">
              Discrete rules evaluated in strict priority order. If any rule triggers, its action takes precedence over the default threshold curve.
            </p>

            {viewPolicy.rules && viewPolicy.rules.length > 0 ? (
              <div className="border border-border-subtle rounded-lg overflow-hidden">
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-border-subtle bg-surface-subtle/50 text-[11px] font-medium text-text-muted uppercase">
                      <th className="py-2.5 px-3">Rule</th>
                      <th className="py-2.5 px-3">Condition</th>
                      <th className="py-2.5 px-3">Action</th>
                      <th className="py-2.5 px-3">Priority</th>
                      <th className="py-2.5 px-3 text-right">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border-light">
                    {viewPolicy.rules.map((rule, idx) => (
                      <tr key={idx}>
                        <td className="py-2 px-3 font-medium text-text-primary">{rule.ruleName}</td>
                        <td className="py-2 px-3 font-mono text-[11px] text-text-secondary">
                          {rule.field} {rule.operator} {rule.thresholdValue}
                        </td>
                        <td className="py-2 px-3">
                          <StatusBadge status={rule.action} size="sm" />
                        </td>
                        <td className="py-2 px-3 font-mono text-text-muted">#{rule.priority}</td>
                        <td className="py-2 px-3 text-right">
                          <span
                            className={`text-[10px] font-mono font-medium ${
                              rule.enabled ? 'text-success' : 'text-text-dim'
                            }`}
                          >
                            {rule.enabled ? 'ENABLED' : 'DISABLED'}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="p-6 text-center text-text-muted text-xs bg-surface-subtle rounded-lg">
                No discrete custom rules configured. Policy operates strictly on the score threshold curve (ALLOW: 0-{viewPolicy.lowRiskThreshold}, REVIEW: {viewPolicy.lowRiskThreshold + 1}-{viewPolicy.blockThreshold - 1}, BLOCK: {viewPolicy.blockThreshold}-100).
              </div>
            )}
          </div>
        </Modal>
      )}
    </div>
  );
};
