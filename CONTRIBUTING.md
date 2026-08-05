# Contributing to OpenAttend

Thank you for your interest in contributing to OpenAttend! We welcome contributions from developers, researchers, and students.

---

## 🚀 Getting Started

### 1. Fork & Clone
```bash
git clone https://github.com/YOUR_USERNAME/OpenAttend-VESIT.git
cd OpenAttend-VESIT
```

### 2. Install Dependencies
```bash
npm install
```

### 3. Local Development Server
```bash
npm run dev
```
Open `http://localhost:3000` in your browser.

---

## 🧪 Testing & Validation

Before submitting a Pull Request, ensure all tests pass cleanly:

```bash
npm test
```

Tests verify:
1. **Predictor Core**: Standalone math calculations (`packages/predictor-core/test.js`).
2. **Sync Engine Core**: Idempotency, SHA-256 range hashing, malformed row isolation (`apps/api/test/sync.test.js`).
3. **Milestone Integrated Services**: Auth, APIs, RBAC matrix, rate limiting (`apps/api/test/milestones.test.js`).

---

## 📜 Pull Request Guidelines

- **Branch Naming**: Use descriptive prefixes: `feat/...`, `fix/...`, `docs/...`, `refactor/...`.
- **Commit Messages**: Follow Conventional Commits format (e.g. `feat(sync): add drive modifiedTime short-circuit`).
- **Scope Restriction**: Ensure no Google API write methods or write scopes are introduced anywhere in the repository — OpenAttend is strictly read-only.
- **Environment Safety**: Never commit real secret keys or `.env` files.
