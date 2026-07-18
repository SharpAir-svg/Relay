/**
 * Relay Extension - Content Script
 * Injected into webpages to enable AI features
 */

// Listen for messages from the extension
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  handleMessage(message, sendResponse);
  return true;
});

async function handleMessage(message: any, sendResponse: (response?: any) => void) {
  switch (message.action) {
    case 'getPageContent':
      sendResponse({
        url: window.location.href,
        title: document.title,
        content: document.body.innerText.slice(0, 15000),
        selection: window.getSelection()?.toString() || '',
      });
      break;
      
    case 'getSelectedText':
      sendResponse({
        text: window.getSelection()?.toString() || '',
      });
      break;
      
    case 'executeScript':
      try {
        const result = await executeScript(message.script, message.args);
        sendResponse({ success: true, result });
      } catch (error) {
        sendResponse({ 
          success: false, 
          error: error instanceof Error ? error.message : 'Unknown error' 
        });
      }
      break;
      
    case 'highlightElement':
      highlightElement(message.selector);
      sendResponse({ success: true });
      break;
      
    case 'removeHighlight':
      removeHighlight();
      sendResponse({ success: true });
      break;
      
    default:
      sendResponse({ error: 'Unknown action' });
  }
}

async function executeScript(script: string, args: any[]): Promise<any> {
  // Safe script execution in content context
  const func = new Function(...args.map((_, i) => `arg${i}`), `return (${script})(...args)`);
  return func(...args);
}

function highlightElement(selector: string) {
  removeHighlight();
  
  const element = document.querySelector(selector);
  if (element) {
    element.setAttribute('data-relay-highlight', 'true');
    element.scrollIntoView({ behavior: 'smooth', block: 'center' });
    
    // Add visual highlight
    const originalOutline = element.style.outline;
    element.style.outline = '3px solid #0ea5e9';
    element.style.outlineOffset = '2px';
    
    // Store original style for removal
    (element as HTMLElement).dataset.relayOriginalOutline = originalOutline;
  }
}

function removeHighlight() {
  const highlighted = document.querySelectorAll('[data-relay-highlight="true"]');
  highlighted.forEach(el => {
    el.removeAttribute('data-relay-highlight');
    const originalOutline = (el as HTMLElement).dataset.relayOriginalOutline;
    if (originalOutline !== undefined) {
      (el as HTMLElement).style.outline = originalOutline;
    } else {
      (el as HTMLElement).style.outline = '';
    }
    delete (el as HTMLElement).dataset.relayOriginalOutline;
  });
}

// Notify background script that content script is loaded
chrome.runtime.sendMessage({ action: 'contentScriptLoaded' });
