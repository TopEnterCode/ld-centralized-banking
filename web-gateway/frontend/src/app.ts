import { createClient, type LDClient, type LDContext } from '@launchdarkly/js-client-sdk';
import './styles.css';

type RuntimeConfig = {
  mode: 'mock' | 'launchdarkly';
  modeLabel: string;
  clientSideId: string;
  clientSdkConfigured: boolean;
};

type SyntheticContext = {
  key: string;
  employee: boolean;
  cohort: string;
  tier: string;
  region: string;
  channel: string;
  platform: 'android' | 'ios' | 'web';
  appVersion: string;
  deviceKey: string;
};

type BrowserDecision = {
  value: boolean;
  reason: string;
  source: string;
  usedFallback: boolean;
};

type TimelineEvent = {
  service: string;
  title: string;
  detail: string;
  source: string;
  degraded: boolean;
  timestamp: string;
};

type JourneyResponse = {
  correlationId: string;
  success: boolean;
  degraded: boolean;
  payment: { paymentReference: string; authoritativeVersion: string };
  timeline: TimelineEvent[];
};

type RolloutResponse = {
  assignments: Array<{ key: string; enabled: boolean; reason: string; source: string }>;
  enabledCount: number;
  disabledCount: number;
};

interface BrowserFlagProvider {
  initialize(context: SyntheticContext): Promise<void>;
  identify(context: SyntheticContext): Promise<void>;
  evaluate(flagKey: string, fallback: boolean): Promise<BrowserDecision>;
  close(): void;
}

class MockBrowserFlagProvider implements BrowserFlagProvider {
  private context!: SyntheticContext;

  async initialize(context: SyntheticContext): Promise<void> {
    this.context = context;
  }

  async identify(context: SyntheticContext): Promise<void> {
    this.context = context;
  }

  async evaluate(flagKey: string, fallback: boolean): Promise<BrowserDecision> {
    try {
      const response = await api<{
        value: boolean;
        reason: string;
        source: string;
        usedFallback: boolean;
      }>('/api/browser/evaluate', {
        method: 'POST',
        body: JSON.stringify({ flagKey, context: this.context })
      });
      return { ...response, value: Boolean(response.value) };
    } catch {
      return {
        value: fallback,
        reason: 'Mock browser provider could not reach the gateway',
        source: 'service-fallback',
        usedFallback: true
      };
    }
  }

  close(): void {}
}

class LiveBrowserFlagProvider implements BrowserFlagProvider {
  private client: LDClient | undefined;

  constructor(private readonly clientSideId: string) {}

  async initialize(context: SyntheticContext): Promise<void> {
    if (!this.clientSideId) {
      throw new Error('Client-side ID is not configured');
    }
    this.client = createClient(this.clientSideId, toLdContext(context), {
      streaming: true
    });
    this.client.on('change:client-new-payment-ui', () => {
      window.dispatchEvent(new CustomEvent('client-flag-change'));
    });
    this.client.start();
    const result = await this.client.waitForInitialization({ timeout: 3 });
    if (result.status !== 'complete') {
      throw new Error(`LaunchDarkly initialization ${result.status}`);
    }
  }

  async identify(context: SyntheticContext): Promise<void> {
    if (!this.client) return;
    await this.client.identify(toLdContext(context));
  }

  async evaluate(flagKey: string, fallback: boolean): Promise<BrowserDecision> {
    if (!this.client) {
      return { value: fallback, reason: 'SDK not initialized', source: 'sdk-default', usedFallback: true };
    }
    const value = Boolean(this.client.variation(flagKey, fallback));
    return {
      value,
      reason: 'Browser SDK current in-memory evaluation',
      source: 'launchdarkly',
      usedFallback: false
    };
  }

  close(): void {
    this.client?.close();
  }
}

const state: {
  runtime?: RuntimeConfig;
  personas: SyntheticContext[];
  selected?: SyntheticContext;
  provider?: BrowserFlagProvider;
  clientDecision?: BrowserDecision;
} = { personas: [] };

