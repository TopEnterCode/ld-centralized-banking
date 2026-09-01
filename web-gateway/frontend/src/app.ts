import { createClient, type LDClient, type LDContext } from '@launchdarkly/js-client-sdk';
import Observability, { LDObserve } from '@launchdarkly/observability';
import SessionReplay from '@launchdarkly/session-replay';
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

type MaintenanceStatus = {
  configuration: {
    enabled: boolean;
    mode: string;
    title: string;
    message: string;
    eta: string;
  };
  decision: {
    source: string;
    reason: string;
    usedFallback: boolean;
  };
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
  maintenance: MaintenanceStatus;
};

type RolloutResponse = {
  assignments: Array<{ key: string; enabled: boolean; reason: string; source: string }>;
  enabledCount: number;
  disabledCount: number;
};

type MonitoringFlag = {
  flagKey: string;
  label: string;
  type: string;
  owner: string;
  currentValue: string;
  status: string;
  description: string;
};

type MonitoringRow = Record<string, string | number>;

type MonitoringResponse = {
  mode: string;
  syntheticDataOnly: boolean;
  dataSource: string;
  generatedAt: string;
  summary: {
    flagCount: number;
    releaseCount: number;
    errorLogCount: number;
    traceCount: number;
    sessionCount: number;
    errorRate: string;
  };
  flags: MonitoringFlag[];
  history: MonitoringRow[];
  releases: MonitoringRow[];
  errorLogs: MonitoringRow[];
  traces: MonitoringRow[];
  sessions: MonitoringRow[];
};

interface BrowserFlagProvider {
  initialize(context: SyntheticContext): Promise<void>;
  identify(context: SyntheticContext): Promise<void>;
  evaluate(flagKey: string, fallback: boolean): Promise<BrowserDecision>;
  track(eventKey: string, data?: Record<string, string>, metricValue?: number): void;
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

