import React, { useState, useEffect } from 'react';
import {
  Play,
  Square,
  Sliders,
  CheckCircle2,
} from 'lucide-react';
import { SimulationStatusDto, StartSimulationRequest } from '../../types';
import { demoApi } from '../../api';
import { SimulationLaunchModal } from './SimulationLaunchModal';

interface DemoModeBannerProps {
  onSimulationTick?: () => void;
}

export const DemoModeBanner: React.FC<DemoModeBannerProps> = ({ onSimulationTick }) => {
  const [activeSim, setActiveSim] = useState<SimulationStatusDto | null>(null);
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
  const [isLaunching, setIsLaunching] = useState<boolean>(false);
  const [isStopping, setIsStopping] = useState<boolean>(false);

  const fetchActiveStatus = async () => {
    try {
      const res = await demoApi.getActive();
      setActiveSim(res);
      if (res && res.status === 'RUNNING') {
        onSimulationTick?.();
      }
    } catch {
      // Standby / offline
    }
  };

  useEffect(() => {
    fetchActiveStatus();
    const interval = setInterval(fetchActiveStatus, 1500);
    return () => clearInterval(interval);
  }, []);

  const handleLaunch = async (req: StartSimulationRequest) => {
    try {
      setIsLaunching(true);
      const started = await demoApi.start(req);
      setActiveSim(started);
      setIsModalOpen(false);
      onSimulationTick?.();
    } catch (err) {
      console.error('Failed to launch simulation', err);
    } finally {
      setIsLaunching(false);
    }
  };

  const handleStop = async () => {
    if (!activeSim?.id) return;
    try {
      setIsStopping(true);
      const stopped = await demoApi.stop(activeSim.id);
      setActiveSim(stopped);
      onSimulationTick?.();
    } catch (err) {
      console.error('Failed to stop simulation', err);
    } finally {
      setIsStopping(false);
    }
  };

  const isRunning = activeSim?.status === 'RUNNING';
  const hasCompleted = activeSim?.status === 'COMPLETED' || activeSim?.status === 'STOPPED';

  const fraudRatePercent =
    activeSim?.fraud_rate != null ? (activeSim.fraud_rate * 100).toFixed(1) : '0.0';

  return (
    <>
      {isRunning && activeSim ? (
        /* Slim, elegant persistent active demo banner */
        <div
          className="sticky top-14 z-20 w-full bg-danger-light border-b border-danger-border px-6 py-2 transition-all duration-200"
          role="alert"
          aria-live="assertive"
        >
          <div className="max-w-[1560px] mx-auto flex items-center justify-between gap-4 flex-wrap">
            {/* Left status & scenario */}
            <div className="flex items-center gap-3">
              <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-[11px] font-semibold uppercase tracking-wider bg-white text-danger border border-danger-border shadow-subtle">
                <span className="w-2 h-2 rounded-full bg-danger animate-pulse" />
                Demo Mode
              </span>

              <div className="flex items-center gap-2 text-xs text-text-primary">
                <span className="font-semibold text-text-primary">
                  {activeSim.mode_display_name || activeSim.mode}
                </span>
                <span className="text-text-muted hidden sm:inline">
                  — Simulated traffic running through live risk pipeline
                </span>
              </div>
            </div>

            {/* Middle metrics & Right actions */}
            <div className="flex items-center gap-3">
              <div className="hidden md:flex items-center gap-3 font-mono text-xs text-text-secondary bg-white/80 px-3 py-1 rounded border border-danger-border/60">
                <span>
                  {activeSim.generated_count}/{activeSim.target_count} txs
                </span>
                <span className="text-border-medium">|</span>
                <span className="text-success font-medium">{activeSim.allowed_count} Allow</span>
                <span className="text-warning font-medium">{activeSim.review_count} Review</span>
                <span className="text-danger font-semibold">{activeSim.blocked_count} Block</span>
                <span className="text-border-medium">|</span>
                <span>
                  Fraud Rate:{' '}
                  <strong className={Number(fraudRatePercent) > 20 ? 'text-danger' : 'text-text-primary'}>
                    {fraudRatePercent}%
                  </strong>
                </span>
              </div>

              <button
                type="button"
                onClick={() => setIsModalOpen(true)}
                className="px-2.5 py-1 text-xs font-medium rounded bg-white border border-border-subtle text-text-secondary hover:text-text-primary transition-colors"
                title="View simulation parameters"
              >
                <Sliders className="w-3.5 h-3.5 inline mr-1" />
                Details
              </button>

              {/* Unmistakable, prominent Exit / Stop Demo action */}
              <button
                type="button"
                onClick={handleStop}
                disabled={isStopping}
                id="exit-demo-mode-btn"
                className="inline-flex items-center gap-1.5 px-3.5 py-1 text-xs font-semibold rounded-md bg-danger text-white hover:bg-danger-hover shadow-subtle transition-all"
                title="Immediately stop simulation"
              >
                <Square className="w-3 h-3 fill-current" />
                <span>{isStopping ? 'Stopping...' : 'Stop Demo'}</span>
              </button>
            </div>
          </div>
        </div>
      ) : (
        /* Subtle idle launcher strip */
        <div className="sticky top-14 z-20 w-full bg-surface border-b border-border-subtle px-6 py-1.5 transition-all">
          <div className="max-w-[1560px] mx-auto flex items-center justify-between gap-4 flex-wrap">
            <div className="flex items-center gap-2.5 text-xs">
              <span className="w-1.5 h-1.5 rounded-full bg-text-dim" />
              <span className="font-medium text-text-secondary">Simulation Engine</span>
              <span className="text-text-muted text-[11px] hidden sm:inline">
                {hasCompleted && activeSim ? (
                  <span className="inline-flex items-center gap-1 text-success">
                    <CheckCircle2 className="w-3 h-3" />
                    Last Scenario: {activeSim.mode_display_name || activeSim.mode} ({activeSim.status} · {activeSim.generated_count} txs)
                  </span>
                ) : (
                  'Inject controlled fraud attacks (velocity spikes, emulator farms, proxy clusters) through the live pipeline.'
                )}
              </span>
            </div>

            <button
              type="button"
              onClick={() => setIsModalOpen(true)}
              id="launch-demo-scenario-btn"
              className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium rounded-md bg-brand-light text-brand hover:bg-brand hover:text-white border border-brand-border transition-all"
            >
              <Play className="w-3 h-3 fill-current" />
              <span>Launch Scenario</span>
            </button>
          </div>
        </div>
      )}

      {/* Structured Launch Modal */}
      <SimulationLaunchModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onLaunch={handleLaunch}
        isLaunching={isLaunching}
        activeSimulation={activeSim}
        onStopSimulation={handleStop}
        isStopping={isStopping}
      />
    </>
  );
};
