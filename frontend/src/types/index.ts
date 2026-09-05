export type RiskDecision = 'ALLOW' | 'REVIEW' | 'BLOCK';
export type IncidentSeverity = 'NORMAL' | 'ELEVATED' | 'CRITICAL';
export type IncidentStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp?: string;
  correlationId?: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface DashboardMetrics {
  today_transactions: number;
  suspicious_transactions: number;
  blocked_transactions: number;
  review_queue: number;
  fraud_exposure_paise: number;
  fraud_exposure_inr: number;
  prevented_loss_paise: number;
  prevented_loss_inr: number;
  fraud_rate: number;
  critical_incidents: number;
}

export interface TimePoint {
  time: string;
  fraud_rate: number;
  total: number;
  fraud: number;
}

export interface VolumePoint {
  time: string;
  volume: number;
  amount_inr: number;
}

export interface RiskBucket {
  range: string;
  count: number;
}

export interface DecisionShare {
  name: string;
  count: number;
  percentage: number;
}

export interface AnalyticsCharts {
  fraud_rate_over_time: TimePoint[];
  transaction_volume: VolumePoint[];
  risk_distribution: RiskBucket[];
  decision_distribution: DecisionShare[];
}

export interface Transaction {
  id: string;
  merchantId?: string;
  merchantName?: string;
  customerId?: string;
  deviceId?: string;
  ipAddress?: string;
  amountInPaise: number;
  amountInInr: number;
  currency: string;
  paymentMethod: string;
  paymentStatus: string;
  customerAccountAgeDays?: number;
  isNewDevice: boolean;
  isNewIp: boolean;
  riskScore?: number | null;
  decision?: RiskDecision | null;
  createdAt: string;
}

export interface ContributingFeature {
  feature_name: string;
  display_name: string;
  impact: number;
  formatted_impact: string;
  direction: 'INCREASES_RISK' | 'DECREASES_RISK';
  description: string;
}

export interface RiskExplanation {
  transaction_id: string;
  risk_score: number;
  fraud_probability: number;
  decision: RiskDecision;
  model_version: string;
  top_contributing_features: ContributingFeature[];
  human_readable_explanation: string;
  evaluated_at: string;
}

export interface AuditEvent {
  id: number;
  audit_id?: string;
  auditId?: string;
  event_type?: string;
  eventType?: string;
  actor_type?: string;
  actorType?: string;
  actor_id?: string;
  actorId: string;
  merchant_id?: string;
  merchantId?: string;
  transaction_id?: string;
  transactionId?: string;
  correlation_id?: string;
  correlationId?: string;
  service?: string;
  payload_hash?: string;
  payloadHash?: string;
  metadata?: string;
  entityType?: string;
  entity_type?: string;
  entityId?: string;
  entity_id?: string;
  action: string;
  details?: string;
  createdAt: string;
  created_at?: string;
  timestamp?: string;
}

export interface TransactionTimeline {
  transaction_id: string;
  merchant_id?: string;
  event_count: number;
  started_at: string;
  last_event_at: string;
  events: AuditEvent[];
}

export interface TransactionInvestigation {
  transaction: Transaction;
  explanation?: RiskExplanation | null;
  customer_summary?: {
    customer_id: string;
    email: string;
    account_age_days: number;
    total_transactions: number;
    average_amount_inr: number;
    risk_segment: string;
  };
  device_summary?: {
    device_id: string;
    total_transactions: number;
    distinct_accounts: number;
    is_new_device: boolean;
  };
  ip_summary?: {
    ip_address: string;
    total_transactions: number;
    distinct_accounts: number;
    is_new_ip: boolean;
  };
  related_transactions?: Transaction[];
  audit_trail?: AuditEvent[];
}

export interface FraudIncident {
  incident_id: string;
  merchant_id: string;
  merchant_name?: string;
  severity: IncidentSeverity;
  detected_at: string;
  baseline_rate: number;
  current_rate: number;
  percentage_increase: number;
  affected_transactions: number;
  estimated_exposure: number; // in paise
  status: IncidentStatus;
  time_window: string;
  z_score?: number;
  explanation_summary?: string;
  acknowledged_at?: string;
  resolved_at?: string;
}