const $ = <T extends HTMLElement>(selector: string): T => {
  const element = document.querySelector<T>(selector);
  if (!element) throw new Error(`Missing UI element: ${selector}`);
  return element;
};

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) }
  });
  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as { detail?: string; message?: string };
    throw new Error(problem.detail ?? problem.message ?? `Request failed (${response.status})`);
  }
  return (await response.json()) as T;
}

function toLdContext(context: SyntheticContext): LDContext {
  return {
    kind: 'multi',
    user: {
      kind: 'user',
      key: context.key,
      employee: context.employee,
      cohort: context.cohort,
      tier: context.tier,
      region: context.region,
      channel: context.channel
    },
    device: {
      kind: 'device',
      key: context.deviceKey,
      platform: context.platform,
      appVersion: context.appVersion
    }
  };
}

async function initialize(): Promise<void> {
  bindTabs();
  bindPayment();
  bindControls();
  window.addEventListener('client-flag-change', () => void refreshClientFlag());
  try {
    const [runtime, personas] = await Promise.all([
      api<RuntimeConfig>('/api/runtime'),
      api<SyntheticContext[]>('/api/personas')
    ]);
    state.runtime = runtime;
    state.personas = personas;
    state.selected = personas[0];
    configureMode(runtime);
    state.provider =
      runtime.mode === 'launchdarkly'
        ? new LiveBrowserFlagProvider(runtime.clientSideId)
        : new MockBrowserFlagProvider();
    if (!state.selected) throw new Error('No synthetic personas configured');
    try {
      await state.provider.initialize(state.selected);
      setConnection(runtime.mode === 'mock' ? 'Mock provider ready' : 'LaunchDarkly connected', true);
    } catch (error) {
      setConnection(error instanceof Error ? error.message : 'Provider unavailable', false);
    }
    await Promise.all([refreshPersona(), refreshHealth(), refreshRollout()]);
    window.setInterval(() => void refreshHealth(), 7000);
  } catch (error) {
    showToast(error instanceof Error ? error.message : 'Unable to initialize the POC', true);
    setConnection('Gateway unavailable', false);
  }
}

function configureMode(runtime: RuntimeConfig): void {
  const badge = $('#mode-badge');
  badge.textContent = runtime.modeLabel;
  badge.className = `mode-badge ${runtime.mode === 'mock' ? 'mock' : 'live'}`;
  $('#browser-source').textContent = runtime.mode === 'mock' ? 'mock browser provider' : 'LaunchDarkly browser SDK';
}

function bindTabs(): void {
  document.querySelectorAll<HTMLButtonElement>('.tab').forEach((button) => {
    button.addEventListener('click', () => {
      const target = button.dataset.tab;
      document.querySelectorAll('.tab').forEach((tab) => tab.classList.remove('active'));
      document.querySelectorAll('.view').forEach((view) => view.classList.remove('active'));
      button.classList.add('active');
      $(`#view-${target}`).classList.add('active');
    });
  });
}

function bindPayment(): void {
  $('#submit-payment').addEventListener('click', () => void submitJourney());
  $('#new-payment').addEventListener('click', () => {
    $('#payment-result').classList.add('hidden');
    $('#payment-panel').classList.remove('hidden');
  });
  $('#persona-select').addEventListener('change', (event) => {
    const key = (event.target as HTMLSelectElement).value;
    state.selected = state.personas.find((persona) => persona.key === key);
    void refreshPersona();
  });
}

function bindControls(): void {
  document.querySelectorAll<HTMLButtonElement>('[data-action]').forEach((button) => {
    button.addEventListener('click', async () => {
      if (!state.selected) return;
      const action = button.dataset.action ?? '';
      const value = button.dataset.dynamicPersona ? state.selected.key : (button.dataset.value ?? '');
      await applyControl(action, value, button);
    });
  });
}

