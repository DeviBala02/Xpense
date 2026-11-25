# BudgetBee 🐝

A personal expense and savings tracker that answers one question: **"How much can I safely spend today?"**
Spring Boot + React with a local AI model (Ollama) for chat and coaching. It has **accounts (login and logout)**,
**keeps your records until you delete them**, and **works on your phone**.

![Dashboard](docs/budgetbee-dashboard.png)

![On a phone: login, dashboard and chat](docs/budgetbee-mobile.png)

## The problem with existing apps

People download expense trackers and abandon them within weeks. User posts and articles repeat the same complaints:

| Complaint | What users say |
|-----------|----------------|
| **They show history, not a decision.** | A month-end chart that says "you spent money" is too late. People prefer one number: how much is left to spend. |
| **Budgets are static and disconnected from savings goals.** | A budget set once becomes irrelevant. Goals sit on another screen, so overspending never shows what it costs your goal. |
| **Tracking is unpaid work.** | Manual entry, endless categories and clean-up make people quit. |
| **Privacy.** | Many apps ask for bank access or upload your data to a cloud. |

## How BudgetBee solves it

**1. A daily "Safe to spend" number that updates itself.**
```
money you can spend this month = income  -  money reserved for savings goals
daily limit                    = (what was left at the start of today) / days left in the month
safe to spend today            = daily limit  -  spent today
```
**2. Goal impact on every decision ("Can I afford it?").** Spending beyond today's limit comes out of your savings.
BudgetBee shows how many days that delays your goal and what it does to your daily limit.

**3. Low-effort entry.** One-line "Add an expense" box, or chat: "spent 50 on snacks". The category is worked out for you.

**4. AI you can trust with money.** A local model writes the coach's tips, but all numbers come from the database.
`NumberGuard` rejects any AI text that contains a number it was not given.

**5. Private by design.** No bank login and no cloud. The AI runs on your machine.

## Accounts, saved records and phone use

- **Sign up / log in / log out.** Passwords are stored as BCrypt hashes. A login is a random token kept on the device
  (only its SHA-256 hash is stored on the server). **Logging out ends that login immediately.**
  Five wrong passwords lock that email for 10 minutes.
- **Every user has separate data.** Every record has an owner, and every query is filtered by the logged-in user.
- **Records are kept.** Data is saved in a database file (`backend/data/`), so it is still there when you start the app
  again. Nothing is deleted unless you delete it. Every delete asks first. **Account > Delete my account** removes
  everything for good (it needs your password).
- **Works on a phone.** The layout adapts: bottom tab bar, large tap targets, full-screen chat, notch-safe spacing.
  Open it in your phone's browser and use "Add to Home screen" to get an app icon (it is a Progressive Web App).

## Run it (easiest: one window)

You need Java 21, Maven 3.9+ and Node 18+ installed. Then:

- **Windows:** double-click `start-app.bat`
- **Mac / Linux:** run `./start-app.sh`

It builds the web page, starts the server, and BudgetBee opens at **http://localhost:8080**.
The first run downloads libraries and takes a few minutes. Create an account, then press **Load sample data** on the
dashboard to see a full month.

Doing it by hand instead:
```bash
cd frontend && npm install && npm run build      # puts the web page inside the backend
cd ../backend && mvn spring-boot:run             # open http://localhost:8080
```

### Use it on your phone

1. Phone and computer on the **same Wi-Fi**. Start BudgetBee on the computer.
2. Find the computer's IP address (Windows: `ipconfig`, look for "IPv4 Address"; Mac: System Settings > Wi-Fi > Details).
3. On the phone's browser open `http://<that-ip>:8080`, for example `http://192.168.1.20:8080`.
4. If Windows asks about the firewall for Java, allow **Private networks**.
5. Optional: browser menu > **Add to Home screen**.

This works only while the computer is on and on the same network. For use anywhere, deploy the app to a server and
put it behind HTTPS (see Roadmap). Do not expose it to the internet over plain HTTP.

### Developing the screen (hot reload)
```bash
cd backend && mvn spring-boot:run          # terminal 1
cd frontend && npm install && npm run dev  # terminal 2, open http://localhost:5173
# npm run dev:phone  also lets a phone on your Wi-Fi open http://<ip>:5173
```

### Data and backups
- Default: a file database at `backend/data/budgetbee.mv.db`. **Back up by copying the `data` folder** (stop the app first).
- MySQL instead: `docker compose up -d`, then `mvn spring-boot:run "-Dspring-boot.run.profiles=mysql"`.
  If you used an earlier version of this project with MySQL, drop the `budgetbee` database once, because tables now belong to users.

