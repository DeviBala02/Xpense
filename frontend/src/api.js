// Small fetch wrapper. It adds your login token to every call and throws an Error with a friendly message.

const TOKEN_KEY = "budgetbee_token";

export const auth = {
  token() { try { return localStorage.getItem(TOKEN_KEY); } catch { return null; } },
  set(t) { try { localStorage.setItem(TOKEN_KEY, t); } catch { /* private mode: login lasts until the tab closes */ } },
  clear() { try { localStorage.removeItem(TOKEN_KEY); } catch { /* ignore */ } },
};

async function call(method, url, body) {
  const headers = { "Content-Type": "application/json" };
  const token = auth.token();
  if (token) headers.Authorization = `Bearer ${token}`;

  let res;
  try {
    res = await fetch(url, { method, headers, body: body ? JSON.stringify(body) : undefined });
  } catch {
    throw new Error("Cannot reach the server. Check that BudgetBee is running and you are online");
  }

  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = null; }

  const isLoginCall = url.startsWith("/api/auth/login") || url.startsWith("/api/auth/register");
  if (res.status === 401 && !isLoginCall) {
    auth.clear();
    window.dispatchEvent(new Event("auth-expired"));
  }
  if (!res.ok) {
    if (data && data.error) throw new Error(data.error);
    if (res.status >= 500) throw new Error("The server had a problem. Please try again in a moment");
    throw new Error(`Request failed (${res.status})`);
  }
  return data;
}

export const api = {
  // account
  register: (name, email, password) => call("POST", "/api/auth/register", { name, email, password }),
  login: (email, password) => call("POST", "/api/auth/login", { email, password }),
  logout: () => call("POST", "/api/auth/logout"),
  me: () => call("GET", "/api/auth/me"),
  deleteAccount: (password) => call("POST", "/api/auth/delete-account", { password }),

  // data
  summary: (period) => call("GET", `/api/summary?period=${period}`),
  expenses: (period) => call("GET", `/api/expenses?period=${period}`),
  addExpense: (b) => call("POST", "/api/expenses", b),
  deleteExpense: (id) => call("DELETE", `/api/expenses/${id}`),
  categories: () => call("GET", "/api/categories"),
  addCategory: (b) => call("POST", "/api/categories", b),
  updateCategory: (id, b) => call("PUT", `/api/categories/${id}`, b),
  deleteCategory: (id) => call("DELETE", `/api/categories/${id}`),
  budgets: () => call("GET", "/api/budgets"),
  addBudget: (b) => call("POST", "/api/budgets", b),
  deleteBudget: (id) => call("DELETE", `/api/budgets/${id}`),
  goals: () => call("GET", "/api/goals"),
  addGoal: (b) => call("POST", "/api/goals", b),
  deposit: (id, amount) => call("POST", `/api/goals/${id}/deposit`, { amount: String(amount) }),
  deleteGoal: (id) => call("DELETE", `/api/goals/${id}`),
  income: (period) => call("GET", `/api/income?period=${period}`),
  incomePending: () => call("GET", "/api/income/pending"),
  incomeSummary: (period) => call("GET", `/api/income/summary?period=${period}`),
  addIncome: (b) => call("POST", "/api/income", b),
  categorizeIncome: (id, category, remember) =>
    call("PUT", `/api/income/${id}/category`, { category, remember: String(!!remember) }),
  deleteIncome: (id) => call("DELETE", `/api/income/${id}`),
  incomeCategories: () => call("GET", "/api/income/categories"),
  addIncomeCategory: (b) => call("POST", "/api/income/categories", b),
  deleteIncomeCategory: (id) => call("DELETE", `/api/income/categories/${id}`),
  plan: () => call("GET", "/api/plan/today"),
  whatIf: (amount) => call("GET", `/api/plan/what-if?amount=${encodeURIComponent(amount)}`),
  coach: () => call("GET", "/api/plan/coach"),
  settings: () => call("GET", "/api/settings"),
  saveSettings: (expectedIncome) => call("PUT", "/api/settings", { expectedIncome: String(expectedIncome) }),
  demoSeed: () => call("POST", "/api/demo/seed"),
  chat: (message) => call("POST", "/api/chat", { message }),
};

export const rupee = (n) =>
  "₹" + Number(n || 0).toLocaleString("en-IN", { maximumFractionDigits: 2 });