async function applyControl(action: string, value: string, button: HTMLButtonElement): Promise<void> {
  setBusy(button, true);
  try {
    await api('/api/demo/control', { method: 'POST', body: JSON.stringify({ action, value }) });
    document.querySelectorAll<HTMLButtonElement>(`[data-action="${action}"]`).forEach((item) => item.classList.remove('selected'));
    button.classList.add('selected');
    if (action === 'reset' || action === 'restore-all') {
      resetControlSelections();
    }
    if (action === 'reset') {
      resetJourneyPresentation();
      const defaultPersona = state.personas[0];
      if (!defaultPersona) throw new Error('No synthetic personas configured');
      state.selected = defaultPersona;
      ($('#persona-select') as HTMLSelectElement).value = defaultPersona.key;
    }
    showToast(`${button.textContent?.trim() ?? action} applied`, false);
    const updates: Array<Promise<void>> = [refreshClientFlag(), refreshHealth()];
    if (action === 'reset') {
      updates.push(refreshPersona());
    }
    if (['reset', 'restore-all', 'target-individual', 'target-employee', 'target-pilot', 'rollout'].includes(action)) {
      updates.push(refreshRollout());
    }
    await Promise.all(updates);
  } catch (error) {
    showToast(error instanceof Error ? error.message : 'Control failed', true);
  } finally {
    setBusy(button, false);
  }
}

function resetControlSelections(): void {
  document.querySelectorAll<HTMLButtonElement>('[data-action].selected').forEach((item) => item.classList.remove('selected'));
  document.querySelector<HTMLButtonElement>('[data-action="rollout"][data-value="0"]')?.classList.add('selected');
  document.querySelector<HTMLButtonElement>('[data-action="migration"][data-value="off"]')?.classList.add('selected');
  document.querySelector<HTMLButtonElement>('[data-action="kill-switch"][data-value="false"]')?.classList.add('selected');
}

function resetJourneyPresentation(): void {
  $('#payment-result').classList.add('hidden');
  $('#payment-panel').classList.remove('hidden');
  ($('#recipient') as HTMLInputElement).value = 'Demo Merchant – Riverside';
  ($('#amount') as HTMLInputElement).value = '1250.00';

  const timeline = $('#timeline');
  timeline.classList.add('empty');
  timeline.replaceChildren();
  const emptyState = document.createElement('div');
  emptyState.className = 'empty-state';
  const symbol = document.createElement('span');
  symbol.textContent = '⌁';
  const heading = document.createElement('strong');
  heading.textContent = 'Run a synthetic payment';
  const explanation = document.createElement('p');
  explanation.textContent = 'Every Java service decision will appear here with source, reason, and fallback status.';
  emptyState.append(symbol, heading, explanation);
  timeline.append(emptyState);
  $('#correlation-id').textContent = 'No journey yet';
}

async function refreshPersona(): Promise<void> {
  if (!state.selected) return;
  $('#nav-persona').textContent = state.selected.key;
  $('#phone-name').textContent = displayName(state.selected.key);
  await state.provider?.identify(state.selected);
  await refreshClientFlag();
}

async function refreshClientFlag(): Promise<void> {
  if (!state.provider) return;
  const decision = await state.provider.evaluate('client-new-payment-ui', false);
  state.clientDecision = decision;
  const chip = $('#ui-version-chip');
  const dot = $('#client-flag-dot');
  const benefit = $('#new-ui-benefit');
  const panel = $('#payment-panel');
  chip.textContent = decision.value ? 'NEW UI' : 'LEGACY UI';
  $('#payment-heading').textContent = decision.value ? 'Smart transfer' : 'Quick transfer';
  dot.className = `flag-dot ${decision.value ? 'on' : 'off'}`;
  dot.title = `${decision.source}: ${decision.reason}`;
  benefit.classList.toggle('hidden', !decision.value);
  panel.classList.toggle('new-experience', decision.value);
}

