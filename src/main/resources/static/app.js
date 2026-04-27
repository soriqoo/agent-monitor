const summaryCards = document.getElementById("summaryCards");
const servicesTableBody = document.getElementById("servicesTableBody");
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
      <div class="value">${value}</div>
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
    servicesTableBody.innerHTML = `
      <tr class="empty-row">
        <td colspan="8">No monitored services registered yet.</td>
      </tr>
    `;
    return;
  }

  servicesTableBody.innerHTML = rows.map((row) => `
    <tr>
      <td>
        <div class="service-meta">
          <span class="service-name">${escapeHtml(row.serviceName)}</span>
          <span class="service-subline">${row.enabled ? "Enabled for polling" : "Disabled from polling"}</span>
          <span class="runtime-meta">Last checked ${escapeHtml(formatTimestamp(row.lastCheckedAt))}</span>
          ${row.error ? `<span class="service-error">${escapeHtml(row.error)}</span>` : ""}
        </div>
      </td>
      <td>${escapeHtml(row.environment)}</td>
      <td><span class="badge ${badgeClass(row.healthStatus)}">${escapeHtml(row.healthStatus || "UNSEEN")}</span></td>
      <td><span class="badge muted">${escapeHtml(row.runStatus || "N/A")}</span></td>
      <td>${escapeHtml(row.lastRunDate || "-")}</td>
      <td><span class="badge ${row.openIncident ? "down" : "up"}">${row.openIncident ? "OPEN" : "CLEAR"}</span></td>
      <td class="service-url-cell"><span class="service-url service-url-value">${escapeHtml(row.baseUrl)}</span></td>
      <td>
        <div class="row-actions">
          <button class="small-button" type="button" data-action="edit" data-id="${row.id}">Edit</button>
          <button class="danger-button" type="button" data-action="delete" data-id="${row.id}">Delete</button>
        </div>
      </td>
    </tr>
  `).join("");
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

async function loadDashboard() {
  setFormMessage("");

  const [summary, overview] = await Promise.all([
    fetchJson("/internal/monitoring/summary"),
    fetchJson("/api/monitored-services/overview")
  ]);

  renderSummary(summary);
  renderServices(overview);
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

form.addEventListener("submit", submitForm);
resetButton.addEventListener("click", resetForm);
refreshButton.addEventListener("click", loadDashboard);
servicesTableBody.addEventListener("click", handleTableAction);

loadDashboard().catch((error) => {
  setFormMessage(`Dashboard load failed: ${error.message}`, "error");
});
