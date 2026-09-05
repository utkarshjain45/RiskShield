import { useState, useEffect } from 'react';
import { Sidebar, NavigationTab } from './components/layout/Sidebar';
import { Navbar } from './components/layout/Navbar';
import { OverviewDashboard } from './pages/OverviewDashboard';
import { TransactionsPage } from './pages/TransactionsPage';
import { InvestigationPage } from './pages/InvestigationPage';
import { FraudIncidentsPage } from './pages/FraudIncidentsPage';
import { ModelEvaluationPage } from './pages/ModelEvaluationPage';
import { AuditCenterPage } from './pages/AuditCenterPage';
import { RiskPoliciesPage } from './pages/RiskPoliciesPage';
import { AiAssistantDrawer } from './components/assistant/AiAssistantDrawer';
import { DemoModeBanner } from './components/demo/DemoModeBanner';
import { transactionsApi, incidentsApi } from './api';

export default function App() {
  const [currentTab, setCurrentTab] = useState<NavigationTab>('dashboard');
  const [selectedTxId, setSelectedTxId] = useState<string>('');
  const [openIncidentsCount, setOpenIncidentsCount] = useState<number>(0);
  const [activeMerchant, setActiveMerchant] = useState<string>('ALL');
  const [isAssistantOpen, setIsAssistantOpen] = useState<boolean>(false);
  const [refreshTrigger, setRefreshTrigger] = useState<number>(0);

  const handleSimulationTick = () => {
    setRefreshTrigger((prev) => prev + 1);
    incidentsApi
      .list({ status: 'OPEN', size: 1 })
      .then((res) => {
        setOpenIncidentsCount(res.totalElements || 0);
      })
      .catch(() => {});
  };

  // Load initial active transaction ID if needed
  useEffect(() => {
    transactionsApi
      .list({ size: 1 })
      .then((res) => {
        if (res.content && res.content.length > 0 && !selectedTxId) {
          setSelectedTxId(res.content[0].id);
        }
      })
      .catch(() => {});

    incidentsApi
      .list({ status: 'OPEN', size: 1 })
      .then((res) => {
        setOpenIncidentsCount(res.totalElements || 0);
      })
      .catch(() => {});
  }, [refreshTrigger]);

  const handleInvestigateTransaction = (txId: string) => {
    setSelectedTxId(txId);
    setCurrentTab('investigation');
  };

  return (
    <div className="flex min-h-screen w-full bg-canvas text-text-primary antialiased">
      {/* Sidebar Navigation */}
      <Sidebar
        currentTab={currentTab}
        onSelectTab={(tab) => setCurrentTab(tab)}
        openIncidentsCount={openIncidentsCount}
      />

      {/* Main Workspace Area */}
      <div className="flex-1 ml-60 flex flex-col min-w-0">
        <Navbar
          activeMerchant={activeMerchant}
          onMerchantChange={setActiveMerchant}
          onOpenAssistant={() => setIsAssistantOpen(true)}
        />

        {/* Demo Simulation Engine Banner */}
        <DemoModeBanner onSimulationTick={handleSimulationTick} />

        <main className="flex-1 max-w-[1560px] w-full mx-auto px-6 py-7 lg:px-8">
          {currentTab === 'dashboard' && (
            <OverviewDashboard
              onNavigateToTransactions={() => setCurrentTab('transactions')}
              onNavigateToIncidents={() => setCurrentTab('incidents')}
            />
          )}

          {currentTab === 'transactions' && (
            <TransactionsPage
              onInvestigateTransaction={handleInvestigateTransaction}
            />
          )}

          {currentTab === 'investigation' && (
            <InvestigationPage
              transactionId={selectedTxId || 'tx_default'}
              onBack={() => setCurrentTab('transactions')}
              onSelectTransaction={(id) => setSelectedTxId(id)}
              onOpenAssistant={(txId) => {
                setSelectedTxId(txId);
                setIsAssistantOpen(true);
              }}
            />
          )}

          {currentTab === 'incidents' && <FraudIncidentsPage />}

          {currentTab === 'model' && <ModelEvaluationPage />}

          {currentTab === 'audit' && <AuditCenterPage />}

          {currentTab === 'policies' && <RiskPoliciesPage />}
        </main>
      </div>

      {/* AI Investigation Assistant Drawer */}
      <AiAssistantDrawer
        isOpen={isAssistantOpen}
        onClose={() => setIsAssistantOpen(false)}
        activeTransactionId={selectedTxId}
        activeMerchantId={activeMerchant}
      />
    </div>
  );
}

