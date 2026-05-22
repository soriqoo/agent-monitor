const summaryCards = document.getElementById("summaryCards");
const servicesList = document.getElementById("servicesList");
const serviceDetailPanel = document.getElementById("serviceDetailPanel");
const checkHistoryList = document.getElementById("checkHistoryList");
const incidentHistoryList = document.getElementById("incidentHistoryList");
const alertHistoryList = document.getElementById("alertHistoryList");
const form = document.getElementById("serviceForm");
const formTitle = document.getElementById("formTitle");
const probeButton = document.getElementById("probeButton");
const submitButton = document.getElementById("submitButton");
const resetButton = document.getElementById("resetButton");
const refreshButton = document.getElementById("refreshButton");
const formMessage = document.getElementById("formMessage");
const lastRefreshText = document.getElementById("lastRefreshText");

const serviceIdInput = document.getElementById("serviceId");
const serviceNameInput = document.getElementById("serviceName");
const baseUrlInput = document.getElementById("baseUrl");
const environmentInput = document.getElementById("environment");
const enabledInput = document.getElementById("enabled");

const HISTORY_PREVIEW_COUNT = 2;
const PROBE_BUTTON_TEXT = "Test connection";

let serviceRows = [];
let selectedServiceId = null;
let historyState = {
  incidentsExpanded: false,
  alertsExpanded: false
};
let latestHistory = {
  incidents: [],
  alerts: []
};

