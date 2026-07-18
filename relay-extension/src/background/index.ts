/**
 * Relay Extension - Background Service Worker
 * Handles extension lifecycle, context menus, and message routing
 */

import { getSettings, saveSettings, DEFAULT_SETTINGS } from '@/utils/storage';

// Initialize extension
chrome.runtime.onInstalled.addListener(async (details) => {
  if (details.reason === 'install') {
    // Set default settings
    await saveSettings(DEFAULT_SETTINGS);
    
    // Create context menu items
    chrome.contextMenus.create({
      id: 'relay-summarize',
      title: 'Summarize with Relay',
      contexts: ['selection'],
    });
    
    chrome.contextMenus.create({
      id: 'relay-explain',
      title: 'Explain with Relay',
      contexts: ['selection'],
    });
    
    chrome.contextMenus.create({
      id: 'relay-translate',
      title: 'Translate with Relay',
      contexts: ['selection'],
    });
    
    // Open onboarding page or sidebar
    chrome.tabs.create({
      url: 'settings.html?onboarding=true',
    });
  }
});

// Context menu handler
chrome.contextMenus.onClicked.addListener(async (info, tab) => {
  if (!tab?.id) return;
  
  const action = info.menuItemId.replace('relay-', '');
  const text = info.selectionText || '';
  
  // Send message to sidebar to handle the action
  chrome.runtime.sendMessage({
    action: 'contextMenuAction',
    type: action,
    text,
    tabId: tab.id,
  });
  
  // Open sidebar
  chrome.sidebarAction.open();
});

// Browser action click - toggle sidebar
chrome.action.onClicked.addListener(() => {
  chrome.sidebarAction.toggle();
});

// Message handler
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  handleMessage(message, sender, sendResponse);
  return true; // Keep channel open for async response
});

async function handleMessage(
  message: any,
  sender: chrome.runtime.MessageSender,
  sendResponse: (response?: any) => void
) {
  try {
    switch (message.action) {
      case 'openSettings':
        chrome.runtime.openOptionsPage();
        sendResponse({ success: true });
        break;
        
      case 'closeSidebar':
        chrome.sidebarAction.close();
        sendResponse({ success: true });
        break;
        
      case 'getPageContent':
        if (!sender.tab?.id) {
          sendResponse({ error: 'No tab ID' });
          return;
        }
        
        try {
          const results = await chrome.scripting.executeScript({
            target: { tabId: sender.tab.id },
            func: () => ({
              url: window.location.href,
              title: document.title,
              content: document.body.innerText.slice(0, 15000),
              selection: window.getSelection()?.toString() || '',
            }),
          });
          sendResponse(results[0]?.result);
        } catch (error) {
          sendResponse({ error: error instanceof Error ? error.message : 'Failed to get page content' });
        }
        break;
        
      case 'getSettings':
        const settings = await getSettings();
        sendResponse(settings);
        break;
        
      case 'saveSettings':
        await saveSettings(message.settings);
        sendResponse({ success: true });
        break;
        
      case 'agentAction':
        // Handle agent actions with explicit approval
        if (sender.tab?.id) {
          await executeAgentAction(message.actionType, message.target, message.value, sender.tab.id);
          sendResponse({ success: true });
        } else {
          sendResponse({ error: 'No tab available' });
        }
        break;
        
      default:
        sendResponse({ error: 'Unknown action' });
    }
  } catch (error) {
    sendResponse({ 
      error: error instanceof Error ? error.message : 'An error occurred' 
    });
  }
}

async function executeAgentAction(
  actionType: string,
  target: string,
  value: string,
  tabId: number
) {
  switch (actionType) {
    case 'click':
      await chrome.scripting.executeScript({
        target: { tabId },
        func: (selector: string) => {
          const element = document.querySelector(selector);
          if (element) {
            (element as HTMLElement).click();
            return true;
          }
          return false;
        },
        args: [target],
      });
      break;
      
    case 'fill':
      await chrome.scripting.executeScript({
        target: { tabId },
        func: (selector: string, val: string) => {
          const element = document.querySelector(selector) as HTMLInputElement;
          if (element) {
            element.value = val;
            element.dispatchEvent(new Event('input', { bubbles: true }));
            element.dispatchEvent(new Event('change', { bubbles: true }));
            return true;
          }
          return false;
        },
        args: [target, value],
      });
      break;
      
    case 'navigate':
      await chrome.tabs.update(tabId, { url: target });
      break;
      
    case 'openTab':
      await chrome.tabs.create({ url: target });
      break;
      
    case 'closeTab':
      await chrome.tabs.remove(tabId);
      break;
      
    case 'search':
      const searchUrl = `https://www.google.com/search?q=${encodeURIComponent(target)}`;
      await chrome.tabs.create({ url: searchUrl });
      break;
      
    case 'extract':
      const extractResults = await chrome.scripting.executeScript({
        target: { tabId },
        func: (selector: string) => {
          const elements = document.querySelectorAll(selector);
          return Array.from(elements).map(el => el.textContent || '');
        },
        args: [target],
      });
      return extractResults[0]?.result;
  }
}

// Keep service worker alive for long-running tasks
chrome.alarms.create('keepAlive', { periodInMinutes: 0.5 });
chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === 'keepAlive') {
    // Just touch storage to keep worker alive
    chrome.storage.local.get(['_ping'], () => {});
  }
});
