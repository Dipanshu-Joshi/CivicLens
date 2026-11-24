# CivicSense frontend

## Local demo mode

The fastest way to explore the complete CivicSense workflow is the built-in
development demo. It needs **no Spring Boot server, database, object storage,
AI key, or login credentials outside this page**.

From `frontend/`:

```powershell
npm install
npm run dev
```

Open <http://localhost:5173>. You can also run `.\scripts\run-frontend.ps1`
from the repository root.

### Demo accounts

The sign-in page shows three one-click demo cards. You can also type the
credentials manually:

| Role | Email | Password | Landing page |
| --- | --- | --- | --- |
| Citizen | `citizen@demo.com` | `Demo@123` | `/dashboard` |
| Department admin | `department@demo.com` | `Demo@123` | `/department` |
| Super admin | `admin@demo.com` | `Demo@123` | `/admin` |

A `DEMO MODE` badge is visible in the app shell and on the sign-in page. Demo
mode is on by default in Vite dev; set `VITE_DEMO_MODE=false` only when you need
the original network-stubbed browser tests to run against the same dev server.

### What is simulated

- **Auth and sessions** are local. Demo state and the active session persist in
  `localStorage` under `civicsense.demo.state.v1` and
  `civicsense.demo.session.v1`. The access token stays in memory.
- **Complaints, departments, users, notifications, timelines, analysis results,
  routing candidates, audit events and analytics** come from typed seed data.
- **AI classification and routing** are deterministic local simulations and are
  labelled as simulated in the UI. No external AI service is contacted.
- **Photo uploads** are intercepted before `fetch`: previews are kept locally as
  data URLs and staged images never leave the browser. Native object-URL
  previews in the photo capture flow still work.
- **Maps** keep the OpenStreetMap interface and fall back gracefully when tiles
  cannot load.
- Every local mutation fires a `civicsense:demo-change` event so React Query
  caches refresh across the app.

The demo contains 15 realistic Bhopal complaints, five departments, at least
10 users and 10 notifications, plus supporting timelines, AI analyses, routing
decisions and audit history.

### Switching and resetting

Use **Switch demo account** in the sidebar to change roles without signing out.
Use **RESET DEMO DATA** to restore the original seed and clear the demo session;
the browser asks for confirmation first. Resetting never touches a real backend.

### Production behaviour

Demo mode is development-only. Vite replaces demo imports with the no-op module
in `src/demo/prodStub.ts` during production builds, so the adapter, seeded
credentials and demo UI are not shipped. Production keeps the real Axios
transport, real auth, refresh flow and backend URLs unchanged.

### Verification

```powershell
npm run test
npm run lint
npm run build
```

The demo test suite covers credentials, role restrictions, persistence, logout
and switching, citizen/department/admin workflows, local uploads, route guards,
reset, production exclusion and the absence of backend network calls.

---

The remainder of this file is the original Vite template reference.

# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some Oxlint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the Oxlint configuration

If you are developing a production application, we recommend enabling type-aware lint rules. Check out the [oxlint documentation](https://oxc.rs/docs/guide/usage/linter) for more information.
