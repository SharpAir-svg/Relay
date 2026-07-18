import React, { useState, useEffect, useRef } from 'react';
import { Message, ExtensionSettings } from '@/types';
import { getSettings, saveMemory } from '@/utils/storage';
import { chatStream, ProviderOptions } from '@/services/aiProvider';

const Icons = {
  Send: () => <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" /></svg>,
  Settings: () => <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" /><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" /></svg>,
  Close: () => <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>,
  Mic: () => <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" /></svg>,
  Bot: () => <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" /></svg>,
  User: () => <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" /></svg>,
  Summarize: () => <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" /></svg>,
  Explain: () => <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" /></svg>,
  Translate: () => <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 5h12M9 3v2m1.048 9.5A18.022 18.022 0 016.412 9m6.088 9h7M11 21l5-10 5 10M12.751 5C11.783 10.77 8.07 15.61 3 18.129" /></svg>,
  Rewrite: () => <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" /></svg>,
};

const QUICK_ACTIONS = [
  { id: 'summarize', label: 'Summarize', icon: Icons.Summarize, prompt: 'Summarize this webpage clearly.' },
  { id: 'explain', label: 'Explain', icon: Icons.Explain, prompt: 'Explain this content.' },
  { id: 'translate', label: 'Translate', icon: Icons.Translate, prompt: 'Translate to English.' },
  { id: 'rewrite', label: 'Rewrite', icon: Icons.Rewrite, prompt: 'Rewrite for clarity.' },
];