async function submitJourney(): Promise<void> {
  if (!state.selected) return;
  const button = $('#submit-payment') as HTMLButtonElement;
  setBusy(button, true);
  button.textContent = 'Simulating secure workflow…';
  try {
    const recipientAlias = ($('#recipient') as HTMLInputElement).value.trim();
    const amount = Number(($('#amount') as HTMLInputElement).value);
    const response = await api<JourneyResponse>('/api/journey', {
      method: 'POST',
      body: JSON.stringify({ context: state.selected, recipientAlias, amount })
    });
    $('#result-reference').textContent = response.payment.paymentReference;
    $('#result-version').textContent = response.payment.authoritativeVersion.toUpperCase();
    $('#payment-panel').classList.add('hidden');
    $('#payment-result').classList.remove('hidden');
    renderTimeline(response.timeline, response.correlationId);
    showToast(response.degraded ? 'Journey completed in degraded mode' : 'Synthetic journey completed', response.degraded);
  } catch (error) {
    showToast(error instanceof Error ? error.message : 'Journey failed', true);
  } finally {
    setBusy(button, false);
    button.textContent = 'Review & send';
  }
}

function renderTimeline(events: TimelineEvent[], correlationId: string): void {
  const timeline = $('#timeline');
  timeline.classList.remove('empty');
  timeline.replaceChildren();
  events.forEach((event, index) => {
    const row = document.createElement('div');
    row.className = `timeline-row ${event.degraded ? 'degraded' : ''}`;
    const marker = document.createElement('span');
    marker.className = 'timeline-marker';
    marker.textContent = String(index + 1).padStart(2, '0');
    const copy = document.createElement('div');
    const heading = document.createElement('div');
    heading.className = 'timeline-heading';
    const strong = document.createElement('strong');
    strong.textContent = event.title;
    const source = document.createElement('span');
    source.textContent = event.source;
    heading.append(strong, source);
    const detail = document.createElement('p');
    detail.textContent = event.detail;
    const service = document.createElement('small');
    service.textContent = event.service;
    copy.append(heading, detail, service);
    row.append(marker, copy);
    timeline.append(row);
  });
  $('#correlation-id').textContent = `Trace ${correlationId.slice(0, 8)}`;
}

async function refreshRollout(): Promise<void> {
  try {
    const response = await api<RolloutResponse>('/api/demo/rollout');
    const grid = $('#rollout-grid');
    const fragment = document.createDocumentFragment();
    response.assignments.forEach((assignment) => {
      const cell = document.createElement('span');
      cell.className = assignment.enabled ? 'enabled' : '';
      cell.title = `${assignment.key}: ${assignment.enabled ? 'enabled' : 'safe default'} · ${assignment.reason}`;
      cell.setAttribute('aria-label', `${assignment.key} ${assignment.enabled ? 'enabled' : 'disabled'}`);
      fragment.append(cell);
    });
    grid.replaceChildren(fragment);
    $('#enabled-count').textContent = String(response.enabledCount);
    $('#disabled-count').textContent = `${response.disabledCount} disabled`;
  } catch {
    $('#disabled-count').textContent = 'Rollout data unavailable';
  }
}

async function refreshHealth(): Promise<void> {
  try {
    const response = await api<{ services: Record<string, string> }>('/api/health/services');
    let healthy = 0;
    Object.entries(response.services).forEach(([name, status]) => {
      const node = document.querySelector<HTMLElement>(`[data-service="${name}"]`);
      if (!node) return;
      node.classList.toggle('unavailable', status !== 'healthy');
      const detail = node.querySelector('small');
      if (detail) detail.textContent = status;
      if (status === 'healthy') healthy += 1;
    });
    $('#healthy-count').textContent = `${healthy}/5 healthy`;
  } catch {
    $('#healthy-count').textContent = 'Status unavailable';
  }
}

function setConnection(label: string, connected: boolean): void {
  const connection = $('#connection-label');
  connection.lastChild!.textContent = ` ${label}`;
  connection.classList.toggle('offline', !connected);
}

function showToast(message: string, error: boolean): void {
  const toast = $('#control-toast');
  toast.textContent = message;
  toast.classList.toggle('error', error);
}

function setBusy(button: HTMLButtonElement, busy: boolean): void {
  button.disabled = busy;
  button.classList.toggle('busy', busy);
}

function displayName(key: string): string {
  if (key === 'somchai-employee') return 'Somchai · synthetic';
  if (key === 'mali-pilot') return 'Mali · synthetic';
  return 'Narin · synthetic';
}

window.addEventListener('beforeunload', () => state.provider?.close());
void initialize();
