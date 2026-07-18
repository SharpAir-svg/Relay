# Relay - AI Browser Extension

A powerful, privacy-first AI browser assistant with support for multiple AI providers.

## Features

- **Multi-Provider Support**: OpenRouter, OpenAI, Anthropic, Groq, Google Gemini, Ollama, or any OpenAI-compatible endpoint
- **AI Sidebar**: Chat with any webpage, get summaries, explanations, translations
- **Browser Memory**: Locally stored conversations and preferences
- **Password Vault**: Encrypted local password storage with master password
- **Agent Mode**: Automate browser tasks with explicit approval
- **Voice Input**: Hands-free interaction
- **Dark/Light Theme**: Beautiful glassmorphic UI

## Installation

### From Source

1. **Install dependencies**
   ```bash
   npm install
   ```

2. **Build the extension**
   ```bash
   npm run build
   ```

3. **Load in Chrome**
   - Open `chrome://extensions/`
   - Enable "Developer mode"
   - Click "Load unpacked"
   - Select the `dist` folder

### Development

```bash
npm run dev
```

This builds the extension in watch mode.

## Configuration

1. Click the Relay icon in your browser toolbar
2. Go to Settings (gear icon)
3. Select your AI provider
4. Enter your API key
5. Choose your preferred model

## Supported Providers

| Provider | Default Model | Notes |
|----------|---------------|-------|
| OpenRouter | Claude 3.5 Sonnet | Access to 100+ models |
| OpenAI | GPT-4o | Requires OpenAI API key |
| Anthropic | Claude 3.5 Sonnet | Direct Anthropic API |
| Groq | Llama 3.1 70B | Fast inference |
| Google Gemini | Gemini 1.5 Pro | Google's multimodal model |
| Ollama | Llama 3.1 | Local models |
| Custom | Any | OpenAI-compatible endpoints |

## Security

- All data stored locally using Chrome storage
- Passwords encrypted with AES-256-GCM
- API keys never leave your browser
- Agent actions require explicit approval

## Keyboard Shortcuts

- `Ctrl/Cmd + Shift + R`: Toggle sidebar
- `Ctrl/Cmd + K`: Command palette (coming soon)

## Project Structure

```
relay-extension/
├── src/
│   ├── background/      # Service worker
│   ├── content/         # Content scripts
│   ├── sidebar/         # Sidebar React app
│   ├── settings/        # Settings page
│   ├── services/        # AI provider services
│   ├── utils/           # Utilities (crypto, storage)
│   └── types/           # TypeScript types
├── public/
│   ├── manifest.json    # Extension manifest
│   ├── sidebar.html     # Sidebar entry point
│   └── settings.html    # Settings entry point
└── dist/                # Built extension
```

## License

MIT License

## Contributing

Contributions welcome! Please open an issue first to discuss changes.
