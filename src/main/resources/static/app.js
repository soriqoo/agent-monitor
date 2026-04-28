const summaryCards = document.getElementById("summaryCards");
const servicesList = document.getElementById("servicesList");
const incidentHistoryList = document.getElementById("incidentHistoryList");
const alertHistoryList = document.getElementById("alertHistoryList");
const form = document.getElementById("serviceForm");
const formTitle = document.getElementById("formTitle");
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

let serviceRows = [];

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
    ["Monitor status", summary.status]
  ];

  summaryCards.innerHTML = cards.map(([label, value]) => `
    <article class="summary-card ${label === "Monitor status" ? "status-card" : ""}">
      <span class="label">${label}</span>
      <div class="value">${label === "Monitor status" ? `<span class="status-pill">${value}</span>` : value}</div>
    </article>
  `).join("");
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
    <article class="service-card">
      <div class="service-card-top">
        <div class="service-meta">
          <div class="service-title-row">
            <span class="service-name">${escapeHtml(row.serviceName)}</span>
            <span class="service-environment">${escapeHtml(row.environment)}</span>
          </div>
          <span class="service-subline">${row.enabled ? "Enabled for polling" : "Disabled from polling"}</span>
        </div>
        <div class="row-actions">
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

function renderHistory(history) {
  renderIncidentHistory(history.incidents || []);
  renderAlertHistory(history.alerts || []);
}

function renderIncidentHistory(items) {
  if (!items.length) {
    incidentHistoryList.innerHTML = renderEmptyHistory(
      "No incidents recorded yet.",
      "New incidents and recoveries will appear here."
    );
    return;
  }

  incidentHistoryList.innerHTML = items.map((item) => `
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
  `).join("");
}

function renderAlertHistory(items) {
  if (!items.length) {
    alertHistoryList.innerHTML = renderEmptyHistory(
      "No alert deliveries recorded yet.",
      "Successful Slack alert sends will appear here."
    );
    return;
  }

  alertHistoryList.innerHTML = items.map((item) => `
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
      ${renderHistoryCopy(item.message)}
    </article>
  `).join("");
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

  const [summary, overview, history] = await Promise.all([
    fetchJson("/internal/monitoring/summary"),
    fetchJson("/api/monitored-services/overview"),
    fetchJson("/api/monitoring/history?limit=6")
  ]);

  renderSummary(summary);
  renderServices(overview);
  renderHistory(history);
  lastRefreshText.textContent = new Intl.DateTimeFormat(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  }).format(new Date());
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

async function handleTableAction(event) {
  const button = event.target.closest("button[data-action]");
  if (!button) {
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

  if (button.dataset.action === "delete") {
    const confirmed = window.confirm(`Delete monitored service "${row.serviceName}"?`);
    if (!confirmed) {
      return;
    }

    try {
      await fetchJson(`/api/monitored-services/${row.id}`, { method: "DELETE" });
      setFormMessage("Monitored service deleted.", "success");
      resetForm();
      await loadDashboard();
    } catch (error) {
      setFormMessage(error.message, "error");
    }
  }
}

function handleHistoryAction(event) {
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
  copy.classList.toggle("history-copy-clamped", expanded);
  button.textContent = expanded ? "Show full message" : "Collapse";
}

form.addEventListener("submit", submitForm);
resetButton.addEventListener("click", resetForm);
refreshButton.addEventListener("click", loadDashboard);
servicesList.addEventListener("click", handleTableAction);
incidentHistoryList.addEventListener("click", handleHistoryAction);
alertHistoryList.addEventListener("click", handleHistoryAction);

loadDashboard().catch((error) => {
  setFormMessage(`Dashboard load failed: ${error.message}`, "error");
});
