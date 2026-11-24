import { describe, it, expect, beforeEach, vi } from 'vitest';
import { focusTrap } from './focusTrap';

describe('focusTrap accessibility action', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
  });

  it('focuses first interactive element on mount, cycles Tab/Shift+Tab, and restores focus on destroy', async () => {
    vi.useFakeTimers();
    const trigger = document.createElement('button');
    trigger.textContent = 'Open Modal';
    document.body.appendChild(trigger);
    trigger.focus();
    expect(document.activeElement).toBe(trigger);

    const dialog = document.createElement('div');
    dialog.tabIndex = -1;
    const firstBtn = document.createElement('button');
    firstBtn.textContent = 'First';
    const disabledBtn = document.createElement('button');
    disabledBtn.disabled = true;
    const lastInput = document.createElement('input');
    dialog.append(firstBtn, disabledBtn, lastInput);
    document.body.appendChild(dialog);

    const handle = focusTrap(dialog);
    vi.advanceTimersByTime(20);
    expect(document.activeElement).toBe(firstBtn);

    // Shift+Tab on first element wraps to last
    const shiftTab = new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, cancelable: true });
    dialog.dispatchEvent(shiftTab);
    expect(shiftTab.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(lastInput);

    // Tab on last element wraps to first
    const forwardTab = new KeyboardEvent('keydown', { key: 'Tab', cancelable: true });
    dialog.dispatchEvent(forwardTab);
    expect(forwardTab.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(firstBtn);

    // Non-Tab keys are ignored
    const esc = new KeyboardEvent('keydown', { key: 'Escape', cancelable: true });
    dialog.dispatchEvent(esc);
    expect(esc.defaultPrevented).toBe(false);

    handle.destroy();
    expect(document.activeElement).toBe(trigger);
    vi.useRealTimers();
  });

  it('focuses container and prevents Tab escape when no focusable children exist', () => {
    vi.useFakeTimers();
    const emptyDialog = document.createElement('div');
    emptyDialog.tabIndex = -1;
    document.body.appendChild(emptyDialog);

    const handle = focusTrap(emptyDialog);
    vi.advanceTimersByTime(20);
    expect(document.activeElement).toBe(emptyDialog);

    const tab = new KeyboardEvent('keydown', { key: 'Tab', cancelable: true });
    emptyDialog.dispatchEvent(tab);
    expect(tab.defaultPrevented).toBe(true);

    handle.destroy();
    vi.useRealTimers();
  });
});
