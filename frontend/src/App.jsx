import { useEffect, useRef, useState } from "react";
import { api, auth, rupee } from "./api.js";

/* ------------------------------------------------------------ helpers */

function useLoad(fn, deps) {
  const [data, setData] = useState(null);
  const [err, setErr] = useState("");
  useEffect(() => {
    let live = true;
    setErr("");
    fn()
      .then((d) => live && setData(d))
      .catch((e) => live && setErr(e.message));
    return () => { live = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return { data, err };
}

const PERIODS = [
  ["today", "Today"],
  ["week", "This week"],
  ["month", "This month"],
  ["year", "This year"],
];

function PeriodTabs({ value, onChange }) {
  return (
    <div className="seg" role="tablist" aria-label="Period">
      {PERIODS.map(([k, label]) => (
        <button key={k} className={value === k ? "on" : ""} onClick={() => onChange(k)}>{label}</button>
      ))}
    </div>
  );
}

function BackendDown({ err }) {
  if (!err) return null;
  return <p className="err">{err}. Check that the backend is running on port 8080.</p>;
}

const fmtDate = (iso) =>
  new Date(iso).toLocaleString("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });

const pad = (n) => String(n).padStart(2, "0");
const ymd = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const monthStart = () => { const d = new Date(); return ymd(new Date(d.getFullYear(), d.getMonth(), 1)); };
const monthEnd = () => { const d = new Date(); return ymd(new Date(d.getFullYear(), d.getMonth() + 1, 0)); };

/* ------------------------------------------------------------ safe to spend (the core idea) */

function PlanHero({ tick, bump }) {
  const load = useLoad(() => api.plan(), [tick]);
  const [income, setIncome] = useState("");
  const [amt, setAmt] = useState("");
  const [res, setRes] = useState(null);
  const [coach, setCoach] = useState(null);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState("");

  useEffect(() => { setRes(null); setCoach(null); }, [tick]);

  const v = load.data;
  const p = v && v.plan;
  if (!p) return load.err ? <BackendDown err={load.err} /> : null;

  async function saveIncome(e) {
    e.preventDefault();
    setMsg("");
    try { await api.saveSettings(income); setIncome(""); bump(); } catch (x) { setMsg(x.message); }
  }
  async function seed() {
    setMsg("");
    try { const r = await api.demoSeed(); setMsg(r.message); bump(); } catch (x) { setMsg(x.message); }
  }
  async function check(e) {
    e.preventDefault();
    if (!amt) return;
    setMsg("");
    try { setRes(await api.whatIf(amt)); } catch (x) { setMsg(x.message); }
  }
  async function askCoach() {
    setBusy(true); setMsg("");
    try { setCoach(await api.coach()); } catch (x) { setMsg(x.message); }
    setBusy(false);
  }

  const incomeForm = (
    <form className="form" onSubmit={saveIncome}>
      <label className="field">Income you expect each month (₹)
        <input type="number" min="1" required value={income} placeholder="30000" onChange={(e) => setIncome(e.target.value)} />
      </label>
      <button className="btn honey">Save</button>
    </form>
  );

  if (p.status === "SETUP") {
    return (
      <div className="card hero SETUP stack-0">
        <div>
          <h2>Find out what you can safely spend today</h2>
          <p className="note">
            Tell BudgetBee your monthly income. It sets aside what your savings goals need, divides the rest over the days left,
            and gives you one number that updates every time you spend.
          </p>
          {incomeForm}
          <p className="note" style={{ marginTop: 12 }}>Just exploring? <button className="chip" onClick={seed}>Load sample data</button></p>
          {msg && <div className="note">{msg}</div>}
        </div>
      </div>
    );
  }

  if (p.status === "NO_PLAN") {
    return (
      <div className="card hero NO_PLAN stack-0">
        <div>
          <h2>Your savings goals need more than your income covers</h2>
          <p className="note">This month's goal savings ({rupee(p.savingsReserve)}) are more than the income BudgetBee knows about ({rupee(p.incomeBasis)}). Give a goal a later deadline or a smaller target, or update your income.</p>
          {incomeForm}
        </div>
      </div>
    );
  }

  const over = Number(p.leftToday) < 0;
  const pct = Number(p.dailyAllowance) > 0 ? Math.min(100, Math.round((Number(p.spentToday) / Number(p.dailyAllowance)) * 100)) : 100;
  const headline = { GOOD: "You are on track today", CAREFUL: "Getting close to today's limit", OVER: "Over today's limit" }[p.status];
  const w = res && res.whatIf;

  return (
    <div className={`card hero ${p.status}`}>
      <div className="hero-main">
        <div className="label">Safe to spend today</div>
        <div className="hero-num">{over ? "-" : ""}{rupee(Math.abs(p.leftToday))}</div>
        <div className="sub"><b>{headline}.</b> Limit {rupee(p.dailyAllowance)} a day, {rupee(p.spentToday)} spent today.</div>
        <div className={`track hero-track ${p.status}`}><b style={{ width: `${pct}%` }} /></div>
        <ul className="hero-facts">
          <li>{p.daysLeft} days left this month. Limit changes as you spend.</li>
          <li className={p.onTrack ? "down" : "up"}>
            At this pace you will spend {rupee(p.projectedSpend)} of your {rupee(p.pool)} plan.
          </li>
          <li>
            {Number(p.savingsReserve) > 0
              ? <>Saving {rupee(p.savingsReserve)} this month{v.goalName ? <> for <b>{v.goalName}</b></> : null}.</>
              : "No savings goal with a deadline yet, so nothing is set aside."}
          </li>
        </ul>
        <details className="small-details">
          <summary>Expected income: {rupee(v.expectedIncome)} (received so far: {rupee(v.incomeReceived)})</summary>
          {incomeForm}
        </details>
      </div>

      <div className="hero-side">
        <h2>Can I afford it?</h2>
        <form className="form" onSubmit={check}>
          <label className="field">Amount (₹)
            <input type="number" min="1" value={amt} placeholder="500" onChange={(e) => setAmt(e.target.value)} />
          </label>
          <button className="btn">Check</button>
        </form>
        {w && (
          <p className={`verdict ${w.affordable ? "yes" : "no"}`}>
            {w.affordable
              ? <>Yes. You would still have {rupee(w.leftTodayAfter)} left today.</>
              : <>
                  Not without dipping into savings. It is {rupee(w.overBy)} beyond today's limit
                  {w.delayDays != null && res.goalName ? <>, which delays <b>{res.goalName}</b> by about {w.delayDays} days</> : null}.
                  Your daily limit would drop from {rupee(p.dailyAllowance)} to {rupee(w.newDailyAllowance)}.
                </>}
          </p>
        )}
        <div className="coach">
          <button className="btn ghost small" onClick={askCoach} disabled={busy}>{busy ? "Thinking…" : "Ask the AI coach"}</button>
          {coach && (
            <p className="coach-text">
              {coach.text}
              <small>{coach.engine === "rules" ? "built-in tips (turn on AI for friendlier wording)" : `written by ${coach.engine}, numbers checked against your data`}</small>
            </p>
          )}
        </div>
        {msg && <div className="err">{msg}</div>}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------ quick add */

function QuickAdd({ bump }) {
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState("");
  const [msg, setMsg] = useState("");
  const [busy, setBusy] = useState(false);

  async function add(e) {
    e.preventDefault();
    if (!amount) return;
    setBusy(true); setMsg("");
    try {
      const x = await api.addExpense({ amount, note, category: "" });
      setMsg(`Added ${rupee(x.amount)} under ${x.category.icon} ${x.category.name}.`);
      setAmount(""); setNote("");
      bump();
    } catch (err) { setMsg(err.message); }
    setBusy(false);
  }

  return (
    <form className="card quick" onSubmit={add}>
      <div className="quick-title">Add an expense</div>
      <div className="quick-row">
        <input type="number" inputMode="decimal" min="1" step="0.01" required value={amount} placeholder="₹ Amount" aria-label="Amount in rupees" onChange={(e) => setAmount(e.target.value)} />
        <input value={note} placeholder="What for? e.g. snacks, auto, lunch" aria-label="What was it for" onChange={(e) => setNote(e.target.value)} />
        <button className="btn honey" disabled={busy}>{busy ? "Adding…" : "Add"}</button>
      </div>
      {msg && <div className="note quick-msg">{msg}</div>}
    </form>
  );
}

/* ------------------------------------------------------------ dashboard */

function Dashboard({ period, setPeriod, tick, setTab, bump }) {
  const s = useLoad(() => api.summary(period), [period, tick]);
  const b = useLoad(() => api.budgets(), [tick]);
  const e = useLoad(() => api.expenses(period), [period, tick]);
  const sum = s.data;
  const max = sum ? Math.max(1, ...sum.last7Days.map((d) => Number(d.amount))) : 1;
  const alerts = (b.data || []).filter((x) => x.state !== "OK");
  const diff = sum && sum.previousTotal != null ? Number(sum.total) - Number(sum.previousTotal) : null;

  return (
    <>
      <div className="head">
        <h1>Dashboard</h1>
        <PeriodTabs value={period} onChange={setPeriod} />
      </div>
      <BackendDown err={s.err} />

      <PlanHero tick={tick} bump={bump} />
      <QuickAdd bump={bump} />

      {sum && sum.pendingIncome > 0 && (
        <div className="alert INBOX">
          <span>💰</span>
          <span><b>{sum.pendingIncome}</b> credit{sum.pendingIncome > 1 ? "s" : ""} received and waiting for a category.</span>
          <button className="btn small go" onClick={() => setTab("income")}>Choose categories</button>
        </div>
      )}

      {alerts.map((a) => (
        <div key={a.id} className={`alert ${a.state}`}>
          <span>{a.state === "EXCEEDED" ? "🚨" : "⚠️"}</span>
          <span>
            <b>{a.name}</b> is {a.percent}% used: {rupee(a.spent)} of {rupee(a.limit)}.
            {a.state === "EXCEEDED" ? " You are over the limit." : ` ${rupee(a.dailyAllowance)} a day left to stay on track.`}
          </span>
        </div>
      ))}

      {sum && (
        <>
          <div className="grid g4">
            <div className="card stat">
              <div className="label">Spent {sum.label}</div>
              <div className="num">{rupee(sum.total)}</div>
              {diff !== null && (
                <div className={`sub ${diff > 0 ? "up" : "down"}`}>
                  {diff > 0 ? "▲" : "▼"} {rupee(Math.abs(diff))} vs previous
                </div>
              )}
            </div>
            <div className="card stat">
              <div className="label">Money in {sum.label}</div>
              <div className="num in">{rupee(sum.income)}</div>
              <div className="sub">
                Balance {Number(sum.income) - Number(sum.total) < 0 ? "-" : ""}{rupee(Math.abs(Number(sum.income) - Number(sum.total)))}
              </div>
            </div>
            <div className="card stat">
              <div className="label">Spent today</div>
              <div className="num">{rupee(sum.today)}</div>
              <div className="sub">{(e.data || []).length} payments {sum.label}</div>
            </div>
            <div className="card stat">
              <div className="label">Top category</div>
              <div className="num">{sum.byCategory[0] ? `${sum.byCategory[0].icon} ${sum.byCategory[0].name}` : "None yet"}</div>
              <div className="sub">{sum.byCategory[0] ? `${rupee(sum.byCategory[0].amount)} · ${sum.byCategory[0].percent}%` : "Make a payment to see it here"}</div>
            </div>
          </div>

          <div className="grid g2 stack">
            <div className="card">
              <h2>Last 7 days</h2>
              <div className="bars">
                {sum.last7Days.map((d, i) => (
                  <div key={d.date} className={`bar ${i === 6 ? "today" : ""}`} title={`${d.date}: ${rupee(d.amount)}`}>
                    <i style={{ height: `${(Number(d.amount) / max) * 100}%` }} />
                    {d.label}
                  </div>
                ))}
              </div>
            </div>
            <div className="card">
              <h2>By category</h2>
              {sum.byCategory.length === 0 && <div className="empty">No spending {sum.label}.</div>}
              {sum.byCategory.map((c) => (
                <div key={c.name} className="row">
                  <div className="ico">{c.icon}</div>
                  <div className="grow">
                    <div className="title">{c.name}</div>
                    <div className="track"><b style={{ width: `${c.percent}%` }} /></div>
                  </div>
                  <div className="amt">{rupee(c.amount)}</div>
                </div>
              ))}
            </div>
          </div>

          <div className="card stack">
            <h2>Latest payments</h2>
            {(e.data || []).length === 0 && <div className="empty">Nothing yet. Pay with a keyword in GPay, or tell the bot what you spent.</div>}
            {(e.data || []).slice(0, 6).map((x) => <ExpenseRow key={x.id} x={x} />)}
          </div>
        </>
      )}
    </>
  );
}

function ExpenseRow({ x, onDelete }) {
  return (
    <div className="row">
      <div className="ico">{x.category.icon}</div>
      <div className="grow">
        <div className="title">{x.merchant || x.note || x.category.name}</div>
        <div className="meta">{x.category.name} · {fmtDate(x.spentAt)}</div>
      </div>
      <span className={`badge ${x.source}`}>{x.source === "GPAY" ? "GPay" : x.source === "CHAT" ? "Bot" : "Manual"}</span>
      <div className="amt">{rupee(x.amount)}</div>
      {onDelete && <button className="x" aria-label="Delete expense" onClick={() => onDelete(x.id)}>×</button>}
    </div>
  );
}

/* ------------------------------------------------------------ expenses */

function Expenses({ period, setPeriod, tick, bump }) {
  const list = useLoad(() => api.expenses(period), [period, tick]);
  const cats = useLoad(() => api.categories(), []);
  const [f, setF] = useState({ amount: "", note: "", category: "", spentAt: "" });
  const [err, setErr] = useState("");

  async function save(ev) {
    ev.preventDefault();
    setErr("");
    try {
      await api.addExpense({ ...f, amount: f.amount });
      setF({ amount: "", note: "", category: "", spentAt: "" });
      bump();
    } catch (e2) { setErr(e2.message); }
  }

  return (
    <>
      <div className="head">
        <h1>Expenses</h1>
        <PeriodTabs value={period} onChange={setPeriod} />
      </div>
      <div className="card">
        <h2>Add one by hand</h2>
        <form className="form" onSubmit={save}>
          <label className="field">Amount (₹)
            <input type="number" min="1" step="0.01" required value={f.amount} onChange={(e) => setF({ ...f, amount: e.target.value })} />
          </label>
          <label className="field">Note or keyword
            <input value={f.note} placeholder="snacks" onChange={(e) => setF({ ...f, note: e.target.value })} />
          </label>
          <label className="field">Category
            <select value={f.category} onChange={(e) => setF({ ...f, category: e.target.value })}>
              <option value="">Auto from note</option>
              {(cats.data || []).map((c) => <option key={c.id} value={c.name}>{c.icon} {c.name}</option>)}
            </select>
          </label>
          <label className="field">When
            <input type="datetime-local" value={f.spentAt} onChange={(e) => setF({ ...f, spentAt: e.target.value })} />
          </label>
          <button className="btn honey">Save expense</button>
        </form>
        {err && <div className="err">{err}</div>}
      </div>
      <div className="card stack">
        <h2>All expenses</h2>
        <BackendDown err={list.err} />
        {list.data && list.data.length === 0 && <div className="empty">No expenses in this period.</div>}
        {(list.data || []).map((x) => (
          <ExpenseRow key={x.id} x={x} onDelete={async (id) => { if (window.confirm("Delete this expense?")) { await api.deleteExpense(id); bump(); } }} />
        ))}
      </div>
    </>
  );
}

/* ------------------------------------------------------------ money in (credits) */

function MoneyIn({ period, setPeriod, tick, bump }) {
  const pending = useLoad(() => api.incomePending(), [tick]);
  const list = useLoad(() => api.income(period), [period, tick]);
  const sum = useLoad(() => api.incomeSummary(period), [period, tick]);
  const cats = useLoad(() => api.incomeCategories(), [tick]);
  const [remember, setRemember] = useState({});
  const [f, setF] = useState({ amount: "", sender: "", category: "", note: "" });
  const [nc, setNc] = useState({ name: "", icon: "" });
  const [err, setErr] = useState("");
  const run = async (fn) => { setErr(""); try { await fn(); bump(); } catch (e) { setErr(e.message); } };
  const categories = cats.data || [];

  return (
    <>
      <div className="head">
        <h1>Money in</h1>
        <PeriodTabs value={period} onChange={setPeriod} />
      </div>
      <BackendDown err={pending.err} />
      {err && <div className="err">{err}</div>}

      {(pending.data || []).length > 0 && (
        <div className="card" style={{ borderColor: "#D5C9F8" }}>
          <h2>Needs a category ({pending.data.length})</h2>
          <p className="note">Money received is saved automatically from your credit alerts. Tap a category for each one.</p>
          {pending.data.map((x) => (
            <div key={x.id} className="inbox-item">
              <div className="inbox-top">
                <span className="big">+ {rupee(x.amount)}</span>
                <b>{x.sender || "Unknown sender"}</b>
                <span className="meta">{fmtDate(x.receivedAt)}</span>
                <button className="x" style={{ marginLeft: "auto" }} aria-label="Delete credit" onClick={() => window.confirm("Delete this credit?") && run(() => api.deleteIncome(x.id))}>×</button>
              </div>
              {x.rawText && <div className="inbox-raw">{x.rawText}</div>}
              <div className="pick">
                {categories.map((c) => (
                  <button key={c.id} className="chip" onClick={() => run(() => api.categorizeIncome(x.id, c.name, remember[x.id]))}>
                    {c.icon} {c.name}
                  </button>
                ))}
                {x.sender && (
                  <label className="check">
                    <input type="checkbox" checked={!!remember[x.id]} onChange={(e) => setRemember({ ...remember, [x.id]: e.target.checked })} />
                    Always use this for {x.sender}
                  </label>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="grid g2 stack">
        <div className="card stat">
          <div className="label">Money in {sum.data ? sum.data.label : ""}</div>
          <div className="num in">{rupee(sum.data ? sum.data.total : 0)}</div>
          <div className="sub">{(list.data || []).length} credits</div>
        </div>
        <div className="card">
          <h2>By category</h2>
          {sum.data && sum.data.byCategory.length === 0 && <div className="empty">No credits in this period.</div>}
          {(sum.data ? sum.data.byCategory : []).map((c) => (
            <div key={c.name} className="row">
              <div className="ico">{c.icon}</div>
              <div className="grow">
                <div className="title">{c.name}</div>
                <div className="track in"><b style={{ width: `${c.percent}%` }} /></div>
              </div>
              <div className="amt">{rupee(c.amount)}</div>
            </div>
          ))}
        </div>
      </div>

      <div className="card stack">
        <h2>All credits</h2>
        {list.data && list.data.length === 0 && <div className="empty">Nothing received in this period.</div>}
        {(list.data || []).map((x) => (
          <div key={x.id} className="row">
            <div className="ico">{x.category ? x.category.icon : "❓"}</div>
            <div className="grow">
              <div className="title">{x.sender || x.note || "Money received"}</div>
              <div className="meta">{x.category ? x.category.name : "Needs a category"} · {fmtDate(x.receivedAt)}</div>
            </div>
            <span className={`badge ${x.source}`}>{x.source === "SMS" ? "SMS" : x.source === "CHAT" ? "Bot" : "Manual"}</span>
            <div className="amt in">+ {rupee(x.amount)}</div>
            <button className="x" aria-label="Delete credit" onClick={() => window.confirm("Delete this credit?") && run(() => api.deleteIncome(x.id))}>×</button>
          </div>
        ))}
      </div>

      <div className="card stack">
        <h2>Add money received by hand</h2>
        <form className="form" onSubmit={(e) => { e.preventDefault(); run(async () => { await api.addIncome(f); setF({ amount: "", sender: "", category: "", note: "" }); }); }}>
          <label className="field">Amount (₹)
            <input type="number" min="1" step="0.01" required value={f.amount} onChange={(e) => setF({ ...f, amount: e.target.value })} />
          </label>
          <label className="field">From
            <input value={f.sender} placeholder="Karthik" onChange={(e) => setF({ ...f, sender: e.target.value })} />
          </label>
          <label className="field">Category
            <select value={f.category} onChange={(e) => setF({ ...f, category: e.target.value })}>
              <option value="">Choose later</option>
              {categories.map((c) => <option key={c.id} value={c.name}>{c.icon} {c.name}</option>)}
            </select>
          </label>
          <button className="btn honey">Save</button>
        </form>
      </div>

      <div className="card stack">
        <h2>Your income categories</h2>
        <div className="chips">
          {categories.map((c) => (
            <span key={c.id} className="chip">
              {c.icon} {c.name}
              <button className="x" style={{ padding: "0 4px", fontSize: 15 }} aria-label={`Delete ${c.name}`} onClick={() => window.confirm(`Delete the category "${c.name}"?`) && run(() => api.deleteIncomeCategory(c.id))}>×</button>
            </span>
          ))}
        </div>
        <form className="form" onSubmit={(e) => { e.preventDefault(); run(async () => { await api.addIncomeCategory(nc); setNc({ name: "", icon: "" }); }); }}>
          <label className="field">New category<input required value={nc.name} onChange={(e) => setNc({ ...nc, name: e.target.value })} /></label>
          <label className="field">Emoji<input style={{ width: 70 }} value={nc.icon} onChange={(e) => setNc({ ...nc, icon: e.target.value })} /></label>
          <button className="btn">Add</button>
        </form>
      </div>
    </>
  );
}

/* ------------------------------------------------------------ budgets & goals */

function Budgets({ tick, bump }) {
  const budgets = useLoad(() => api.budgets(), [tick]);
  const goals = useLoad(() => api.goals(), [tick]);
  const cats = useLoad(() => api.categories(), []);
  const [b, setB] = useState({ category: "", amount: "", startDate: monthStart(), endDate: monthEnd() });
  const [g, setG] = useState({ name: "", targetAmount: "", deadline: "" });
  const [dep, setDep] = useState({});
  const [err, setErr] = useState("");

  const run = async (fn) => { setErr(""); try { await fn(); bump(); } catch (e) { setErr(e.message); } };

  return (
    <>
      <div className="head"><h1>Budgets and goals</h1></div>
      {err && <div className="err">{err}</div>}

      <div className="card">
        <h2>Set a budget for a period</h2>
        <form className="form" onSubmit={(e) => { e.preventDefault(); run(async () => { await api.addBudget(b); setB({ ...b, amount: "" }); }); }}>
          <label className="field">Category
            <select value={b.category} onChange={(e) => setB({ ...b, category: e.target.value })}>
              <option value="">Overall (all categories)</option>
              {(cats.data || []).map((c) => <option key={c.id} value={c.name}>{c.icon} {c.name}</option>)}
            </select>
          </label>
          <label className="field">Limit (₹)
            <input type="number" min="1" required value={b.amount} onChange={(e) => setB({ ...b, amount: e.target.value })} />
          </label>
          <label className="field">From
            <input type="date" required value={b.startDate} onChange={(e) => setB({ ...b, startDate: e.target.value })} />
          </label>
          <label className="field">To
            <input type="date" required value={b.endDate} onChange={(e) => setB({ ...b, endDate: e.target.value })} />
          </label>
          <button className="btn honey">Save budget</button>
        </form>
      </div>

      <div className="card stack">
        <h2>Active budgets</h2>
        <BackendDown err={budgets.err} />
        {budgets.data && budgets.data.length === 0 && <div className="empty">No budgets yet. Set one above, or tell the bot “set food budget 3000 this month”.</div>}
        {(budgets.data || []).map((x) => (
          <div key={x.id} className="row">
            <div className="ico">{x.icon}</div>
            <div className="grow">
              <div className="title">{x.name}</div>
              <div className={`track ${x.state}`}><b style={{ width: `${Math.min(x.percent, 100)}%` }} /></div>
              <div className="meta">
                {rupee(x.spent)} of {rupee(x.limit)} · {x.daysLeft} days left
                {x.state === "EXCEEDED" ? " · over the limit" : ` · ${rupee(x.dailyAllowance)} a day`}
              </div>
            </div>
            <div className="amt">{x.percent}%</div>
            <button className="x" aria-label="Delete budget" onClick={() => window.confirm("Delete this budget?") && run(() => api.deleteBudget(x.id))}>×</button>
          </div>
        ))}
      </div>

      <div className="card stack">
        <h2>Savings goals</h2>
        <form className="form" onSubmit={(e) => { e.preventDefault(); run(async () => { await api.addGoal(g); setG({ name: "", targetAmount: "", deadline: "" }); }); }}>
          <label className="field">Goal
            <input required value={g.name} placeholder="New laptop" onChange={(e) => setG({ ...g, name: e.target.value })} />
          </label>
          <label className="field">Target (₹)
            <input type="number" min="1" required value={g.targetAmount} onChange={(e) => setG({ ...g, targetAmount: e.target.value })} />
          </label>
          <label className="field">Deadline
            <input type="date" value={g.deadline} onChange={(e) => setG({ ...g, deadline: e.target.value })} />
          </label>
          <button className="btn">Create goal</button>
        </form>
        {goals.data && goals.data.length === 0 && <div className="empty">No goals yet.</div>}
        {(goals.data || []).map((x) => (
          <div key={x.id} className="row">
            <div className="ico">🎯</div>
            <div className="grow">
              <div className="title">{x.name}</div>
              <div className="track goal"><b style={{ width: `${x.percent}%` }} /></div>
              <div className="meta">
                {rupee(x.saved)} of {rupee(x.target)}
                {x.perMonthNeeded != null && ` · save ${rupee(x.perMonthNeeded)} a month to finish by ${x.deadline}`}
              </div>
            </div>
            <input type="number" style={{ width: 90 }} placeholder="₹ add" value={dep[x.id] || ""} onChange={(e) => setDep({ ...dep, [x.id]: e.target.value })} aria-label={`Amount to add to ${x.name}`} />
            <button className="btn small" onClick={() => dep[x.id] && run(async () => { await api.deposit(x.id, dep[x.id]); setDep({ ...dep, [x.id]: "" }); })}>Add</button>
            <button className="x" aria-label="Delete goal" onClick={() => window.confirm("Delete this savings goal?") && run(() => api.deleteGoal(x.id))}>×</button>
          </div>
        ))}
      </div>
    </>
  );
}

/* ------------------------------------------------------------ categories & keywords */

function Categories({ tick, bump }) {
  const cats = useLoad(() => api.categories(), [tick]);
  const [edit, setEdit] = useState({});
  const [n, setN] = useState({ name: "", icon: "", keywords: "" });
  const [err, setErr] = useState("");
  const run = async (fn) => { setErr(""); try { await fn(); bump(); } catch (e) { setErr(e.message); } };

  return (
    <>
      <div className="head"><h1>Categories and keywords</h1></div>
      <p className="note">Type one of these words in the note box when you pay in GPay and the payment lands in that category on its own.</p>
      {err && <div className="err">{err}</div>}
      <div className="card stack">
        {(cats.data || []).map((c) => (
          <div key={c.id} className="row" style={{ alignItems: "flex-start" }}>
            <div className="ico">{c.icon}</div>
            <div className="grow">
              <div className="title">{c.name}</div>
              <textarea rows={2} className="kw" aria-label={`Keywords for ${c.name}`}
                value={edit[c.id] ?? c.keywords ?? ""}
                onChange={(e) => setEdit({ ...edit, [c.id]: e.target.value })} />
            </div>
            <button className="btn small" onClick={() => run(async () => { await api.updateCategory(c.id, { keywords: edit[c.id] ?? c.keywords }); setEdit({ ...edit, [c.id]: undefined }); })}>Save</button>
            <button className="x" aria-label={`Delete ${c.name}`} onClick={() => window.confirm(`Delete the category "${c.name}"?`) && run(() => api.deleteCategory(c.id))}>×</button>
          </div>
        ))}
      </div>
      <div className="card stack">
        <h2>New category</h2>
        <form className="form" onSubmit={(e) => { e.preventDefault(); run(async () => { await api.addCategory(n); setN({ name: "", icon: "", keywords: "" }); }); }}>
          <label className="field">Name<input required value={n.name} onChange={(e) => setN({ ...n, name: e.target.value })} /></label>
          <label className="field">Emoji<input style={{ width: 70 }} value={n.icon} onChange={(e) => setN({ ...n, icon: e.target.value })} /></label>
          <label className="field" style={{ flex: 1 }}>Keywords, separated by commas
            <input value={n.keywords} placeholder="pet, vet, dogfood" onChange={(e) => setN({ ...n, keywords: e.target.value })} />
          </label>
          <button className="btn honey">Add category</button>
        </form>
      </div>
    </>
  );
}

/* ------------------------------------------------------------ chat bot */

const TIPS = ["spent 50 on snacks", "how much can I spend today?", "can I afford 500?", "coach me", "received 5000 salary", "set food budget 3000 this month"];

function ChatPanel({ open, onChanged, onClose }) {
  const [log, setLog] = useState([{ who: "bot", text: "Hi! Tell me what you spent, or ask me anything about your money." }]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const end = useRef(null);
  useEffect(() => { end.current?.scrollIntoView({ behavior: "smooth" }); }, [log]);

  async function send(msg) {
    const m = (msg ?? text).trim();
    if (!m || busy) return;
    setText("");
    setLog((l) => [...l, { who: "me", text: m }]);
    setBusy(true);
    try {
      const r = await api.chat(m);
      setLog((l) => [...l, { who: "bot", text: r.reply, engine: r.engine }]);
      if (r.changed) onChanged();
    } catch (e) {
      setLog((l) => [...l, { who: "bot", text: `I could not reach the backend: ${e.message}` }]);
    }
    setBusy(false);
  }

  return (
    <aside className={`chat ${open ? "open" : ""}`} aria-label="BudgetBee assistant">
      <header>
        <div>
          <h2>Ask BudgetBee</h2>
          <p>Add, change and look up expenses by chatting.</p>
        </div>
        <button className="chat-close" aria-label="Close chat" onClick={onClose}>×</button>
      </header>
      <div className="log">
        {log.map((m, i) => (
          <div key={i} className={`msg ${m.who}`}>
            {m.text}
            {m.engine && <small>{m.engine === "rules" ? "built-in parser" : m.engine}</small>}
          </div>
        ))}
        {busy && <div className="msg bot">Thinking…</div>}
        <div ref={end} />
      </div>
      <div className="chips" style={{ padding: "0 12px", margin: "8px 0 0" }}>
        {TIPS.slice(0, 3).map((t) => <button key={t} className="chip" onClick={() => send(t)}>{t}</button>)}
      </div>
      <form className="composer" onSubmit={(e) => { e.preventDefault(); send(); }}>
        <input value={text} onChange={(e) => setText(e.target.value)} placeholder="spent 120 on lunch" aria-label="Message" />
        <button className="btn" disabled={busy}>Send</button>
      </form>
    </aside>
  );
}

/* ------------------------------------------------------------ login / sign up */

function AuthScreen({ onLogin }) {
  const [mode, setMode] = useState("login");
  const [f, setF] = useState({ name: "", email: "", password: "" });
  const [show, setShow] = useState(false);
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);
  const signup = mode === "signup";

  async function submit(e) {
    e.preventDefault();
    setErr(""); setBusy(true);
    try {
      const r = signup ? await api.register(f.name, f.email, f.password) : await api.login(f.email, f.password);
      auth.set(r.token);
      onLogin(r.user);
    } catch (x) { setErr(x.message); }
    setBusy(false);
  }

  return (
    <div className="auth-wrap">
      <div className="auth-card">
        <div className="brand dark"><div className="hex">🐝</div><span>BudgetBee</span></div>
        <h1>{signup ? "Create your account" : "Welcome back"}</h1>
        <p className="note">Know what you can safely spend today. Your records stay saved until you delete them.</p>

        <div className="seg auth-seg" role="tablist">
          <button type="button" className={!signup ? "on" : ""} onClick={() => { setMode("login"); setErr(""); }}>Log in</button>
          <button type="button" className={signup ? "on" : ""} onClick={() => { setMode("signup"); setErr(""); }}>Sign up</button>
        </div>

        <form onSubmit={submit} className="auth-form">
          {signup && (
            <label className="field">Your name
              <input required autoComplete="name" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} />
            </label>
          )}
          <label className="field">Email
            <input type="email" required autoComplete="email" inputMode="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} />
          </label>
          <label className="field">Password {signup && <span className="hint">(at least 8 characters)</span>}
            <span className="pw">
              <input type={show ? "text" : "password"} required minLength={signup ? 8 : undefined}
                autoComplete={signup ? "new-password" : "current-password"}
                value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} />
              <button type="button" className="pw-toggle" onClick={() => setShow((v) => !v)}>{show ? "Hide" : "Show"}</button>
            </span>
          </label>
          {err && <div className="err" role="alert">{err}</div>}
          <button className="btn honey wide" disabled={busy}>{busy ? "Please wait…" : signup ? "Create account" : "Log in"}</button>
        </form>
        <p className="note auth-foot">
          {signup ? "Already have an account? " : "New here? "}
          <button type="button" className="link" onClick={() => { setMode(signup ? "login" : "signup"); setErr(""); }}>
            {signup ? "Log in" : "Create an account"}
          </button>
        </p>
      </div>
    </div>
  );
}

function Account({ user, onLogout, onDeleted }) {
  const [pw, setPw] = useState("");
  const [open, setOpen] = useState(false);
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  async function del(e) {
    e.preventDefault();
    if (!window.confirm("This permanently deletes your account and every record in it. It cannot be undone. Continue?")) return;
    setBusy(true); setErr("");
    try { await api.deleteAccount(pw); auth.clear(); onDeleted(); } catch (x) { setErr(x.message); }
    setBusy(false);
  }

  return (
    <>
      <div className="head"><h1>Account</h1></div>
      <div className="card">
        <div className="profile">
          <div className="avatar">{(user.name || "?").trim().charAt(0).toUpperCase()}</div>
          <div>
            <div className="title">{user.name}</div>
            <div className="meta">{user.email}</div>
          </div>
        </div>
        <p className="note">Your records are saved. Logging out does not delete anything. They are removed only when you delete them yourself.</p>
        <button className="btn" onClick={onLogout}>Log out</button>
      </div>
      <div className="card stack">
        <h2>Delete my account</h2>
        <p className="note">This removes your account, expenses, income, budgets and goals for good.</p>
        {!open ? (
          <button className="btn ghost danger" onClick={() => setOpen(true)}>I want to delete my account</button>
        ) : (
          <form className="form" onSubmit={del}>
            <label className="field">Type your password to confirm
              <input type="password" required autoComplete="current-password" value={pw} onChange={(e) => setPw(e.target.value)} />
            </label>
            <button className="btn danger" disabled={busy}>{busy ? "Deleting…" : "Delete everything"}</button>
            <button type="button" className="btn ghost" onClick={() => { setOpen(false); setPw(""); setErr(""); }}>Cancel</button>
          </form>
        )}
        {err && <div className="err">{err}</div>}
      </div>
    </>
  );
}

/* ------------------------------------------------------------ app shell */

const NAV = [
  ["dash", "📊", "Dashboard", "Home"],
  ["exp", "🧾", "Expenses", "Spend"],
  ["income", "💰", "Money in", "Income"],
  ["bud", "🎯", "Budgets and goals", "Budgets"],
  ["cat", "🏷️", "Categories", "Tags"],
  ["me", "👤", "Account", "Account"],
];

export default function App() {
  const [user, setUser] = useState(undefined);      // undefined = checking, null = logged out
  const [bootError, setBootError] = useState("");
  const [tab, setTab] = useState("dash");
  const [period, setPeriod] = useState("month");
  const [tick, setTick] = useState(0);
  const [chatOpen, setChatOpen] = useState(false);
  const bump = () => setTick((t) => t + 1);

  function boot() {
    setBootError("");
    if (!auth.token()) { setUser(null); return; }
    api.me().then(setUser).catch((e) => {
      if (auth.token()) { setBootError(e.message); setUser(undefined); }   // still logged in, server just unreachable
      else setUser(null);
    });
  }

  useEffect(() => {
    boot();
    const expired = () => setUser(null);
    window.addEventListener("auth-expired", expired);
    return () => window.removeEventListener("auth-expired", expired);
  }, []);

  async function logout() {
    try { await api.logout(); } catch { /* already logged out */ }
    auth.clear();
    setUser(null);
    setTab("dash");
    setChatOpen(false);
  }

  if (user === undefined) {
    return (
      <div className="auth-wrap">
        <div className="auth-card center">
          <div className="brand dark"><div className="hex">🐝</div><span>BudgetBee</span></div>
          {bootError ? (
            <>
              <p className="err">{bootError}</p>
              <button className="btn honey" onClick={boot}>Try again</button>
            </>
          ) : <p className="note">Loading…</p>}
        </div>
      </div>
    );
  }

  if (!user) return <AuthScreen onLogin={(u) => { setUser(u); setTab("dash"); bump(); }} />;

  return (
    <div className="shell" key={user.id}>
      <nav className="side" aria-label="Main">
        <div className="brand"><div className="hex">🐝</div><span>BudgetBee</span></div>
        {NAV.map(([k, icon, label]) => (
          <button key={k} className={`nav ${tab === k ? "on" : ""}`} onClick={() => setTab(k)}>
            <span>{icon}</span>{label}
          </button>
        ))}
        <div className="foot">
          <div className="who">{user.name}</div>
          <button className="btn ghost small light" onClick={logout}>Log out</button>
        </div>
      </nav>

      <header className="topbar">
        <div className="brand"><div className="hex">🐝</div><span>BudgetBee</span></div>
        <button className="topbar-user" aria-label="Account" onClick={() => setTab("me")}>{(user.name || "?").trim().charAt(0).toUpperCase()}</button>
      </header>

      <main className="main">
        {tab === "dash" && <Dashboard period={period} setPeriod={setPeriod} tick={tick} setTab={setTab} bump={bump} />}
        {tab === "exp" && <Expenses period={period} setPeriod={setPeriod} tick={tick} bump={bump} />}
        {tab === "income" && <MoneyIn period={period} setPeriod={setPeriod} tick={tick} bump={bump} />}
        {tab === "bud" && <Budgets tick={tick} bump={bump} />}
        {tab === "cat" && <Categories tick={tick} bump={bump} />}
        {tab === "me" && <Account user={user} onLogout={logout} onDeleted={() => { setUser(null); setTab("dash"); }} />}
      </main>

      <ChatPanel open={chatOpen} onChanged={bump} onClose={() => setChatOpen(false)} />
      <button className="fab" aria-label={chatOpen ? "Close assistant" : "Open assistant"} onClick={() => setChatOpen((o) => !o)}>{chatOpen ? "×" : "💬"}</button>

      <nav className="bottom-nav" aria-label="Main">
        {NAV.map(([k, icon, , short]) => (
          <button key={k} className={tab === k ? "on" : ""} onClick={() => setTab(k)}>
            <span className="bn-ico">{icon}</span>
            <span className="bn-lbl">{short}</span>
          </button>
        ))}
      </nav>
    </div>
  );
}
