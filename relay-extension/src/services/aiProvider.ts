/**
 * AI Provider Service - Modular architecture for multiple AI providers
 */

import { AIProvider, Message, StreamChunk, PageContext } from '@/types';

// Provider configurations
const PROVIDER_CONFIGS: Record<AIProvider, { baseUrl: string; defaultModel: string; models: string[] }> = {
  openrouter: {
    baseUrl: 'https://openrouter.ai/api/v1',
    defaultModel: 'anthropic/claude-3.5-sonnet',
    models: [
      'anthropic/claude-3.5-sonnet',
      'anthropic/claude-3-opus',
      'openai/gpt-4o',
      'openai/gpt-4-turbo',
      'google/gemini-pro-1.5',
      'meta-llama/llama-3-70b-instruct',
    ],
  },
  openai: {
    baseUrl: 'https://api.openai.com/v1',
    defaultModel: 'gpt-4o',
    models: ['gpt-4o', 'gpt-4-turbo', 'gpt-4', 'gpt-3.5-turbo'],
  },
  anthropic: {
    baseUrl: 'https://api.anthropic.com',
    defaultModel: 'claude-3-5-sonnet-20241022',
    models: ['claude-3-5-sonnet-20241022', 'claude-3-opus-20240229', 'claude-3-haiku-20240307'],
  },
  groq: {
    baseUrl: 'https://api.groq.com/openai/v1',
    defaultModel: 'llama-3.1-70b-versatile',
    models: ['llama-3.1-70b-versatile', 'llama-3.1-8b-instant', 'mixtral-8x7b-32768'],
  },
  gemini: {
    baseUrl: 'https://generativelanguage.googleapis.com/v1beta',
    defaultModel: 'gemini-1.5-pro',
    models: ['gemini-1.5-pro', 'gemini-1.5-flash', 'gemini-pro'],
  },
  ollama: {
    baseUrl: 'http://localhost:11434/api',
    defaultModel: 'llama3.1',
    models: ['llama3.1', 'mistral', 'codellama', 'phi3'],
  },
  custom: {
    baseUrl: '',
    defaultModel: '',
    models: [],
  },
};

export interface ProviderOptions {
  apiKey: string;
  baseUrl?: string;
  model: string;
  provider: AIProvider;
}

/**
 * Build the request body based on provider
 */
function buildRequestBody(
  provider: AIProvider,
  messages: Message[],
  model: string,
  stream: boolean = true
): object {
  const baseMessages = messages.map(m => ({
    role: m.role,
    content: m.content,
  }));

  switch (provider) {
    case 'anthropic':
      return {
        model,
        max_tokens: 4096,
        messages: baseMessages.filter(m => m.role !== 'system'),
        system: messages.find(m => m.role === 'system')?.content || '',
        stream,
      };
    case 'gemini':
      return {
        contents: messages
          .filter(m => m.role !== 'system')
          .map(m => ({
            role: m.role === 'assistant' ? 'model' : 'user',
            parts: [{ text: m.content }],
          })),
        generationConfig: {
          maxOutputTokens: 4096,
        },
      };
    case 'ollama':
      return {
        model,
        messages: baseMessages,
        stream,
      };
    default:
      // OpenAI-compatible format (OpenRouter, OpenAI, Groq, Custom)
      return {
        model,
        messages: baseMessages,
        max_tokens: 4096,
        stream,
      };
  }
}

/**
 * Send a chat request with streaming support
 */
