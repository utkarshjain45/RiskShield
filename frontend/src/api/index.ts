import { apiClient } from './client';
import {
  DashboardMetrics,
  AnalyticsCharts,
  ModelEvaluation,
  CurrentEvaluationResponse,
  ThresholdEvaluationDto,
  Transaction,
  TransactionInvestigation,
  FraudIncident,
  AuditEvent,
  TransactionTimeline,
  RiskPolicy,
  PolicyEvaluationResult,
  Page,
  IncidentStatus,
  AiMessage,
  AiSession,
  ChatInquiryRequest,
  ChatInquiryResponse,
  SimulationStatusDto,
  SimulationModeDto,
  StartSimulationRequest,
} from '../types';

export const modelEvaluationApi = {
  getCurrent: () => apiClient<CurrentEvaluationResponse>('/model/evaluation/current'),
  getThresholds: () => apiClient<ThresholdEvaluationDto[]>('/model/evaluation/thresholds'),
};

export const assistantApi = {
  chat: (payload: ChatInquiryRequest) =>
    apiClient<ChatInquiryResponse>('/assistant/chat', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  listSessions: (merchantId?: string) =>
    apiClient<AiSession[]>('/assistant/sessions', {
      params: merchantId ? { merchantId } : undefined,
    }),

  getMessages: (sessionId: string) =>
    apiClient<AiMessage[]>(`/assistant/sessions/${sessionId}/messages`),
};

export const analyticsApi = {
  getDashboard: () => apiClient<DashboardMetrics>('/analytics/dashboard'),
  getCharts: () => apiClient<AnalyticsCharts>('/analytics/charts'),
  getModelEvaluation: () => apiClient<ModelEvaluation>('/analytics/model-evaluation'),
  getSummary: () => apiClient<any>('/analytics/summary'),
};

export const transactionsApi = {
  list: (params?: {
    search?: string;
    paymentStatus?: string;
    merchantId?: string;
    decision?: string;
    page?: number;
    size?: number;
  }) => apiClient<Page<Transaction>>('/transactions', { params }),

  getById: (id: string) => apiClient<Transaction>(`/transactions/${id}`),

  getInvestigation: (id: string) =>
    apiClient<TransactionInvestigation>(`/transactions/${id}/investigation`),

  assessRisk: (id: string) =>
    apiClient<any>(`/risk/assess/${id}`, { method: 'POST' }),

  getExplanation: (id: string) =>
    apiClient<any>(`/risk/assessments/${id}/explanation`),
};

export const incidentsApi = {
  list: (params?: {
    merchantId?: string;
    status?: IncidentStatus;
    page?: number;
    size?: number;
  }) => apiClient<Page<FraudIncident>>('/incidents', { params }),

  getById: (id: string) => apiClient<FraudIncident>(`/incidents/${id}`),

  acknowledge: (id: string, actorId: string = 'RISK_INVESTIGATOR') =>
    apiClient<FraudIncident>(`/incidents/${id}/acknowledge`, {
      method: 'POST',
      body: JSON.stringify({ actor_id: actorId }),
    }),

  resolve: (id: string, actorId: string = 'RISK_INVESTIGATOR', notes: string = 'Investigated and resolved') =>
    apiClient<FraudIncident>(`/incidents/${id}/resolve`, {
      method: 'POST',
      body: JSON.stringify({ actor_id: actorId, notes }),
    }),

  scanSpikes: (merchantId: string, window: string = '15m') =>
    apiClient<any>(`/incidents/scan/${merchantId}`, {
      method: 'POST',
      params: { window },
    }),

  getMerchantMetrics: (merchantId: string) =>
    apiClient<any>(`/incidents/metrics/${merchantId}`),
};

export const policiesApi = {
  list: (params?: { merchantId?: string; enabled?: boolean }) =>
    apiClient<RiskPolicy[]>('/policies', { params }),

  getById: (id: string) => apiClient<RiskPolicy>(`/policies/${id}`),

  update: (id: string, payload: Partial<RiskPolicy>) =>
    apiClient<RiskPolicy>(`/policies/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  evaluate: (id: string, riskScore: number, transactionId?: string) =>
    apiClient<PolicyEvaluationResult>(`/policies/${id}/evaluate`, {
      method: 'POST',
      body: JSON.stringify({ riskScore, transactionId }),
    }),
};

export const auditApi = {
  list: (params?: {
    merchantId?: string;
    eventType?: string;
    actorId?: string;
    service?: string;
    entityType?: string;
    action?: string;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  }) => apiClient<Page<AuditEvent>>('/audit', { params }),

  getByTransaction: (transactionId: string) =>
    apiClient<AuditEvent[]>(`/audit/${transactionId}`),

  getTimeline: (transactionId: string) =>
    apiClient<TransactionTimeline>(`/audit/${transactionId}/timeline`),

  getByIncident: (incidentId: string) =>
    apiClient<AuditEvent[]>(`/audit/incidents/${incidentId}`),
};

export const healthApi = {
  getBackendHealth: () => apiClient<any>('/health'),
  getMlHealth: () => fetch('http://localhost:8000/health').then((res) => (res.ok ? res.json() : null)),
};

export const demoApi = {
  start: (req: StartSimulationRequest) =>
    apiClient<SimulationStatusDto>('/demo/simulations/start', {
      method: 'POST',
      body: JSON.stringify(req),
    }),

  stop: (id: string) =>
    apiClient<SimulationStatusDto>(`/demo/simulations/${id}/stop`, {
      method: 'POST',
    }),

  getById: (id: string) =>
    apiClient<SimulationStatusDto>(`/demo/simulations/${id}`),

  getActive: () =>
    apiClient<SimulationStatusDto | null>('/demo/simulations/active'),

  getModes: () =>
    apiClient<SimulationModeDto[]>('/demo/simulations/modes'),
};