async function fetchJson(url, options = {}) {
  const response = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });

  if (!response.ok) {
    let message = `${response.status} ${response.statusText}`;
    try {
      const errorBody = await response.json();
      if (errorBody.message) {
        message = errorBody.message;
      }
    } catch (_error) {
      // keep fallback message
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

function renderSummary(summary) {
  const cards = [
    ["Registered services", summary.registeredServices],
    ["Enabled services", summary.enabledServices],
    ["Open incidents", summary.openIncidents],
    ["Monitor status", summary.status],
    ["Retention cleanup", renderRetentionSummary(summary.retention)]
  ];

  summaryCards.innerHTML = cards.map(([label, value]) => `
    <article class="summary-card ${label === "Monitor status" ? "status-card" : ""} ${label === "Retention cleanup" ? "retention-card" : ""}">
      <span class="label">${label}</span>
      <div class="value">${label === "Monitor status" ? `<span class="status-pill">${value}</span>` : value}</div>
    </article>
  `).join("");
}

function renderRetentionSummary(retention) {
  const lastRun = retention?.lastRun;

  if (!lastRun) {
    return `
      <span class="retention-empty">No cleanup yet</span>
      <button class="retention-run-button" type="button" data-action="run-retention-cleanup">
        Run cleanup
      </button>
    `;
  }

  const deletedTotal =
    Number(lastRun.deletedServiceChecks || 0) +
    Number(lastRun.deletedAlertEvents || 0) +
    Number(lastRun.deletedResolvedIncidents || 0);

  return `
    <span class="status-pill ${String(lastRun.status || "").toLowerCase()}">${escapeHtml(lastRun.status || "UNKNOWN")}</span>
    <span class="retention-meta">${escapeHtml(formatDateTime(lastRun.completedAt || lastRun.startedAt))}</span>
    <span class="retention-count">${deletedTotal} rows pruned</span>
    <button class="retention-run-button" type="button" data-action="run-retention-cleanup">
      Run cleanup
    </button>
  `;
}

function badgeClass(status) {
  switch ((status || "").toUpperCase()) {
    case "UP":
      return "up";
    case "DEGRADED":
      return "degraded";
    case "DOWN":
      return "down";
    case "UNKNOWN":
      return "unknown";
    default:
      return "muted";
  }
}

function renderServices(rows) {
  serviceRows = rows;

  if (!rows.length) {
    servicesList.innerHTML = `
      <article class="empty-state">
        <strong>No monitored services registered yet.</strong>
        <span>Add a service from the form to start polling and incident tracking.</span>
      </article>
    `;
    return;
  }

  servicesList.innerHTML = rows.map((row) => `
    <article class="service-card ${selectedServiceId === row.id ? "is-selected" : ""}" data-service-id="${row.id}">
      <div class="service-card-top">
        <div class="service-meta">
          <div class="service-title-row">
            <span class="service-name">${escapeHtml(row.serviceName)}</span>
            <span class="service-environment">${escapeHtml(row.environment)}</span>
          </div>
          <span class="service-subline">${row.enabled ? "Enabled for polling" : "Disabled from polling"}</span>
        </div>
        <div class="row-actions">
          <button class="check-button" type="button" data-action="check-now" data-id="${row.id}">
            Check now
          </button>
          <button class="toggle-button ${row.enabled ? "is-enabled" : "is-disabled"}" type="button" data-action="toggle-enabled" data-id="${row.id}">
            ${row.enabled ? "Disable" : "Enable"}
          </button>
          <button class="small-button" type="button" data-action="edit" data-id="${row.id}">Edit</button>
          <button class="danger-button" type="button" data-action="delete" data-id="${row.id}">Delete</button>
        </div>
      </div>

      <div class="service-status-strip">
        <div class="status-group">
          <span class="metric-label">Health</span>
          <span class="badge ${badgeClass(row.healthStatus)}">${escapeHtml(row.healthStatus || "UNSEEN")}</span>
        </div>
        <div class="status-group">
          <span class="metric-label">Run</span>
          <span class="badge muted">${escapeHtml(row.runStatus || "N/A")}</span>
        </div>
        <div class="status-group">
          <span class="metric-label">Incident</span>
          <span class="badge ${row.openIncident ? "down" : "up"}">${row.openIncident ? "OPEN" : "CLEAR"}</span>
        </div>
        <div class="status-group">
          <span class="metric-label">Last run</span>
          <span class="metric-value">${escapeHtml(row.lastRunDate || "-")}</span>
        </div>
      </div>

      <div class="service-details-grid">
        <div class="detail-block detail-wide">
          <span class="metric-label">Base URL</span>
          <span class="service-url">${escapeHtml(row.baseUrl)}</span>
        </div>
        <div class="detail-block">
          <span class="metric-label">Last checked</span>
          <span class="metric-value">${escapeHtml(formatTimestamp(row.lastCheckedAt))}</span>
        </div>
      </div>

      ${row.error ? `
        <div class="service-error-box">
          <span class="metric-label">Latest error</span>
          <span class="service-error">${escapeHtml(row.error)}</span>
        </div>
      ` : ""}
    </article>
  `).join("");
}

function renderServiceDetail(detail) {
  const service = detail.service;
  const checks = detail.checks || [];
  const incidents = detail.incidents || [];
  const alerts = detail.alerts || [];

  serviceDetailPanel.innerHTML = `
    <article class="detail-shell">
      <div class="detail-summary-card">
        <div class="detail-summary-top">
          <div class="detail-title-block">
            <div class="service-title-row">
              <span class="service-name">${escapeHtml(service.serviceName)}</span>
              <span class="service-environment">${escapeHtml(service.environment)}</span>
            </div>
            <span class="service-subline">${service.enabled ? "Enabled for polling" : "Disabled from polling"}</span>
          </div>
          <span class="badge ${service.openIncident ? "down" : "up"}">${service.openIncident ? "OPEN INCIDENT" : "CLEAR"}</span>
        </div>

        <div class="detail-metric-strip">
          <div class="status-group">
            <span class="metric-label">Health</span>
            <span class="badge ${badgeClass(service.healthStatus)}">${escapeHtml(service.healthStatus || "UNSEEN")}</span>
          </div>
          <div class="status-group">
            <span class="metric-label">Run</span>
            <span class="badge muted">${escapeHtml(service.runStatus || "N/A")}</span>
          </div>
          <div class="status-group">
            <span class="metric-label">Last run</span>
            <span class="metric-value">${escapeHtml(service.lastRunDate || "-")}</span>
          </div>
          <div class="status-group">
            <span class="metric-label">Last checked</span>
            <span class="metric-value">${escapeHtml(formatTimestamp(service.lastCheckedAt))}</span>
          </div>
        </div>

        <div class="detail-summary-grid">
          <div class="detail-block detail-wide">
            <span class="metric-label">Base URL</span>
            <span class="service-url">${escapeHtml(service.baseUrl)}</span>
          </div>
          <div class="detail-block">
            <span class="metric-label">Recent checks</span>
            <span class="metric-value">${checks.length}</span>
          </div>
          <div class="detail-block">
            <span class="metric-label">Recent incidents</span>
            <span class="metric-value">${incidents.length}</span>
          </div>
          <div class="detail-block">
            <span class="metric-label">Recent alerts</span>
            <span class="metric-value">${alerts.length}</span>
          </div>
        </div>

        ${service.error ? `
          <div class="service-error-box">
            <span class="metric-label">Latest error</span>
            <span class="service-error">${escapeHtml(service.error)}</span>
          </div>
        ` : ""}
      </div>

      <div class="detail-history-grid">
        <section class="detail-history-column">
          <div class="history-column-header">
            <h3>Recent Checks</h3>
            <span>${checks.length ? `Latest ${checks.length} checks` : "No recent checks"}</span>
          </div>
          <div class="history-list">
            ${checks.length ? checks.map((item) => `
              <article class="history-item detail-history-item">
                <div class="history-item-top">
                  <div class="history-title-block">
                    <span class="history-title">${escapeHtml(formatTimestamp(item.checkedAt))}</span>
                    <span class="history-subtitle">${escapeHtml(item.responseTimeMs != null ? `${item.responseTimeMs} ms` : "Response time unavailable")}</span>
                  </div>
                  <span class="badge ${badgeClass(item.healthStatus)}">${escapeHtml(item.healthStatus)}</span>
                </div>
                <div class="detail-check-metrics">
                  <div class="alert-metric-card">
                    <span class="metric-label">Run</span>
                    <span class="alert-metric-value">${escapeHtml(item.runStatus || "N/A")}</span>
                  </div>
                  <div class="alert-metric-card">
                    <span class="metric-label">Last run</span>
                    <span class="alert-metric-value">${escapeHtml(item.lastRunDate || "-")}</span>
                  </div>
                </div>
                ${item.error ? `
                  <div class="alert-error-box">
                    <span class="metric-label">Error</span>
                    <p class="history-copy">${escapeHtml(item.error)}</p>
                  </div>
                ` : ""}
              </article>
            `).join("") : renderEmptyHistory("No checks for this service yet.", "The latest polling results will appear here.")}
          </div>
        </section>

        <section class="detail-history-column">
          <div class="history-column-header">
            <h3>Service Incidents</h3>
            <span>${incidents.length ? `Latest ${incidents.length} records` : "No recent incidents"}</span>
          </div>
          <div class="history-list">
            ${incidents.length ? incidents.map((item) => `
              <article class="history-item detail-history-item">
                <div class="history-item-top">
                  <div class="history-title-block">
                    <span class="history-title">${escapeHtml(item.status === "OPEN" ? "Incident opened" : "Incident resolved")}</span>
                    <span class="history-subtitle">${escapeHtml(formatDateTime(item.openedAt))}</span>
                  </div>
                  <span class="badge ${item.status === "OPEN" ? "down" : "up"}">${escapeHtml(item.status)}</span>
                </div>
                <div class="history-meta-lines">
                  ${item.resolvedAt ? `<span>Resolved ${escapeHtml(formatDateTime(item.resolvedAt))}</span>` : `<span>Still open</span>`}
                </div>
                ${renderHistoryCopy(item.lastError)}
              </article>
            `).join("") : renderEmptyHistory("No incidents for this service yet.", "Incident lifecycle entries will appear here.")}
          </div>
        </section>

        <section class="detail-history-column">
          <div class="history-column-header">
            <h3>Service Alerts</h3>
            <span>${alerts.length ? `Latest ${alerts.length} deliveries` : "No recent alerts"}</span>
          </div>
          <div class="history-list">
            ${alerts.length ? alerts.map((item) => `
              <article class="history-item detail-history-item">
                <div class="history-item-top">
                  <div class="history-title-block">
                    <span class="history-title">${escapeHtml(item.serviceName)}</span>
                    <span class="history-subtitle">${escapeHtml(formatDateTime(item.sentAt))}</span>
                  </div>
                  <span class="badge muted">${escapeHtml(formatAlertType(item.alertType))}</span>
                </div>
                ${renderAlertMessage(item.message)}
              </article>
            `).join("") : renderEmptyHistory("No alerts for this service yet.", "Successful alert deliveries will appear here.")}
          </div>
        </section>
      </div>
    </article>
  `;
}

function renderHistory(history) {
  latestHistory = {
    incidents: history.incidents || [],
    alerts: history.alerts || []
  };
  renderIncidentHistory(latestHistory.incidents);
  renderAlertHistory(latestHistory.alerts);
}

function renderCheckHistory(items) {
  if (!items.length) {
    checkHistoryList.innerHTML = renderEmptyHistory(
      "No checks recorded yet.",
      "Polling and manual check results will appear here."
    );
    return;
  }

  checkHistoryList.innerHTML = items.map((item) => `
    <article class="history-item">
      <div class="history-item-top">
        <div class="history-title-block">
          <span class="history-title">${escapeHtml(item.serviceName)}</span>
          <span class="history-subtitle">${escapeHtml(item.environment)} / ${escapeHtml(formatTimestamp(item.checkedAt))}</span>
        </div>
        <span class="badge ${badgeClass(item.healthStatus)}">${escapeHtml(item.healthStatus)}</span>
      </div>
      <div class="detail-check-metrics">
        <div class="alert-metric-card">
          <span class="metric-label">Run</span>
          <span class="alert-metric-value">${escapeHtml(item.runStatus || "N/A")}</span>
        </div>
        <div class="alert-metric-card">
          <span class="metric-label">Latency</span>
          <span class="alert-metric-value">${escapeHtml(item.responseTimeMs != null ? `${item.responseTimeMs} ms` : "-")}</span>
        </div>
      </div>
      ${item.error ? `
        <div class="alert-error-box">
          <span class="metric-label">Error</span>
          <p class="history-copy">${escapeHtml(item.error)}</p>
        </div>
      ` : ""}
    </article>
  `).join("");
}

function renderIncidentHistory(items) {
  if (!items.length) {
    incidentHistoryList.innerHTML = renderEmptyHistory(
      "No incidents recorded yet.",
      "New incidents and recoveries will appear here."
    );
    return;
  }

  const visibleItems = historyState.incidentsExpanded ? items : items.slice(0, HISTORY_PREVIEW_COUNT);

  incidentHistoryList.innerHTML = `
    ${visibleItems.map((item) => `
    <article class="history-item">
      <div class="history-item-top">
        <div class="history-title-block">
          <span class="history-title">${escapeHtml(item.serviceName)}</span>
          <span class="history-subtitle">${escapeHtml(item.environment)}</span>
        </div>
        <span class="badge ${item.status === "OPEN" ? "down" : "up"}">${escapeHtml(item.status)}</span>
      </div>
      <div class="history-meta-lines">
        <span>Opened ${escapeHtml(formatDateTime(item.openedAt))}</span>
        ${item.resolvedAt ? `<span>Resolved ${escapeHtml(formatDateTime(item.resolvedAt))}</span>` : `<span>Still open</span>`}
      </div>
      ${renderHistoryCopy(item.lastError)}
    </article>
  `).join("")}
    ${renderHistoryListToggle(items.length, historyState.incidentsExpanded, "incidents")}
  `;
}

function renderAlertHistory(items) {
  if (!items.length) {
    alertHistoryList.innerHTML = renderEmptyHistory(
      "No alert deliveries recorded yet.",
      "Successful Slack alert sends will appear here."
    );
    return;
  }

  const visibleItems = historyState.alertsExpanded ? items : items.slice(0, HISTORY_PREVIEW_COUNT);

  alertHistoryList.innerHTML = `
    ${visibleItems.map((item) => `
    <article class="history-item">
      <div class="history-item-top">
        <div class="history-title-block">
          <span class="history-title">${escapeHtml(item.serviceName)}</span>
          <span class="history-subtitle">${escapeHtml(item.environment)}</span>
        </div>
        <span class="badge muted">${escapeHtml(formatAlertType(item.alertType))}</span>
      </div>
      <div class="history-meta-lines">
        <span>Sent ${escapeHtml(formatDateTime(item.sentAt))}</span>
      </div>
      ${renderAlertMessage(item.message)}
    </article>
  `).join("")}
    ${renderHistoryListToggle(items.length, historyState.alertsExpanded, "alerts")}
  `;
}

function renderEmptyHistory(title, copy) {
  return `
    <article class="empty-state history-empty-state">
      <strong>${escapeHtml(title)}</strong>
      <span>${escapeHtml(copy)}</span>
    </article>
  `;
}

function renderHistoryCopy(value) {
  if (!value) {
    return "";
  }

  const message = String(value).trim();
  const escaped = escapeHtml(message);

  if (message.length <= 140) {
    return `<p class="history-copy">${escaped}</p>`;
  }

  return `
    <div class="history-copy-block" data-copy-state="collapsed">
      <p class="history-copy history-copy-clamped">${escaped}</p>
      <button class="history-toggle-button" type="button" data-action="toggle-history-copy">
        Show full message
      </button>
    </div>
  `;
}

function renderAlertMessage(value) {
  if (!value) {
    return "";
  }

  const parsed = parseAlertMessage(value);
  const metricEntries = [
    ["Health", parsed.healthStatus],
    ["Run", parsed.runStatus],
    ["Last run", parsed.lastRunDate],
    ["Checked", parsed.checkedAt ? formatDateTime(parsed.checkedAt) : ""]
  ].filter(([, fieldValue]) => fieldValue && fieldValue !== "null");

  return `
    <div class="alert-copy-block">
      <p class="alert-lede">${escapeHtml(parsed.summary)}</p>
      ${metricEntries.length ? `
        <div class="alert-metric-grid">
          ${metricEntries.map(([label, fieldValue]) => `
            <div class="alert-metric-card">
              <span class="metric-label">${escapeHtml(label)}</span>
              <span class="alert-metric-value">${escapeHtml(fieldValue)}</span>
            </div>
          `).join("")}
        </div>
      ` : ""}
      ${parsed.error && parsed.error !== "null" ? `
        <div class="alert-error-box">
          <span class="metric-label">Error</span>
          <p class="history-copy">${escapeHtml(parsed.error)}</p>
        </div>
      ` : ""}
      ${renderRawAlertMessage(value)}
    </div>
  `;
}

function renderRawAlertMessage(value) {
  const message = String(value).trim();
  const escaped = escapeHtml(message);

  return `
    <div class="history-copy-block raw-alert-block" data-copy-state="collapsed">
      <button class="history-toggle-button raw-alert-toggle" type="button" data-action="toggle-history-copy">
        View raw message
      </button>
      <pre class="history-copy raw-alert-copy">${escaped}</pre>
    </div>
  `;
}

function parseAlertMessage(value) {
  const lines = String(value)
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);

  const summary = lines[0]
    ?.replace(/^:[^:]+:\s*/, "")
    ?.replace(/\s+/g, " ")
    ?.trim() || "Alert event";

  const fields = {};

  for (const line of lines.slice(1)) {
    const match = line.match(/^-\s*([^:]+):\s*(.*)$/);
    if (!match) {
      continue;
    }

    const key = match[1].trim();
    const normalizedKey = key.charAt(0).toLowerCase() + key.slice(1);
    fields[normalizedKey] = match[2].trim();
  }

  return {
    summary,
    healthStatus: fields.healthStatus || "",
    runStatus: fields.runStatus || "",
    lastRunDate: fields.lastRunDate || "",
    checkedAt: fields.checkedAt || "",
    error: fields.error || ""
  };
}

function renderHistoryListToggle(totalCount, expanded, section) {
  if (totalCount <= HISTORY_PREVIEW_COUNT) {
    return "";
  }

  return `
    <button class="history-list-toggle" type="button" data-action="toggle-history-list" data-section="${section}">
      ${expanded ? "Show fewer items" : `View all ${totalCount} items`}
    </button>
  `;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function setFormMessage(message, type = "") {
  formMessage.textContent = message;
  formMessage.className = `message ${type}`.trim();
}

function resetForm() {
  serviceIdInput.value = "";
  form.reset();
  enabledInput.checked = true;
  formTitle.textContent = "Register Service";
  submitButton.textContent = "Create service";
  setFormMessage("");
}

function fillForm(row) {
  serviceIdInput.value = row.id;
  serviceNameInput.value = row.serviceName;
  baseUrlInput.value = row.baseUrl;
  environmentInput.value = row.environment;
  enabledInput.checked = row.enabled;
  formTitle.textContent = `Edit ${row.serviceName}`;
  submitButton.textContent = "Update service";
  setFormMessage("");
}

function formatTimestamp(value) {
  if (!value) {
    return "-";
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(parsed);
}

function formatDateTime(value) {
  if (!value) {
    return "-";
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(parsed);
}

function formatAlertType(value) {
  return String(value || "ALERT")
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

async function loadDashboard() {
  setFormMessage("");

  const [summary, overview, history, checks] = await Promise.all([
    fetchJson("/internal/monitoring/summary"),
    fetchJson("/api/monitored-services/overview"),
    fetchJson("/api/monitoring/history?limit=6"),
    fetchJson("/api/monitoring/checks?limit=4")
  ]);

  renderSummary(summary);
  renderCheckHistory(checks);
  renderHistory(history);

  if (!overview.length) {
    selectedServiceId = null;
    renderServices(overview);
    serviceDetailPanel.innerHTML = renderEmptyHistory(
      "No monitored services registered yet.",
      "Add a service to inspect focused history and status."
    );
  } else {
    const hasSelected = overview.some((item) => item.id === selectedServiceId);
    selectedServiceId = hasSelected ? selectedServiceId : overview[0].id;
    renderServices(overview);
    await loadServiceDetail(selectedServiceId);
  }

  lastRefreshText.textContent = new Intl.DateTimeFormat(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  }).format(new Date());
}

async function loadServiceDetail(serviceId) {
  const detail = await fetchJson(`/api/monitored-services/${serviceId}/detail?limit=4`);
  renderServiceDetail(detail);
}

async function submitForm(event) {
  event.preventDefault();

  const payload = {
    serviceName: serviceNameInput.value.trim(),
    baseUrl: baseUrlInput.value.trim(),
    environment: environmentInput.value.trim(),
    enabled: enabledInput.checked
  };

  try {
    if (serviceIdInput.value) {
      await fetchJson(`/api/monitored-services/${serviceIdInput.value}`, {
        method: "PUT",
        body: JSON.stringify(payload)
      });
      setFormMessage("Monitored service updated.", "success");
    } else {
      await fetchJson("/api/monitored-services", {
        method: "POST",
        body: JSON.stringify(payload)
      });
      setFormMessage("Monitored service created.", "success");
    }

    resetForm();
    await loadDashboard();
  } catch (error) {
    setFormMessage(error.message, "error");
  }
}

function currentFormPayload() {
  return {
    serviceName: serviceNameInput.value.trim(),
    baseUrl: baseUrlInput.value.trim(),
    environment: environmentInput.value.trim(),
    enabled: enabledInput.checked
  };
}

async function probeServiceConnection() {
  const payload = currentFormPayload();

  if (!payload.serviceName || !payload.baseUrl || !payload.environment) {
    setFormMessage("Enter service name, base URL, and environment before testing.", "error");
    return;
  }

  const originalText = probeButton.textContent || PROBE_BUTTON_TEXT;
  probeButton.disabled = true;
  probeButton.textContent = "Testing...";
  setFormMessage(`Testing connection to ${payload.serviceName}...`);

  try {
    const result = await fetchJson("/api/monitored-services/probe", {
      method: "POST",
      body: JSON.stringify({
        serviceName: payload.serviceName,
        baseUrl: payload.baseUrl,
        environment: payload.environment
      })
    });

    const parts = [
      `health=${result.healthStatus || "UNKNOWN"}`,
      `run=${result.runStatus || "N/A"}`,
      `lastRun=${result.lastRunDate || "-"}`
    ];

    if (result.error) {
      setFormMessage(`Connection tested with warning: ${parts.join(", ")}. ${result.error}`, "error");
    } else {
      setFormMessage(`Connection OK: ${parts.join(", ")}.`, "success");
    }
  } catch (error) {
    setFormMessage(`Connection test failed: ${error.message}`, "error");
  } finally {
    probeButton.disabled = false;
    probeButton.textContent = originalText;
  }
}

async function toggleServiceEnabled(row, button) {
  const nextEnabled = !row.enabled;
  const payload = {
    serviceName: row.serviceName,
    baseUrl: row.baseUrl,
    environment: row.environment,
    enabled: nextEnabled
  };

  button.disabled = true;

  try {
    await fetchJson(`/api/monitored-services/${row.id}`, {
      method: "PUT",
      body: JSON.stringify(payload)
    });
    setFormMessage(
      `${row.serviceName} polling ${nextEnabled ? "enabled" : "disabled"}.`,
      "success"
    );
    await loadDashboard();
  } catch (error) {
    setFormMessage(error.message, "error");
  } finally {
    button.disabled = false;
  }
}

async function checkServiceNow(row, button) {
  const originalText = button.textContent;
  button.disabled = true;
  button.textContent = "Checking...";
  setFormMessage(`Checking ${row.serviceName} now...`);

  try {
    await fetchJson(`/api/monitored-services/${row.id}/check`, { method: "POST" });
    selectedServiceId = row.id;
    setFormMessage(`${row.serviceName} checked now.`, "success");
    await loadDashboard();
  } catch (error) {
    setFormMessage(`Check failed for ${row.serviceName}: ${error.message}`, "error");
  } finally {
    button.disabled = false;
    button.textContent = originalText;
  }
}

async function runRetentionCleanup(button) {
  const originalText = button.textContent;
  button.disabled = true;
  button.textContent = "Running...";
  setFormMessage("Running retention cleanup now...");

  try {
    const result = await fetchJson("/api/monitoring/retention/run", { method: "POST" });
    const deletedTotal =
      Number(result.deletedServiceChecks || 0) +
      Number(result.deletedAlertEvents || 0) +
      Number(result.deletedResolvedIncidents || 0);

    if (result.status === "FAILED") {
      setFormMessage(`Retention cleanup failed: ${result.error || "unknown error"}`, "error");
    } else {
      setFormMessage(`Retention cleanup completed. ${deletedTotal} rows pruned.`, "success");
    }

    await loadDashboard();
  } catch (error) {
    setFormMessage(`Retention cleanup request failed: ${error.message}`, "error");
  } finally {
    button.disabled = false;
    button.textContent = originalText;
  }
}

async function handleSummaryAction(event) {
  const button = event.target.closest("button[data-action='run-retention-cleanup']");
  if (!button) {
    return;
  }

  await runRetentionCleanup(button);
}

async function handleTableAction(event) {
  const button = event.target.closest("button[data-action]");
  if (!button) {
    const card = event.target.closest("[data-service-id]");
    if (!card) {
      return;
    }

    const serviceId = Number(card.dataset.serviceId);
    if (!serviceId || selectedServiceId === serviceId) {
      return;
    }

    selectedServiceId = serviceId;
    renderServices(serviceRows);
    await loadServiceDetail(serviceId);
    return;
  }

  const row = serviceRows.find((item) => item.id === Number(button.dataset.id));
  if (!row) {
    return;
  }

  if (button.dataset.action === "edit") {
    fillForm(row);
    return;
  }

  if (button.dataset.action === "toggle-enabled") {
    await toggleServiceEnabled(row, button);
    return;
  }

  if (button.dataset.action === "check-now") {
    await checkServiceNow(row, button);
    return;
  }

  if (button.dataset.action === "delete") {
    const confirmed = window.confirm(`Delete monitored service "${row.serviceName}"?`);
    if (!confirmed) {
      return;
    }

    try {
      await fetchJson(`/api/monitored-services/${row.id}`, { method: "DELETE" });
      setFormMessage("Monitored service deleted.", "success");
      resetForm();
      if (selectedServiceId === row.id) {
        selectedServiceId = null;
      }
      await loadDashboard();
    } catch (error) {
      setFormMessage(error.message, "error");
    }
  }
}

function handleHistoryAction(event) {
  const listButton = event.target.closest("button[data-action='toggle-history-list']");
  if (listButton) {
    const isIncidents = listButton.dataset.section === "incidents";
    if (isIncidents) {
      historyState.incidentsExpanded = !historyState.incidentsExpanded;
      renderIncidentHistory(latestHistory.incidents);
    } else {
      historyState.alertsExpanded = !historyState.alertsExpanded;
      renderAlertHistory(latestHistory.alerts);
    }
    return;
  }

  const button = event.target.closest("button[data-action='toggle-history-copy']");
  if (!button) {
    return;
  }

  const copyBlock = button.closest(".history-copy-block");
  if (!copyBlock) {
    return;
  }

  const copy = copyBlock.querySelector(".history-copy");
  const expanded = copyBlock.dataset.copyState === "expanded";

  copyBlock.dataset.copyState = expanded ? "collapsed" : "expanded";
  if (button.classList.contains("raw-alert-toggle")) {
    button.textContent = expanded ? "View raw message" : "Hide raw message";
  } else {
    copy.classList.toggle("history-copy-clamped", expanded);
    button.textContent = expanded ? "Show full message" : "Collapse";
  }
}

form.addEventListener("submit", submitForm);
probeButton.addEventListener("click", probeServiceConnection);
resetButton.addEventListener("click", resetForm);
refreshButton.addEventListener("click", loadDashboard);
summaryCards.addEventListener("click", handleSummaryAction);
servicesList.addEventListener("click", handleTableAction);
serviceDetailPanel.addEventListener("click", handleHistoryAction);
incidentHistoryList.addEventListener("click", handleHistoryAction);
alertHistoryList.addEventListener("click", handleHistoryAction);

loadDashboard().catch((error) => {
  setFormMessage(`Dashboard load failed: ${error.message}`, "error");
});