  track(_eventKey: string, _data?: Record<string, string>, _metricValue?: number): void {}

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
      streaming: true,
      plugins: [
        new Observability({
          serviceName: 'banking-poc-browser',
          version: 'synthetic-1.0.0',
          tracingOrigins: true,
          networkRecording: { enabled: true, recordHeadersAndBody: false }
        }),
        new SessionReplay()
      ]
    });
    ['client-new-payment-ui', 'client-new-home-experience'].forEach((flagKey) => {
      this.client?.on(`change:${flagKey}`, () => {
        window.dispatchEvent(new CustomEvent('client-flag-change'));
      });
    });
    this.client.start();
    const result = await this.client.waitForInitialization({ timeout: 3 });
    if (result.status !== 'complete') {
      throw new Error(`LaunchDarkly initialization ${result.status}`);
    }
    LDObserve.recordLog('Synthetic banking POC session initialized', 'info');
    LDObserve.recordIncr({ name: 'synthetic_sessions_started' });
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

  track(eventKey: string, data?: Record<string, string>, metricValue?: number): void {
    this.client?.track(eventKey, data, metricValue);
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
  homeDecision?: BrowserDecision;
  maintenance?: MaintenanceStatus;
  mobileView: 'home' | 'transfer' | 'maintenance';
  monitoring?: MonitoringResponse;
  monitoringFilter: string;
} = { personas: [], mobileView: 'home', monitoringFilter: 'all' };
let journeyGeneration = 0;

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
  bindMonitoring();
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
    await Promise.all([refreshPersona(), refreshHealth(), refreshRollout(), refreshMonitoring()]);
    window.setInterval(() => void refreshHealth(), 7000);
    window.setInterval(() => void refreshMaintenance(), 5000);
    window.setInterval(() => void refreshMonitoring(), 15000);
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
  $('#maintenance-backend-check').addEventListener('click', () => void submitJourney(true));
  $('#open-transfer').addEventListener('click', () => {
    showMobileView(state.maintenance?.configuration.enabled ? 'maintenance' : 'transfer');
  });
  $('#back-home').addEventListener('click', () => showMobileView('home'));
  $('#maintenance-home').addEventListener('click', () => showMobileView('home'));
  $('#maintenance-home-action').addEventListener('click', () => showMobileView('home'));
  document.querySelectorAll<HTMLButtonElement>('.demo-tile, .demo-nav').forEach((button) => {
    button.addEventListener('click', () => showToast('Synthetic demo service — select Transfer to continue', false));
  });
  $('#new-payment').addEventListener('click', () => {
    $('#payment-result').classList.add('hidden');
    $('#payment-panel').classList.remove('hidden');
    showMobileView('transfer');
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
    if (action === 'maintenance') {
      resetJourneyPresentation();
    }
    showToast(`${button.textContent?.trim() ?? action} applied`, false);
    const updates: Array<Promise<void>> = [refreshClientFlag(), refreshHealth(), refreshMonitoring()];
    if (action === 'reset') {
      updates.push(refreshPersona());
    }
    if (['reset', 'restore-all', 'target-individual', 'target-employee', 'target-pilot', 'rollout'].includes(action)) {
      updates.push(refreshRollout());
    }
    if (action === 'maintenance' || action === 'restore-all') {
      updates.push(refreshMaintenance());
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
  document.querySelector<HTMLButtonElement>('[data-action="maintenance"][data-value="false"]')?.classList.add('selected');
}

function bindMonitoring(): void {
  $('#monitoring-flag-filter').addEventListener('change', (event) => {
    state.monitoringFilter = (event.target as HTMLSelectElement).value;
    renderMonitoring();
  });
  $('#send-synthetic-error').addEventListener('click', () => void emitSyntheticError());
  $('#send-synthetic-log').addEventListener('click', () => void emitSyntheticLog());
}

function emitSyntheticError(): void {
  const button = $('#send-synthetic-error') as HTMLButtonElement;
  if (state.runtime?.mode !== 'launchdarkly') {
    showToast('Switch to LaunchDarkly Live mode to emit an external error', true);
    return;
  }
  setBusy(button, true);
  try {
    const syntheticError = new Error('Synthetic payment review telemetry check');
    LDObserve.recordError(syntheticError, 'Synthetic error for demo', {
      flag_key: 'client-new-payment-ui',
      synthetic: 'true'
    });
    state.provider?.track(
      'user-error-rate',
      { flag_key: 'client-new-payment-ui', synthetic: 'true' },
      1
    );
    LDObserve.recordIncr({ name: 'synthetic_monitoring_errors' });
    window.setTimeout(() => {
      throw syntheticError;
    }, 0);
    showToast('Synthetic ERROR queued for LaunchDarkly', false);
  } catch (error) {
    showToast(error instanceof Error ? error.message : 'Unable to emit synthetic error', true);
  } finally {
    setBusy(button, false);
  }
}

function emitSyntheticLog(): void {
  const button = $('#send-synthetic-log') as HTMLButtonElement;
  if (state.runtime?.mode !== 'launchdarkly') {
    showToast('Switch to LaunchDarkly Live mode to emit an external log', true);
    return;
  }
  setBusy(button, true);
  try {
    LDObserve.recordLog('Synthetic payment log for monitoring demo', 'info', {
      flag_key: 'client-new-payment-ui',
      synthetic: 'true'
    });
    LDObserve.recordIncr({ name: 'synthetic_monitoring_logs' });
    showToast('Synthetic LOG queued for LaunchDarkly', false);
  } catch (error) {
    showToast(error instanceof Error ? error.message : 'Unable to emit synthetic log', true);
  } finally {
    setBusy(button, false);
  }
}

function resetJourneyPresentation(): void {
  journeyGeneration += 1;
  $('#payment-result').classList.add('hidden');
  $('#payment-result').classList.remove('maintenance-result');
  $('#payment-panel').classList.remove('hidden');
  $('#result-heading').textContent = 'Payment simulated';
  $('#result-icon').textContent = '✓';
  $('#result-route-label').textContent = 'Authoritative API';
  $('#result-version').textContent = 'v1';
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
  showMobileView('home');
}

function showMobileView(view: 'home' | 'transfer' | 'maintenance'): void {
  state.mobileView = view;
  $('#mobile-home').classList.toggle('hidden', view !== 'home');
  $('#transfer-view').classList.toggle('hidden', view !== 'transfer');
  $('#mobile-maintenance').classList.toggle('hidden', view !== 'maintenance');
}

async function refreshPersona(): Promise<void> {
  if (!state.selected) return;
  $('#nav-persona').textContent = state.selected.key;
  $('#phone-name').textContent = displayName(state.selected.key);
  await state.provider?.identify(state.selected);
  await refreshMaintenance();
  await refreshClientFlag();
}

async function refreshMaintenance(): Promise<void> {
  if (!state.selected) return;
  try {
    state.maintenance = await api<MaintenanceStatus>(
      `/api/maintenance/${encodeURIComponent(state.selected.key)}`
    );
    renderMaintenance(state.maintenance);
  } catch {
    const configuration = {
      enabled: false,
      mode: 'read-only',
      title: 'Maintenance status unavailable',
      message: 'The UI is using the safe default and remains available.',
      eta: 'Status check unavailable'
    };
    state.maintenance = { configuration, decision: { source: 'service-fallback', reason: 'Gateway unavailable', usedFallback: true } };
    renderMaintenance(state.maintenance);
  }
}

function renderMaintenance(status: MaintenanceStatus): void {
  const configuration = status.configuration;
  const banner = $('#maintenance-banner');
  banner.classList.toggle('hidden', !configuration.enabled);
  $('#maintenance-title').textContent = configuration.title;
  $('#maintenance-message').textContent = configuration.message;
  $('#maintenance-eta').textContent = configuration.eta;
  $('#mobile-maintenance-title').textContent = configuration.title;
  $('#mobile-maintenance-message').textContent = configuration.message;
  $('#mobile-maintenance-eta').textContent = configuration.eta;
  $('#home-maintenance-hint').classList.toggle('hidden', !configuration.enabled);
  if (configuration.enabled) {
    showMobileView('maintenance');
  } else if (state.mobileView === 'maintenance') {
    showMobileView('home');
  }
  const submit = $('#submit-payment') as HTMLButtonElement;
  submit.disabled = configuration.enabled;
  submit.title = configuration.enabled
    ? 'Transfers are blocked by maintenance mode'
    : 'Review and send a synthetic payment';
  const check = $('#maintenance-backend-check') as HTMLButtonElement;
  check.classList.toggle('hidden', !configuration.enabled);
}

async function refreshClientFlag(): Promise<void> {
  if (!state.provider) return;
  const [decision, homeDecision] = await Promise.all([
    state.provider.evaluate('client-new-payment-ui', false),
    state.provider.evaluate('client-new-home-experience', false)
  ]);
  state.clientDecision = decision;
  state.homeDecision = homeDecision;
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
  const home = $('#mobile-home');
  home.classList.toggle('personalized-home', homeDecision.value);
  $('#home-experience-label').textContent = homeDecision.value
    ? 'PERSONALIZED HOME'
    : 'STANDARD HOME';
  $('#offer-kicker').textContent = homeDecision.value ? 'แนะนำสำหรับคุณ' : 'สิทธิพิเศษ';
  $('#offer-title').textContent = homeDecision.value
    ? 'ข้อเสนอที่คัดสรร\nสำหรับคุณ'
    : 'สำหรับผู้ใช้บริการผ่านธนาคารออนไลน์';
  $('#offer-link').textContent = homeDecision.value ? 'ดูข้อเสนอของคุณ >>' : 'ดูรายละเอียด >>';
}

async function submitJourney(backendGuardCheck = false): Promise<void> {
  if (!state.selected) return;
  const requestGeneration = journeyGeneration;
  const button = (backendGuardCheck ? $('#maintenance-backend-check') : $('#submit-payment')) as HTMLButtonElement;
  setBusy(button, true);
  const originalText = button.textContent ?? '';
  button.textContent = 'Checking policy…';
  try {
    const recipientAlias = ($('#recipient') as HTMLInputElement).value.trim();
    const amount = Number(($('#amount') as HTMLInputElement).value);
    const response = await api<JourneyResponse>('/api/journey', {
      method: 'POST',
      body: JSON.stringify({ context: state.selected, recipientAlias, amount })
    });
    if (requestGeneration !== journeyGeneration) return;
    const maintenance = response.maintenance?.configuration.enabled === true;
    $('#result-reference').textContent = response.payment.paymentReference;
    $('#result-version').textContent = maintenance
      ? 'PAUSED'
      : response.payment.authoritativeVersion.toUpperCase();
    $('#result-heading').textContent = maintenance ? 'Maintenance mode' : 'Payment simulated';
    $('#result-icon').textContent = maintenance ? '!' : '✓';
    $('#result-route-label').textContent = maintenance ? 'Workflow status' : 'Authoritative API';
    $('#payment-result').classList.toggle('maintenance-result', maintenance);
    showMobileView('transfer');
    $('#payment-panel').classList.add('hidden');
    $('#payment-result').classList.remove('hidden');
    renderTimeline(response.timeline, response.correlationId);
    showToast(
      maintenance
        ? 'Backend guard blocked the synthetic transfer'
        : response.degraded
          ? 'Journey completed in degraded mode'
          : 'Synthetic journey completed',
      response.degraded
    );
  } catch (error) {
    showToast(error instanceof Error ? error.message : 'Journey failed', true);
  } finally {
    setBusy(button, false);
    button.textContent = originalText;
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

async function refreshMonitoring(): Promise<void> {
  try {
    state.monitoring = await api<MonitoringResponse>('/api/monitoring');
    const filter = $('#monitoring-flag-filter') as HTMLSelectElement;
    const knownKeys = new Set(Array.from(filter.options).map((option) => option.value));
    state.monitoring.flags.forEach((flag) => {
      if (knownKeys.has(flag.flagKey)) return;
      const option = document.createElement('option');
      option.value = flag.flagKey;
      option.textContent = flag.label;
      filter.append(option);
    });
    filter.value = state.monitoringFilter;
    renderMonitoring();
  } catch {
    $('#monitoring-source').textContent = 'Monitoring unavailable';
  }
}

function renderMonitoring(): void {
  const data = state.monitoring;
  if (!data) return;
  const summary = data.summary;
  $('#monitoring-flag-count').textContent = String(summary.flagCount);
  $('#monitoring-release-count').textContent = String(summary.releaseCount);
  $('#monitoring-error-count').textContent = String(summary.errorLogCount);
  $('#monitoring-trace-count').textContent = String(summary.traceCount);
  $('#monitoring-session-count').textContent = String(summary.sessionCount);
  $('#monitoring-error-rate').textContent = summary.errorRate;
  $('#monitoring-source').textContent = `${data.dataSource} · ${data.mode}`;
  $('#monitoring-generated-at').textContent = formatMonitoringTime(data.generatedAt);

  renderFlagInventory(data.flags);
  renderMonitoringRows('#flag-history-list', data.history, ['flagKey', 'change', 'from', 'to', 'actor', 'timestamp']);
  renderMonitoringRows('#release-list', data.releases, ['release', 'flagKey', 'rollout', 'health', 'deployedAt']);
  renderMonitoringRows('#error-log-list', data.errorLogs, ['level', 'flagKey', 'service', 'message', 'timestamp']);
  renderMonitoringRows('#trace-list', data.traces, ['traceId', 'flagKey', 'service', 'durationMs', 'status']);
  renderMonitoringRows('#session-list', data.sessions, ['sessionId', 'persona', 'flagKey', 'variation', 'platform', 'events']);
}

function renderFlagInventory(flags: MonitoringFlag[]): void {
  const grid = $('#flag-monitor-grid');
  const visible = flags.filter((flag) => state.monitoringFilter === 'all' || flag.flagKey === state.monitoringFilter);
  grid.replaceChildren();
  visible.forEach((flag) => {
    const card = document.createElement('article');
    card.className = `flag-monitor-item ${flag.status}`;
    const heading = document.createElement('div');
    heading.className = 'flag-monitor-heading';
    const label = document.createElement('strong');
    label.textContent = flag.label;
    const status = document.createElement('span');
    status.textContent = flag.status;
    heading.append(label, status);
    const key = document.createElement('code');
    key.textContent = flag.flagKey;
    const meta = document.createElement('small');
    meta.textContent = `${flag.type} · ${flag.owner}`;
    const value = document.createElement('p');
    value.textContent = `Current safe value: ${flag.currentValue}`;
    card.append(heading, key, meta, value);
    grid.append(card);
  });
}

function renderMonitoringRows(containerSelector: string, rows: MonitoringRow[], fields: string[]): void {
  const container = $(containerSelector);
  const visible = rows.filter(
    (row) => state.monitoringFilter === 'all' || row.flagKey === state.monitoringFilter
  );
  container.replaceChildren();
  visible.forEach((row) => {
    const item = document.createElement('div');
    item.className = 'monitor-table-row';
    fields.forEach((field) => {
      const cell = document.createElement('span');
      cell.dataset.field = field;
      cell.textContent = String(row[field] ?? '—');
      if (field === 'flagKey') cell.classList.add('flag-key-cell');
      if (field === 'level') cell.classList.add(`log-${String(row[field]).toLowerCase()}`);
      if (field === 'status') cell.classList.add(`trace-${String(row[field]).toLowerCase()}`);
      item.append(cell);
    });
    container.append(item);
  });
}

function formatMonitoringTime(value: string): string {
  return `Snapshot ${value.replace('T', ' ').replace('Z', ' UTC')}`;
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
