// AI Provider Types
export type AIProvider = 'openrouter' | 'openai' | 'anthropic' | 'groq' | 'gemini' | 'ollama' | 'custom';

export interface ProviderConfig {
  id: AIProvider;
  name: string;
  baseUrl: string;
  apiKey?: string;
  models: string[];
  defaultModel: string;
}

export interface ProviderSettings {
  provider: AIProvider;
  apiKey: string;
  baseUrl?: string;
  model: string;
}

// Message Types
export interface Message {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: number;
  citations?: Citation[];
}

export interface Citation {
  url: string;
  title: string;
  snippet: string;
}

// Memory Types
export interface Memory {
  id: string;
  type: 'preference' | 'website' | 'prompt' | 'project' | 'note' | 'instruction' | 'research';
  content: string;
  metadata?: Record<string, unknown>;
  createdAt: number;
  updatedAt: number;
}

// Password Vault Types
export interface PasswordEntry {
  id: string;
  url: string;
  username: string;
  password: string;
  notes?: string;
  createdAt: number;
  updatedAt: number;
  lastUsed?: number;
}

export interface VaultState {
  isLocked: boolean;
  entries: PasswordEntry[];
  lastActivity: number;
}

// Agent Action Types
export interface AgentAction {
  type: 'click' | 'fill' | 'navigate' | 'openTab' | 'closeTab' | 'search' | 'extract' | 'download';
  target?: string;
  value?: string;
  description: string;
}

export interface AgentTask {
  id: string;
  goal: string;
  actions: AgentAction[];
  status: 'pending' | 'running' | 'completed' | 'failed';
  requiresApproval: boolean;
}

// Settings Types
export interface ExtensionSettings {
  theme: 'light' | 'dark' | 'system';
  providers: ProviderSettings[];
  activeProvider: AIProvider;
  memoryEnabled: boolean;
  agentPermissions: {
    click: boolean;
    fill: boolean;
    navigate: boolean;
    openTab: boolean;
    closeTab: boolean;
    search: boolean;
    extract: boolean;
    download: boolean;
  };
  voiceEnabled: boolean;
  autoLockMinutes: number;
}

// Stream Response Types
export interface StreamChunk {
  content: string;
  done: boolean;
  error?: string;
}

// Page Context Types
export interface PageContext {
  url: string;
  title: string;
  content: string;
  selection?: string;
  screenshot?: string;
}
