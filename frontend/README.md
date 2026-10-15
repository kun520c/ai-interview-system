# AI Interview Frontend

Production Vue 3 application for the AI Interview System.

This is **not** the Design Freeze prototype in `docs/frontend/prototype/`.

```text
npm install
npm run dev
npm run typecheck
npm run lint
npm run test:unit
npm run build
```

Dev server proxies `/api` to `http://localhost:8080` (override with `AI_INTERVIEW_BACKEND_URL`). Do not enable backend CORS for local development.

Architecture: `docs/frontend/FRONTEND_ARCHITECTURE.md`
