(() => {
  const state = {
    token: localStorage.getItem("ledgerpay.token") || "",
    email: localStorage.getItem("ledgerpay.email") || "",
    accounts: [],
    selected: "",
  };

  const healthEl = document.getElementById("health");
  const sessionEl = document.getElementById("session");
  const accountsEl = document.getElementById("accounts");
  const selectedEl = document.getElementById("selected-account");
  const balanceEl = document.getElementById("balance");
  const logEl = document.getElementById("log");

  function log(message, payload) {
    const stamp = new Date().toLocaleTimeString();
    const extra = payload === undefined ? "" : `\n${JSON.stringify(payload, null, 2)}`;
    logEl.textContent = `[${stamp}] ${message}${extra}\n\n` + logEl.textContent;
  }

  async function api(path, options = {}) {
    const headers = Object.assign({ "Content-Type": "application/json" }, options.headers || {});
    if (state.token) {
      headers.Authorization = `Bearer ${state.token}`;
    }
    const response = await fetch(path, Object.assign({}, options, { headers }));
    const text = await response.text();
    let body = null;
    if (text) {
      try {
        body = JSON.parse(text);
      } catch (_) {
        body = text;
      }
    }
    if (!response.ok) {
      const error = new Error((body && body.message) || `HTTP ${response.status}`);
      error.body = body;
      error.status = response.status;
      throw error;
    }
    return body;
  }

  function setSession() {
    sessionEl.textContent = state.token
      ? `Signed in as ${state.email || "user"}`
      : "Not signed in";
  }

  function renderAccounts() {
    accountsEl.innerHTML = "";
    selectedEl.innerHTML = "";
    if (!state.accounts.length) {
      accountsEl.innerHTML = "<li><span>No accounts yet</span></li>";
      balanceEl.textContent = "Balance —";
      return;
    }
    state.accounts.forEach((account) => {
      const item = document.createElement("li");
      if (account.id === state.selected) {
        item.classList.add("active");
      }
      item.innerHTML = `<span>${account.name}<br><small>${account.id}</small></span><span>${account.balance} ${account.currency}</span>`;
      item.addEventListener("click", () => {
        state.selected = account.id;
        selectedEl.value = account.id;
        renderAccounts();
        refreshBalance();
      });
      accountsEl.appendChild(item);

      const option = document.createElement("option");
      option.value = account.id;
      option.textContent = `${account.name} (${account.currency})`;
      selectedEl.appendChild(option);
    });
    if (!state.selected || !state.accounts.some((a) => a.id === state.selected)) {
      state.selected = state.accounts[0].id;
    }
    selectedEl.value = state.selected;
  }

  async function refreshAccounts() {
    const page = await api("/api/v1/accounts?page=0&size=50");
    state.accounts = page.accounts || [];
    renderAccounts();
    await refreshBalance();
  }

  async function refreshBalance() {
    if (!state.selected) {
      balanceEl.textContent = "Balance —";
      return;
    }
    const balance = await api(`/api/v1/accounts/${state.selected}/balance`);
    balanceEl.textContent = `Balance ${balance.balance} ${balance.currency}`;
  }

  async function registerOrLogin(mode) {
    const email = document.getElementById("email").value.trim();
    const password = document.getElementById("password").value;
    const path = mode === "register" ? "/api/v1/auth/register" : "/api/v1/auth/login";
    const body = await api(path, {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    state.token = body.accessToken;
    state.email = email;
    localStorage.setItem("ledgerpay.token", state.token);
    localStorage.setItem("ledgerpay.email", state.email);
    setSession();
    log(mode === "register" ? "Registered" : "Logged in", { email });
    await refreshAccounts();
  }

  document.getElementById("auth-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      await registerOrLogin("login");
    } catch (error) {
      log("Login failed", error.body || error.message);
    }
  });

  document.getElementById("register-btn").addEventListener("click", async () => {
    try {
      await registerOrLogin("register");
    } catch (error) {
      log("Register failed", error.body || error.message);
    }
  });

  document.getElementById("account-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const account = await api("/api/v1/accounts", {
        method: "POST",
        body: JSON.stringify({
          name: document.getElementById("account-name").value.trim(),
          currency: document.getElementById("currency").value,
        }),
      });
      state.selected = account.id;
      log("Account created", account);
      await refreshAccounts();
    } catch (error) {
      log("Create account failed", error.body || error.message);
    }
  });

  document.getElementById("refresh-btn").addEventListener("click", async () => {
    try {
      await refreshAccounts();
      log("Accounts refreshed");
    } catch (error) {
      log("Refresh failed", error.body || error.message);
    }
  });

  selectedEl.addEventListener("change", async () => {
    state.selected = selectedEl.value;
    renderAccounts();
    try {
      await refreshBalance();
    } catch (error) {
      log("Balance failed", error.body || error.message);
    }
  });

  document.getElementById("deposit-btn").addEventListener("click", async () => {
    try {
      const amount = document.getElementById("deposit-amount").value.trim();
      const result = await api(`/api/v1/accounts/${state.selected}/deposits`, {
        method: "POST",
        headers: { "Idempotency-Key": `ui-deposit-${crypto.randomUUID()}` },
        body: JSON.stringify({ amount }),
      });
      log("Deposit posted", result);
      await refreshAccounts();
    } catch (error) {
      log("Deposit failed", error.body || error.message);
    }
  });

  document.getElementById("transfer-btn").addEventListener("click", async () => {
    try {
      const destinationAccountId = document.getElementById("dest-account").value.trim();
      const amount = document.getElementById("transfer-amount").value.trim();
      const currency = (state.accounts.find((a) => a.id === state.selected) || {}).currency || "USD";
      const result = await api("/api/v1/transfers", {
        method: "POST",
        headers: { "Idempotency-Key": `ui-transfer-${crypto.randomUUID()}` },
        body: JSON.stringify({
          sourceAccountId: state.selected,
          destinationAccountId,
          currency,
          amount,
        }),
      });
      log("Transfer posted", result);
      await refreshAccounts();
    } catch (error) {
      log("Transfer failed", error.body || error.message);
    }
  });

  fetch("/actuator/health")
    .then((r) => r.json())
    .then((body) => {
      healthEl.textContent = `health ${body.status || "UNKNOWN"}`;
      if (body.status === "UP") {
        healthEl.classList.add("up");
      }
    })
    .catch(() => {
      healthEl.textContent = "health DOWN";
    });

  setSession();
  if (state.token) {
    refreshAccounts().catch((error) => log("Session restore failed", error.body || error.message));
  }
})();
