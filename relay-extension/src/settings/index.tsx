import React, { useState, useEffect } from 'react';
import ReactDOM from 'react-dom/client';
import { ExtensionSettings, AIProvider } from '@/types';
import { getSettings, saveSettings, hasMasterPassword, setMasterPassword, clearAllData } from '@/utils/storage';
import { getAvailableModels, getDefaultModel, getBaseUrl } from '@/services/aiProvider';

const PROVIDERS: { id: AIProvider; name: string; description: string }[] = [
  { id: 'openrouter', name: 'OpenRouter', description: 'Access to multiple AI models through one API' },
  { id: 'openai', name: 'OpenAI', description: 'GPT-4, GPT-3.5 Turbo and more' },
  { id: 'anthropic', name: 'Anthropic', description: 'Claude 3.5 Sonnet, Claude 3 Opus' },
  { id: 'groq', name: 'Groq', description: 'Fast inference for open-source models' },
  { id: 'gemini', name: 'Google Gemini', description: 'Gemini Pro, Gemini Ultra' },
  { id: 'ollama', name: 'Ollama', description: 'Run models locally on your machine' },
  { id: 'custom', name: 'Custom Endpoint', description: 'Any OpenAI-compatible API' },
];

function SettingsApp() {
  const [settings, setSettings] = useState<ExtensionSettings | null>(null);
  const [activeTab, setActiveTab] = useState('providers');
  const [apiKey, setApiKey] = useState('');
  const [customBaseUrl, setCustomBaseUrl] = useState('');
  const [masterPassword, setMasterPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [hasVault, setHasVault] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    loadSettings();
    checkVault();
  }, []);

  const loadSettings = async () => {
    const savedSettings = await getSettings();
    setSettings(savedSettings);
    
    // Load current provider's API key
    const provider = savedSettings.providers.find(p => p.provider === savedSettings.activeProvider);
    if (provider) {
      setApiKey(provider.apiKey || '');
      setCustomBaseUrl(provider.baseUrl || '');
    }
  };

  const checkVault = async () => {
    const vaultExists = await hasMasterPassword();
    setHasVault(vaultExists);
  };

  const handleSaveProvider = async () => {
    if (!settings) return;

    const newProviders = settings.providers.filter(p => p.provider !== settings.activeProvider);
    const activeProviderConfig = settings.providers.find(p => p.provider === settings.activeProvider);
    
    const updatedProvider = {
      provider: settings.activeProvider,
      apiKey,
      baseUrl: settings.activeProvider === 'custom' ? customBaseUrl : undefined,
      model: activeProviderConfig?.model || getDefaultModel(settings.activeProvider),
    };

    await saveSettings({
      ...settings,
      providers: [...newProviders, updatedProvider],
    });

    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };

  const handleSwitchProvider = (provider: AIProvider) => {
    if (!settings) return;
    
    const providerConfig = settings.providers.find(p => p.provider === provider);
    setApiKey(providerConfig?.apiKey || '');
    setCustomBaseUrl(providerConfig?.baseUrl || '');
    
    setSettings({
      ...settings,
      activeProvider: provider,
    });
  };

  const handleSetMasterPassword = async () => {
    if (masterPassword.length < 8) {
      alert('Password must be at least 8 characters');
      return;
    }
    if (masterPassword !== confirmPassword) {
      alert('Passwords do not match');
      return;
    }
    
    await setMasterPassword(masterPassword);
    setHasVault(true);
    setMasterPassword('');
    setConfirmPassword('');
    alert('Master password set successfully!');
  };

  const handleClearData = async () => {
    if (confirm('Are you sure? This will delete all settings, memories, and passwords.')) {
      await clearAllData();
      window.location.reload();
    }
  };

  if (!settings) {
    return <div className="p-8 text-center">Loading...</div>;
  }

  return (
    <div className={`min-h-screen ${settings.theme === 'dark' ? 'dark bg-gray-900' : 'bg-gray-50'}`}>
      <div className="max-w-4xl mx-auto p-8">
        {/* Header */}
        <header className="mb-8">
          <div className="flex items-center gap-3 mb-2">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary-500 to-primary-700 flex items-center justify-center text-white">
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
              </svg>
            </div>
            <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Relay Settings</h1>
          </div>
          <p className="text-gray-600 dark:text-gray-400">Configure your AI assistant</p>
        </header>

        {/* Tabs */}
        <div className="flex gap-2 mb-6 border-b border-gray-200 dark:border-gray-700">
          {[
            { id: 'providers', label: 'AI Providers' },
            { id: 'appearance', label: 'Appearance' },
            { id: 'memory', label: 'Memory' },
            { id: 'agent', label: 'Agent' },
            { id: 'vault', label: 'Password Vault' },
            { id: 'advanced', label: 'Advanced' },
          ].map(tab => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`px-4 py-2 text-sm font-medium rounded-t-lg transition-colors ${
                activeTab === tab.id
                  ? 'bg-white dark:bg-gray-800 text-primary-600 dark:text-primary-400 border-t border-l border-r border-gray-200 dark:border-gray-700'
                  : 'text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Tab Content */}
        <div className="bg-white dark:bg-gray-800 rounded-b-lg rounded-tr-lg shadow-sm p-6">
          {activeTab === 'providers' && (
            <div className="space-y-6">
              <div>
                <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Select AI Provider</h2>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {PROVIDERS.map(provider => (
                    <button
                      key={provider.id}
                      onClick={() => handleSwitchProvider(provider.id)}
                      className={`p-4 rounded-xl border-2 text-left transition-all ${
                        settings.activeProvider === provider.id
                          ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                          : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
                      }`}
                    >
                      <div className="font-medium text-gray-900 dark:text-white">{provider.name}</div>
                      <div className="text-sm text-gray-500 dark:text-gray-400">{provider.description}</div>
                    </button>
                  ))}
                </div>
              </div>

              <div className="space-y-4 pt-4 border-t border-gray-200 dark:border-gray-700">
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    API Key
                  </label>
                  <input
                    type="password"
                    value={apiKey}
                    onChange={e => setApiKey(e.target.value)}
                    placeholder="Enter your API key"
                    className="w-full px-4 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  />
                </div>

                {settings.activeProvider === 'custom' && (
                  <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                      Base URL
                    </label>
                    <input
                      type="url"
                      value={customBaseUrl}
                      onChange={e => setCustomBaseUrl(e.target.value)}
                      placeholder="https://api.example.com/v1"
                      className="w-full px-4 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                    />
                  </div>
                )}

                <div className="flex items-center gap-3">
                  <button
                    onClick={handleSaveProvider}
                    className="px-6 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600 transition-colors"
                  >
                    Save Provider
                  </button>
                  {saved && (
                    <span className="text-green-600 dark:text-green-400 text-sm">Saved!</span>
                  )}
                </div>

                <div className="pt-4 border-t border-gray-200 dark:border-gray-700">
                  <h3 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Available Models</h3>
                  <div className="flex flex-wrap gap-2">
                    {getAvailableModels(settings.activeProvider).map(model => (
                      <span
                        key={model}
                        className="px-3 py-1 bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-full text-sm"
                      >
                        {model}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'appearance' && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Theme</h2>
              <div className="flex gap-3">
                {(['light', 'dark', 'system'] as const).map(theme => (
                  <button
                    key={theme}
                    onClick={() => saveSettings({ ...settings, theme })}
                    className={`px-4 py-2 rounded-lg border-2 capitalize ${
                      settings.theme === theme
                        ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                        : 'border-gray-200 dark:border-gray-700'
                    }`}
                  >
                    {theme}
                  </button>
                ))}
              </div>
            </div>
          )}

          {activeTab === 'memory' && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Browser Memory</h2>
              <label className="flex items-center gap-3">
                <input
                  type="checkbox"
                  checked={settings.memoryEnabled}
                  onChange={e => saveSettings({ ...settings, memoryEnabled: e.target.checked })}
                  className="w-4 h-4 text-primary-500 rounded"
                />
                <span className="text-gray-700 dark:text-gray-300">Enable memory to remember conversations and preferences</span>
              </label>
            </div>
          )}

          {activeTab === 'agent' && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Agent Permissions</h2>
              <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
                Grant Relay permission to perform actions on webpages. Each action requires explicit approval.
              </p>
              <div className="grid grid-cols-2 gap-3">
                {(Object.keys(settings.agentPermissions) as Array<keyof typeof settings.agentPermissions>).map(permission => (
                  <label key={permission} className="flex items-center gap-3 p-3 rounded-lg border border-gray-200 dark:border-gray-700">
                    <input
                      type="checkbox"
                      checked={settings.agentPermissions[permission]}
                      onChange={e => saveSettings({
                        ...settings,
                        agentPermissions: { ...settings.agentPermissions, [permission]: e.target.checked }
                      })}
                      className="w-4 h-4 text-primary-500 rounded"
                    />
                    <span className="text-gray-700 dark:text-gray-300 capitalize">{permission}</span>
                  </label>
                ))}
              </div>
            </div>
          )}

          {activeTab === 'vault' && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Password Vault</h2>
              {!hasVault ? (
                <div className="space-y-3">
                  <p className="text-sm text-gray-500 dark:text-gray-400">
                    Set a master password to encrypt your stored passwords locally.
                  </p>
                  <input
                    type="password"
                    value={masterPassword}
                    onChange={e => setMasterPassword(e.target.value)}
                    placeholder="Master password (min 8 characters)"
                    className="w-full px-4 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                  />
                  <input
                    type="password"
                    value={confirmPassword}
                    onChange={e => setConfirmPassword(e.target.value)}
                    placeholder="Confirm password"
                    className="w-full px-4 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                  />
                  <button
                    onClick={handleSetMasterPassword}
                    className="px-6 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600"
                  >
                    Set Master Password
                  </button>
                </div>
              ) : (
                <div className="text-green-600 dark:text-green-400">
                  ✓ Vault is secured with a master password
                </div>
              )}
            </div>
          )}

          {activeTab === 'advanced' && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold text-gray-900 dark:text-white text-red-600">Danger Zone</h2>
              <button
                onClick={handleClearData}
                className="px-6 py-2 bg-red-500 text-white rounded-lg hover:bg-red-600"
              >
                Clear All Data
              </button>
              <p className="text-sm text-gray-500 dark:text-gray-400">
                This will permanently delete all settings, memories, and stored passwords.
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <SettingsApp />
  </React.StrictMode>
);