### Turn on the AI model (optional, free)
```bash
ollama pull llama3.2
AI_ENABLED=true mvn spring-boot:run     # Windows cmd: set AI_ENABLED=true  then  mvn spring-boot:run
```
Without it, built-in rules and templates are used. Each chat reply shows which engine answered.

### Run the tests
```bash
cd backend && mvn test
```

## Try these in the chat

| Say | Result |
|-----|--------|
| `spent 50 on snacks` | adds the expense and shows what is left to spend today |
| `how much can I spend today?` | your safe-to-spend number |
| `can I afford 1000?` | verdict, goal delay and new daily limit |
| `coach me` | AI tip (checked by NumberGuard) |
| `how much did I spend on food this week?` | totals by category and period |
| `set food budget 5000 this month` | budget with 80% warning |
| `save 40000 for laptop` | savings goal |
| `received 5000 salary` | records money in |
| `delete last expense` | undo |

## Architecture

```
React (Vite, PWA) ──► REST API (Spring Boot + Spring Security) ──► H2 file DB  (or MySQL)
                         │
                         ├─ TokenAuthFilter     "Authorization: Bearer <token>" → current user
                         ├─ AuthService         sign up, log in/out, lockout, delete account
                         ├─ PlanCalculator      pure maths: safe-to-spend and what-if (unit tested)
                         ├─ CoachService        facts → local AI text → NumberGuard → fallback template
                         ├─ RuleParser / ChatService   chat understanding with or without AI
                         └─ Budget / Income / Expense services   all scoped to the logged-in user
```

## Design decisions

- **Plain code does the maths, AI does the words.** Money numbers must be exact. A guard rejects invented numbers.
- **Graceful degradation.** Every AI feature has a rules-based fallback.
- **The calculator is pure Java**, so it is fast to test.
- **Server-side tokens, not stateless JWT.** Logout must really end a session, so tokens are stored (hashed) and deleted on logout.
- **One owner id on every row, and `CurrentUser.id()` in every service.** Deleting by id also checks the owner.
- **One server for everything.** `npm run build` puts the web page inside the backend, so a single program serves the app.

## Tests (26)

`PlanCalculatorTest` (11), `NumberGuardTest` (5), `LoginThrottleTest` (6), `TokenUtilTest` (4).

## API

| Endpoint | Purpose |
|----------|---------|
| `POST /api/auth/register`, `POST /api/auth/login` | create account, log in (returns a token) |
| `POST /api/auth/logout`, `GET /api/auth/me` | log out this device, who am I |
| `POST /api/auth/delete-account` | delete account and all data (needs password) |
| `GET /api/health` | is the server up |
| `GET /api/plan/today`, `GET /api/plan/what-if?amount=`, `GET /api/plan/coach` | safe to spend, can I afford it, AI coach |
| `GET/PUT /api/settings` | expected monthly income |
| `POST /api/chat` | chat assistant |
| `GET/POST/DELETE /api/expenses`, `/api/budgets`, `/api/goals`, `/api/categories` | manage data |
| `GET/POST/PUT/DELETE /api/income...` | money in |
| `GET /api/summary?period=month` | dashboard numbers |
| `POST /api/demo/seed` | load sample data into an empty account |

Everything except register, login and health needs the header `Authorization: Bearer <token>`.

## Project layout

```
start-app.bat / start-app.sh   one-click start
backend/src/main/java/com/budgetbee/
  security/  SecurityConfig, TokenAuthFilter, CurrentUser, TokenUtil, LoginThrottle, PasswordConfig
  model/     User, AuthToken, Category, Expense, Income, IncomeCategory, SenderRule, Budget, SavingsGoal, Settings
  repo/      Spring Data repositories (every query filtered by user id)
  service/   AuthService, PlanCalculator, PlanService, CoachService, NumberGuard, OllamaClient, RuleParser,
             ChatService, ParserService, BudgetService, IncomeService, ExpenseService, DefaultsService, DemoService
  web/       REST controllers
backend/src/test/   unit tests
frontend/           React app (src/App.jsx, api.js, styles.css) and PWA files (public/)
docker-compose.yml  optional MySQL
```

## Troubleshooting

- **Page says the web page has not been built:** run `npm run build` in `frontend` (or use `start-app.bat`), then restart.
- **Cannot reach the server:** the backend window was closed or is still starting. Wait for "Started BudgetBeeApplication".
- **Phone cannot open the address:** same Wi-Fi? Use the computer's IP, not `localhost`. Allow Java in the firewall.
- **Forgot password:** there is no reset yet (see Roadmap). On your own computer you can delete the `backend/data` folder to start fresh.
- **Port 8080 busy:** close the other program, or start with `--server.port=8081`.

## Roadmap

Password reset and change password, HTTPS deployment (Dockerfile and a hosted demo), bank statement import (CSV),
merchant rules, recurring bills, weekly AI summary, CI pipeline.
