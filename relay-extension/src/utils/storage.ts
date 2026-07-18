/**
 * Secure local storage utilities for Chrome Extension
 */

import { encrypt, decrypt } from './crypto';
import { ExtensionSettings, Memory, PasswordEntry } from '@/types';

const STORAGE_KEYS = {
  SETTINGS: 'relay_settings',
  MEMORIES: 'relay_memories',
  VAULT_HASH: 'relay_vault_hash',
  VAULT_ENTRIES: 'relay_vault_entries',
  MASTER_PASSWORD_HASH: 'relay_master_hash',
};

// Default settings
export const DEFAULT_SETTINGS: ExtensionSettings = {
  theme: 'system',
  providers: [],
  activeProvider: 'openrouter',
  memoryEnabled: true,
  agentPermissions: {
    click: false,
    fill: false,
    navigate: false,
    openTab: false,
    closeTab: false,
    search: false,
    extract: false,
    download: false,
  },
  voiceEnabled: true,
  autoLockMinutes: 15,
};

/**
 * Get data from Chrome storage
 */
export async function getFromStorage<T>(key: string): Promise<T | null> {
  return new Promise((resolve) => {
    chrome.storage.local.get([key], (result) => {
      resolve(result[key] || null);
    });
  });
}

/**
 * Set data in Chrome storage
 */
export async function setToStorage<T>(key: string, value: T): Promise<void> {
  return new Promise((resolve) => {
    chrome.storage.local.set({ [key]: value }, resolve);
  });
}

/**
 * Remove data from Chrome storage
 */
export async function removeFromStorage(key: string): Promise<void> {
  return new Promise((resolve) => {
    chrome.storage.local.remove(key, resolve);
  });
}

/**
 * Get extension settings
 */
export async function getSettings(): Promise<ExtensionSettings> {
  const settings = await getFromStorage<ExtensionSettings>(STORAGE_KEYS.SETTINGS);
  return settings || DEFAULT_SETTINGS;
}

/**
 * Save extension settings
 */
export async function saveSettings(settings: ExtensionSettings): Promise<void> {
  await setToStorage(STORAGE_KEYS.SETTINGS, settings);
}

/**
 * Get all memories
 */
export async function getMemories(): Promise<Memory[]> {
  const memories = await getFromStorage<Memory[]>(STORAGE_KEYS.MEMORIES);
  return memories || [];
}

/**
 * Save a memory
 */
export async function saveMemory(memory: Memory): Promise<void> {
  const memories = await getMemories();
  const existingIndex = memories.findIndex(m => m.id === memory.id);
  
  if (existingIndex >= 0) {
    memories[existingIndex] = memory;
  } else {
    memories.push(memory);
  }
  
  await setToStorage(STORAGE_KEYS.MEMORIES, memories);
}

/**
 * Delete a memory
 */
export async function deleteMemory(id: string): Promise<void> {
  const memories = await getMemories();
  const filtered = memories.filter(m => m.id !== id);
  await setToStorage(STORAGE_KEYS.MEMORIES, filtered);
}

/**
 * Clear all memories
 */
export async function clearMemories(): Promise<void> {
  await setToStorage(STORAGE_KEYS.MEMORIES, []);
}

/**
 * Check if vault has a master password set
 */
export async function hasMasterPassword(): Promise<boolean> {
  const hash = await getFromStorage<string>(STORAGE_KEYS.MASTER_PASSWORD_HASH);
  return !!hash;
}

/**
 * Set master password
 */
export async function setMasterPassword(password: string): Promise<void> {
  const { hash } = await import('./crypto');
  const passwordHash = await hash(password);
  await setToStorage(STORAGE_KEYS.MASTER_PASSWORD_HASH, passwordHash);
}

/**
 * Verify master password
 */
export async function verifyMasterPassword(password: string): Promise<boolean> {
  const storedHash = await getFromStorage<string>(STORAGE_KEYS.MASTER_PASSWORD_HASH);
  if (!storedHash) return false;
  
  const { hash } = await import('./crypto');
  const computedHash = await hash(password);
  return computedHash === storedHash;
}

/**
 * Encrypt and save vault entries
 */
export async function saveVaultEntries(entries: PasswordEntry[], masterPassword: string): Promise<void> {
  const encrypted = await encrypt(JSON.stringify(entries), masterPassword);
  await setToStorage(STORAGE_KEYS.VAULT_ENTRIES, encrypted);
}

/**
 * Decrypt and get vault entries
 */
export async function getVaultEntries(masterPassword: string): Promise<PasswordEntry[]> {
  const encrypted = await getFromStorage<string>(STORAGE_KEYS.VAULT_ENTRIES);
  if (!encrypted) return [];
  
  const decrypted = await decrypt(encrypted, masterPassword);
  return JSON.parse(decrypted);
}

/**
 * Lock the vault (clear decrypted data from memory)
 */
export async function lockVault(): Promise<void> {
  // In a real implementation, this would clear any cached decrypted data
  await setToStorage('relay_vault_unlocked', false);
}

/**
 * Check if vault is unlocked
 */
export async function isVaultUnlocked(): Promise<boolean> {
  const unlocked = await getFromStorage<boolean>('relay_vault_unlocked');
  return !!unlocked;
}

/**
 * Mark vault as unlocked
 */
export async function markVaultUnlocked(): Promise<void> {
  await setToStorage('relay_vault_unlocked', true);
}

/**
 * Clear all extension data
 */
export async function clearAllData(): Promise<void> {
  await chrome.storage.local.clear();
}