export async function* chatStream(
  options: ProviderOptions,
  messages: Message[],
  context?: PageContext
): AsyncGenerator<StreamChunk> {
  const config = PROVIDER_CONFIGS[options.provider];
  const baseUrl = options.baseUrl || config.baseUrl;
  const model = options.model || config.defaultModel;

  // Add context to messages if provided
  let enhancedMessages = [...messages];
  if (context) {
    const contextMessage = `Context from current page:\nURL: ${context.url}\nTitle: ${context.title}\nContent: ${context.content.substring(0, 10000)}`;
    
    const existingSystemIndex = enhancedMessages.findIndex(m => m.role === 'system');
    if (existingSystemIndex >= 0) {
      enhancedMessages[existingSystemIndex].content += `\n\n${contextMessage}`;
    } else {
      enhancedMessages.unshift({
        id: 'context',
        role: 'system',
        content: contextMessage,
        timestamp: Date.now(),
      });
    }
  }

  const requestBody = buildRequestBody(options.provider, enhancedMessages, model);

  let url = `${baseUrl}/chat/completions`;
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };

  // Provider-specific headers and URL adjustments
  switch (options.provider) {
    case 'anthropic':
      url = `${baseUrl}/v1/messages`;
      headers['x-api-key'] = options.apiKey;
      headers['anthropic-version'] = '2023-06-01';
      break;
    case 'gemini':
      url = `${baseUrl}/models/${model}:streamGenerateContent?alt=sse&key=${options.apiKey}`;
      break;
    case 'ollama':
      url = `${baseUrl}/chat`;
      break;
    default:
      headers['Authorization'] = `Bearer ${options.apiKey}`;
      if (options.provider === 'openrouter') {
        headers['HTTP-Referer'] = 'https://relay-extension.app';
        headers['X-Title'] = 'Relay Extension';
      }
  }

  let retries = 0;
  const maxRetries = 3;

  while (retries < maxRetries) {
    try {
      const response = await fetch(url, {
        method: 'POST',
        headers,
        body: JSON.stringify(requestBody),
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`API Error: ${response.status} - ${errorText}`);
      }

      if (options.provider === 'gemini') {
        // Gemini returns an array of SSE events
        const reader = response.body?.getReader();
        if (!reader) throw new Error('No response body');

        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() || '';

          for (const line of lines) {
            if (line.startsWith('data: ')) {
              const data = JSON.parse(line.slice(6));
              const content = data.candidates?.[0]?.content?.parts?.[0]?.text || '';
              if (content) {
                yield { content, done: false };
              }
            }
          }
        }
        yield { content: '', done: true };
        return;
      }

      // Standard SSE stream for other providers
      const reader = response.body?.getReader();
      if (!reader) throw new Error('No response body');

      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = line.slice(6);
            
            if (data === '[DONE]') {
              yield { content: '', done: true };
              return;
            }

            try {
              const parsed = JSON.parse(data);
              
              // Handle different response formats
              let content = '';
              if (parsed.choices?.[0]?.delta?.content) {
                content = parsed.choices[0].delta.content;
              } else if (parsed.message?.content) {
                content = parsed.message.content;
              }

              if (content) {
                yield { content, done: false };
              }
            } catch (e) {
              // Skip invalid JSON
            }
          }
        }
      }

      yield { content: '', done: true };
      return;
    } catch (error) {
      retries++;
      if (retries >= maxRetries) {
        yield { 
          content: '', 
          done: true, 
          error: error instanceof Error ? error.message : 'Unknown error occurred' 
        };
        return;
      }
      // Wait before retrying (exponential backoff)
      await new Promise(resolve => setTimeout(resolve, Math.pow(2, retries) * 1000));
    }
  }
}

/**
 * Get available models for a provider
 */
export function getAvailableModels(provider: AIProvider): string[] {
  return PROVIDER_CONFIGS[provider].models;
}

/**
 * Get default model for a provider
 */
export function getDefaultModel(provider: AIProvider): string {
  return PROVIDER_CONFIGS[provider].defaultModel;
}

/**
 * Get base URL for a provider
 */
export function getBaseUrl(provider: AIProvider): string {
  return PROVIDER_CONFIGS[provider].baseUrl;
}

/**
 * Validate API key format (basic check)
 */
export function validateApiKey(apiKey: string, provider: AIProvider): boolean {
  if (!apiKey || apiKey.length < 10) return false;
  
  // Provider-specific validation patterns
  switch (provider) {
    case 'openai':
      return apiKey.startsWith('sk-');
    case 'anthropic':
      return apiKey.startsWith('sk-ant-');
    case 'groq':
      return apiKey.startsWith('gsk_');
    default:
      return true;
  }
}