export default function App() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [settings, setSettings] = useState<ExtensionSettings | null>(null);
  const [isListening, setIsListening] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => { loadSettings(); }, []);
  useEffect(() => { messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages]);

  const loadSettings = async () => {
    const s = await getSettings();
    setSettings(s);
    setMessages([{ id: 'welcome', role: 'assistant', content: "Hello! I'm Relay. How can I help?", timestamp: Date.now() }]);
  };

  const getPageContext = async () => {
    return new Promise((resolve) => {
      chrome.tabs.query({ active: true, currentWindow: true }, async ([tab]) => {
        if (!tab?.id) { resolve({ url: '', title: '', content: '' }); return; }
        try {
          const results = await chrome.scripting.executeScript({ target: { tabId: tab.id }, func: () => document.body.innerText.slice(0, 15000) });
          resolve({ url: tab.url || '', title: tab.title || '', content: results[0]?.result || '' });
        } catch { resolve({ url: tab.url || '', title: tab.title || '', content: '' }); }
      });
    });
  };

  const sendMessage = async (content: string) => {
    if (!content.trim() || !settings) return;
    const userMessage: Message = { id: Date.now().toString(), role: 'user', content: content.trim(), timestamp: Date.now() };
    setMessages(prev => [...prev, userMessage]);
    setInput('');
    setIsLoading(true);

    try {
      const pageContext = await getPageContext();
      const providerSettings = settings.providers.find(p => p.provider === settings.activeProvider);
      if (!providerSettings?.apiKey) throw new Error('Please configure your API key in settings');

      const options: ProviderOptions = { provider: settings.activeProvider, apiKey: providerSettings.apiKey, model: providerSettings.model, baseUrl: providerSettings.baseUrl };
      let assistantContent = '';
      const assistantMessage: Message = { id: (Date.now() + 1).toString(), role: 'assistant', content: '', timestamp: Date.now() };
      setMessages(prev => [...prev, assistantMessage]);

      for await (const chunk of chatStream(options, [...messages, userMessage], pageContext)) {
        if (chunk.error) throw new Error(chunk.error);
        assistantContent += chunk.content;
        setMessages(prev => prev.map(m => m.id === assistantMessage.id ? { ...m, content: assistantContent } : m));
      }
      if (settings.memoryEnabled) await saveMemory({ id: `conv-${Date.now()}`, type: 'note', content: `${userMessage.content}\n\n${assistantContent}`, createdAt: Date.now(), updatedAt: Date.now() });
    } catch (error) {
      setMessages(prev => [...prev, { id: (Date.now() + 1).toString(), role: 'assistant', content: `Error: ${error instanceof Error ? error.message : 'An error occurred'}`, timestamp: Date.now() }]);
    } finally { setIsLoading(false); }
  };

  const handleVoiceInput = () => {
    if (!('webkitSpeechRecognition' in window)) { alert('Voice input not supported'); return; }
    const recognition = new (window as any).webkitSpeechRecognition();
    recognition.continuous = false;
    recognition.interimResults = false;
    recognition.onstart = () => setIsListening(true);
    recognition.onend = () => setIsListening(false);
    recognition.onresult = (e: any) => setInput(prev => prev + ' ' + e.results[0][0].transcript);
    recognition.start();
  };

  if (!settings) return <div className="h-full flex items-center justify-center bg-gray-50 dark:bg-gray-900"><div className="animate-pulse text-gray-500">Loading...</div></div>;

  return (
    <div className={`h-full flex flex-col ${settings.theme === 'dark' ? 'dark' : ''}`}>
      <header className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-800/80 backdrop-blur-sm">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-primary-500 to-primary-700 flex items-center justify-center text-white"><Icons.Bot /></div>
          <h1 className="font-semibold text-lg text-gray-900 dark:text-white">Relay</h1>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => chrome.runtime.sendMessage({ action: 'openSettings' })} className="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700"><Icons.Settings /></button>
          <button onClick={() => chrome.runtime.sendMessage({ action: 'closeSidebar' })} className="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700"><Icons.Close /></button>
        </div>
      </header>
      <div className="px-4 py-2 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
        <div className="flex gap-2 overflow-x-auto pb-2 scrollbar-hide">
          {QUICK_ACTIONS.map(action => (<button key={action.id} onClick={() => sendMessage(action.prompt)} className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 text-sm font-medium text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 whitespace-nowrap"><action.icon />{action.label}</button>))}
        </div>
      </div>
      <div className="flex-1 overflow-y-auto px-4 py-3 space-y-4">
        {messages.map(message => (
          <div key={message.id} className={`flex gap-3 ${message.role === 'user' ? 'flex-row-reverse' : ''}`}>
            <div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ${message.role === 'user' ? 'bg-primary-500 text-white' : 'bg-gradient-to-br from-primary-500 to-primary-700 text-white'}`}>{message.role === 'user' ? <Icons.User /> : <Icons.Bot />}</div>
            <div className={`max-w-[80%] rounded-2xl px-4 py-2 ${message.role === 'user' ? 'bg-primary-500 text-white' : 'bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white'}`}><p className="text-sm whitespace-pre-wrap">{message.content}</p></div>
          </div>
        ))}
        {isLoading && (<div className="flex gap-3"><div className="w-8 h-8 rounded-full bg-gradient-to-br from-primary-500 to-primary-700 flex items-center justify-center"><Icons.Bot /></div><div className="bg-gray-100 dark:bg-gray-700 rounded-2xl px-4 py-2"><div className="flex gap-1"><span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} /><span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} /><span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} /></div></div></div>)}
        <div ref={messagesEndRef} />
      </div>
      <div className="p-4 border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
        <div className="flex items-end gap-2 bg-gray-100 dark:bg-gray-700 rounded-2xl px-4 py-2">
          <textarea value={input} onChange={e => setInput(e.target.value)} onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(input); } }} placeholder="Ask anything..." rows={1} className="flex-1 bg-transparent border-none outline-none resize-none text-gray-900 dark:text-white placeholder-gray-500 text-sm max-h-32" style={{ minHeight: '24px' }} />
          <button onClick={handleVoiceInput} className={`p-2 rounded-lg ${isListening ? 'bg-red-500 text-white animate-pulse' : 'hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-500'}`}><Icons.Mic /></button>
          <button onClick={() => sendMessage(input)} disabled={!input.trim() || isLoading} className="p-2 rounded-lg bg-primary-500 text-white hover:bg-primary-600 disabled:opacity-50"><Icons.Send /></button>
        </div>
      </div>
    </div>
  );
}
