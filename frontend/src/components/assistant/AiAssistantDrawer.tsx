import React, { useState, useEffect, useRef } from 'react';
import {
  X,
  Send,
  Sparkles,
  ShieldCheck,
  RotateCcw,
  Clock,
  Terminal,
  Database,
  ChevronRight,
} from 'lucide-react';
import { assistantApi } from '../../api';
import { AiMessage, AiSession, ChatInquiryResponse } from '../../types';

interface AiAssistantDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  activeTransactionId?: string;
  activeMerchantId?: string;
}

export const AiAssistantDrawer: React.FC<AiAssistantDrawerProps> = ({
  isOpen,
  onClose,
  activeTransactionId,
  activeMerchantId = 'mer_001',
}) => {
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<AiMessage[]>([]);
  const [inputQuery, setInputQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sessions, setSessions] = useState<AiSession[]>([]);
  const [showSessionsList, setShowSessionsList] = useState(false);

  const messagesEndRef = useRef<HTMLDivElement | null>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, loading]);

  useEffect(() => {
    if (isOpen) {
      loadSessions();
    }
  }, [isOpen, activeMerchantId]);

  const loadSessions = async () => {
    try {
      const data = await assistantApi.listSessions(activeMerchantId !== 'ALL' ? activeMerchantId : undefined);
      setSessions(data || []);
    } catch (err) {
      console.warn('Could not load past sessions', err);
    }
  };

  const handleSelectSession = async (sId: string) => {
    setSessionId(sId);
    setShowSessionsList(false);
    setLoading(true);
    setError(null);
    try {
      const msgs = await assistantApi.getMessages(sId);
      setMessages(msgs || []);
    } catch (err: any) {
      setError(err?.message || 'Failed to load session messages');
    } finally {
      setLoading(false);
    }
  };

  const handleNewSession = () => {
    setSessionId(null);
    setMessages([]);
    setError(null);
    setShowSessionsList(false);
  };

  const handleSendInquiry = async (messageText?: string) => {
    const textToSend = (messageText || inputQuery).trim();
    if (!textToSend || loading) return;

    setError(null);
    setInputQuery('');

    const tempUserMsg: AiMessage = {
      id: `temp_${Date.now()}`,
      sessionId: sessionId || '',
      role: 'user',
      content: textToSend,
      createdAt: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, tempUserMsg]);
    setLoading(true);

    try {
      const res: ChatInquiryResponse = await assistantApi.chat({
        sessionId: sessionId || undefined,
        merchantId: activeMerchantId !== 'ALL' ? activeMerchantId : 'mer_001',
        message: textToSend,
      });

      if (!sessionId && res.sessionId) {
        setSessionId(res.sessionId);
        loadSessions();
      }

      const assistantMsg: AiMessage = {
        id: res.messageId,
        sessionId: res.sessionId,
        role: 'assistant',
        content: res.answer,
        toolsUsed: res.toolsExecuted,
        sources: res.sources,
        createdAt: res.createdAt,
      };

      setMessages((prev) => [...prev, assistantMsg]);
    } catch (err: any) {
      setError(err?.message || 'Failed to execute investigation inquiry');
    } finally {
      setLoading(false);
    }
  };

  if (!isOpen) return null;

  const quickPrompts = [
    activeTransactionId
      ? `Why was transaction ${activeTransactionId} blocked?`
      : 'Why was transaction TX123 blocked?',
    'What signals caused this transaction to be risky?',
    'Why did fraud increase today?',
    'Show suspicious transactions related to device dev_test_001',
    'How is the current champion model performing?',
  ];

  const renderFormattedContent = (content: string) => {
    const lines = content.split('\n');
    return lines.map((line, idx) => {
      if (line.startsWith('### ')) {
        return (
          <h4 key={idx} className="font-semibold text-text-primary text-xs mt-2 mb-1">
            {line.replace('### ', '')}
          </h4>
        );
      }
      if (line.startsWith('#### ')) {
        return (
          <h5 key={idx} className="font-medium text-text-secondary text-xs mt-1.5 mb-0.5">
            {line.replace('#### ', '')}
          </h5>
        );
      }
      if (line.startsWith('- ')) {
        const bulletContent = line.replace('- ', '');
        return (
          <li key={idx} className="ml-4 list-disc text-xs text-text-secondary leading-relaxed">
            {renderInlineMarkdown(bulletContent)}
          </li>
        );
      }
      if (!line.trim()) {
        return <div key={idx} className="h-1.5" />;
      }
      return (
        <p key={idx} className="text-xs text-text-secondary leading-relaxed">
          {renderInlineMarkdown(line)}
        </p>
      );
    });
  };

  const renderInlineMarkdown = (text: string) => {
    const parts = text.split(/(\*\*.*?\*\*|`.*?`)/g);
    return parts.map((part, i) => {
      if (part.startsWith('**') && part.endsWith('**')) {
        return (
          <strong key={i} className="font-semibold text-text-primary">
            {part.slice(2, -2)}
          </strong>
        );
      }
      if (part.startsWith('`') && part.endsWith('`')) {
        return (
          <code
            key={i}
            className="px-1.5 py-0.5 rounded bg-surface-muted text-text-primary font-mono text-[11px] border border-border-subtle"
          >
            {part.slice(1, -1)}
          </code>
        );
      }
      return part;
    });
  };

  return (
    <div
      className="fixed top-0 right-0 bottom-0 w-[480px] max-w-full bg-surface border-l border-border-subtle shadow-modal z-50 flex flex-col transition-transform animate-in slide-in-from-right duration-200"
      role="dialog"
      aria-label="RiskShield Assistant"
    >
      {/* Header */}
      <div className="px-5 py-3.5 border-b border-border-subtle flex items-center justify-between bg-surface">
        <div className="flex items-center gap-2.5">
          <div className="w-7 h-7 rounded-md bg-brand-light flex items-center justify-center text-brand">
            <Sparkles className="w-4 h-4" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-sm font-semibold text-text-primary">Ask RiskShield</h3>
              <span className="text-[10px] font-mono font-medium px-1.5 py-0.2 rounded bg-success-light text-success border border-success-border">
                READ-ONLY
              </span>
            </div>
            <p className="text-[11px] text-text-muted">Grounded risk intelligence copilot</p>
          </div>
        </div>

        <div className="flex items-center gap-1.5">
          <button
            type="button"
            onClick={() => setShowSessionsList(!showSessionsList)}
            className="p-1.5 rounded-md text-text-muted hover:text-text-primary hover:bg-surface-subtle transition-colors"
            title="Session History"
          >
            <Clock className="w-4 h-4" />
          </button>
          <button
            type="button"
            onClick={handleNewSession}
            className="p-1.5 rounded-md text-text-muted hover:text-text-primary hover:bg-surface-subtle transition-colors"
            title="New Session"
          >
            <RotateCcw className="w-4 h-4" />
          </button>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-md text-text-muted hover:text-text-primary hover:bg-surface-subtle transition-colors"
            title="Close Drawer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Safety Notice */}
      <div className="px-5 py-2 bg-surface-subtle border-b border-border-subtle flex items-center gap-2 text-[11px] text-text-muted">
        <ShieldCheck className="w-3.5 h-3.5 text-success flex-shrink-0" />
        <span>Authoritative model grounding. Assistant does not execute payment mutations.</span>
      </div>

      {/* Sessions History Drawer */}
      {showSessionsList && (
        <div className="p-4 border-b border-border-subtle bg-surface-subtle/50 max-h-48 overflow-y-auto space-y-2">
          <div className="flex justify-between items-center mb-1">
            <span className="text-[11px] font-semibold text-text-muted uppercase tracking-wider">
              Past Inquiries ({sessions.length})
            </span>
            <button
              type="button"
              onClick={() => setShowSessionsList(false)}
              className="text-[11px] text-text-muted hover:underline"
            >
              Close
            </button>
          </div>
          {sessions.length === 0 ? (
            <p className="text-xs text-text-muted">No past sessions found.</p>
          ) : (
            <div className="flex flex-col gap-1.5">
              {sessions.map((s) => (
                <div
                  key={s.sessionId}
                  onClick={() => handleSelectSession(s.sessionId)}
                  className={`p-2.5 rounded-md border text-xs cursor-pointer transition-all flex items-center justify-between ${
                    s.sessionId === sessionId
                      ? 'border-brand bg-brand-light/60 font-medium'
                      : 'border-border-subtle bg-white hover:border-border-medium hover:bg-surface-subtle'
                  }`}
                >
                  <div className="truncate pr-2">
                    <div className="text-text-primary truncate">{s.title}</div>
                    <div className="text-[10px] text-text-muted">{new Date(s.updatedAt).toLocaleString()}</div>
                  </div>
                  <ChevronRight className="w-3.5 h-3.5 text-text-muted flex-shrink-0" />
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Messages Stream */}
      <div className="flex-1 overflow-y-auto p-5 space-y-4">
        {messages.length === 0 ? (
          <div className="h-full flex flex-col justify-center text-center px-4 py-8">
            <div className="w-10 h-10 rounded-full bg-brand-light text-brand flex items-center justify-center mx-auto mb-3">
              <Sparkles className="w-5 h-5" />
            </div>
            <h4 className="text-sm font-semibold text-text-primary mb-1">
              Ask Risk Intelligence
            </h4>
            <p className="text-xs text-text-muted max-w-xs mx-auto mb-6 leading-relaxed">
              Interrogate SHAP feature attributions, investigate transaction anomalies, or query champion model metrics.
            </p>

            <div className="text-left space-y-2">
              <div className="text-[11px] font-semibold text-text-muted uppercase tracking-wider mb-1">
                Suggested Inquiries:
              </div>
              {quickPrompts.map((prompt, idx) => (
                <button
                  key={idx}
                  type="button"
                  onClick={() => handleSendInquiry(prompt)}
                  className="w-full text-left p-2.5 rounded-lg border border-border-subtle bg-surface hover:border-brand hover:bg-brand-light/20 text-xs text-text-primary transition-all flex items-center gap-2 group"
                >
                  <Sparkles className="w-3.5 h-3.5 text-text-muted group-hover:text-brand" />
                  <span className="truncate">{prompt}</span>
                </button>
              ))}
            </div>
          </div>
        ) : (
          messages.map((msg, i) => {
            const isUser = msg.role === 'user';
            return (
              <div
                key={msg.id || i}
                className={`flex flex-col ${isUser ? 'items-end' : 'items-start'} gap-1`}
              >
                <div
                  className={`p-3.5 rounded-xl text-xs max-w-[90%] shadow-subtle ${
                    isUser
                      ? 'bg-brand text-white'
                      : 'bg-surface border border-border-subtle text-text-primary space-y-2'
                  }`}
                >
                  {isUser ? (
                    <div>{msg.content}</div>
                  ) : (
                    <div>
                      {/* Tool Execution Pills */}
                      {msg.toolsUsed && msg.toolsUsed.length > 0 && (
                        <div className="flex flex-wrap gap-1.5 pb-2 mb-2 border-b border-border-light">
                          <span className="text-[10px] text-text-muted self-center uppercase tracking-wider font-semibold">
                            Tools:
                          </span>
                          {msg.toolsUsed.map((tool, tIdx) => (
                            <span
                              key={tIdx}
                              className="inline-flex items-center gap-1 text-[10px] font-mono px-1.5 py-0.5 rounded bg-surface-muted text-text-secondary border border-border-subtle"
                            >
                              <Terminal className="w-2.5 h-2.5" /> {tool}
                            </span>
                          ))}
                        </div>
                      )}

                      {/* Formatted Content */}
                      <div className="space-y-1">{renderFormattedContent(msg.content)}</div>

                      {/* Verified Sources */}
                      {msg.sources && msg.sources.length > 0 && (
                        <div className="pt-2 mt-2 border-t border-border-light flex flex-wrap items-center gap-1.5 text-[11px]">
                          <Database className="w-3 h-3 text-success" />
                          <span className="font-medium text-success text-[10px] uppercase tracking-wider">
                            Sources:
                          </span>
                          {msg.sources.map((src, sIdx) => (
                            <span
                              key={sIdx}
                              className="font-mono text-[10px] px-1.5 py-0.2 rounded bg-success-light text-success border border-success-border"
                            >
                              {src}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                </div>
                <span className="text-[10px] text-text-muted font-mono px-1">
                  {new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </span>
              </div>
            );
          })
        )}

        {loading && (
          <div className="flex items-center gap-2 p-3 text-xs text-text-muted bg-surface-subtle rounded-lg border border-border-subtle max-w-xs">
            <Sparkles className="w-3.5 h-3.5 text-brand animate-spin" />
            <span>Analyzing risk signals & telemetry...</span>
          </div>
        )}

        {error && (
          <div className="p-3 text-xs text-danger bg-danger-light border border-danger-border rounded-lg">
            {error}
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Input Section */}
      <div className="p-4 border-t border-border-subtle bg-surface">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            handleSendInquiry();
          }}
          className="flex items-center gap-2"
        >
          <input
            type="text"
            className="flex-1 px-3 py-2 rounded-md border border-border-subtle bg-surface-subtle text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-brand focus:bg-white transition-all"
            placeholder="Ask about risk scores, feature attributions, or surges..."
            value={inputQuery}
            onChange={(e) => setInputQuery(e.target.value)}
            disabled={loading}
          />
          <button
            type="submit"
            disabled={!inputQuery.trim() || loading}
            className="p-2 rounded-md bg-brand text-white hover:bg-brand-hover disabled:opacity-40 disabled:cursor-not-allowed shadow-subtle transition-colors"
            title="Send Inquiry"
          >
            <Send className="w-3.5 h-3.5" />
          </button>
        </form>
      </div>
    </div>
  );
};