export interface ModelEvaluation {
  model_name: string;
  model_version: string;
  framework: string;
  optimal_threshold: number;
  precision: number;
  recall: number;
  f1: number;
  pr_auc: number;
  roc_auc: number;
  false_positive_rate: number;
  confusion_matrix: {
    true_negatives: number;
    false_positives: number;
    false_negatives: number;
    true_positives: number;
  };
  monetary_impact: {
    fn_count: number;
    fn_cost_inr: number;
    fp_count: number;
    fp_cost_inr: number;
    prevented_fraud_inr?: number;
    total_cost_inr: number;
  };
  threshold_analysis?: Array<{
    threshold: number;
    precision: number;
    recall: number;
    fpr: number;
    f1: number;
  }>;
}

export interface ConfusionMatrix {
  true_negatives: number;
  false_positives: number;
  false_negatives: number;
  true_positives: number;
}

export interface CurrentEvaluationResponse {
  evaluation_id: string;
  model_version: string;
  dataset_version: string;
  test_set_version: string;
  evaluation_label: string;
  created_at: string;
  dataset_size: number;
  fraud_count: number;
  non_fraud_count: number;
  precision: number;
  recall: number;
  f1: number;
  roc_auc: number;
  pr_auc: number;
  false_positive_rate: number;
  false_negative_rate: number;
  confusion_matrix: ConfusionMatrix;
  false_positive_cost: number;
  false_negative_cost: number;
  estimated_prevented_loss: number;
}

export interface ThresholdEvaluationDto {
  threshold: number;
  precision: number;
  recall: number;
  f1: number;
  roc_auc: number;
  pr_auc: number;
  false_positive_rate: number;
  false_negative_rate: number;
  confusion_matrix: ConfusionMatrix;
  false_positive_cost: number;
  false_negative_cost: number;
  estimated_prevented_loss: number;
}

export interface PolicyRule {
  id?: string;
  ruleName: string;
  field: string;
  operator: string;
  thresholdValue: string;
  action: RiskDecision;
  priority: number;
  enabled: boolean;
}

export interface RiskPolicy {
  id: string;
  policyName: string;
  version: string;
  description?: string;
  merchantId?: string;
  merchantName?: string;
  lowRiskThreshold: number;
  reviewThreshold: number;
  blockThreshold: number;
  falsePositiveWeight?: number;
  enabled: boolean;
  rules?: PolicyRule[];
  createdAt: string;
  updatedAt?: string;
}

export interface PolicyEvaluationResult {
  decision: RiskDecision;
  riskScore: number;
  policyId: string;
  policyVersion: string;
  policyName: string;
  reason: string;
  evaluatedAt: string;
}

export interface AiMessage {
  id: string;
  sessionId: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  toolsUsed?: string[];
  sources?: string[];
  createdAt: string;
}

export interface AiSession {
  sessionId: string;
  merchantId?: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  messageCount?: number;
}

export interface ChatInquiryRequest {
  sessionId?: string;
  merchantId?: string;
  message: string;
}

export interface ChatInquiryResponse {
  sessionId: string;
  messageId: string;
  answer: string;
  toolsExecuted: string[];
  sources: string[];
  createdAt: string;
}

export type SimulationMode =
  | 'NORMAL_TRAFFIC'
  | 'VELOCITY_ATTACK'
  | 'DEVICE_ABUSE'
  | 'IP_CLUSTER_ATTACK'
  | 'AMOUNT_ANOMALY'
  | 'COORDINATED_FRAUD_SPIKE';

export type SimulationStatus = 'IDLE' | 'RUNNING' | 'STOPPED' | 'COMPLETED' | 'FAILED';

export interface SimulationStatusDto {
  id: string;
  mode: SimulationMode;
  mode_display_name?: string;
  mode_description?: string;
  mode_expected_outcome?: string;
  status: SimulationStatus;
  merchant_id: string;
  target_count: number;
  generated_count: number;
  allowed_count: number;
  review_count: number;
  blocked_count: number;
  fraud_rate: number;
  interval_ms: number;
  active_incident_id?: string;
  summary?: string;
  started_at: string;
  stopped_at?: string;
}

export interface SimulationModeDto {
  mode: SimulationMode;
  display_name: string;
  description: string;
  expected_outcome: string;
}

export interface StartSimulationRequest {
  mode: SimulationMode;
  merchant_id?: string;
  transaction_count?: number;
  interval_ms?: number;
}
