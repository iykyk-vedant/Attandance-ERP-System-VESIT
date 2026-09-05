# Contributing to OpenAttend — VESIT

Thank you for your interest in contributing to OpenAttend! We welcome contributions from students, researchers, and developers.

---

## 🧭 Finding Issues to Work On

Check out our **[GitHub Issues](https://github.com/iykyk-vedant/Attandance-ERP-System-VESIT/issues)**:
- Filter by label **`good first issue`** for beginner-friendly starter tasks.
- Browse by area: `area: backend`, `area: frontend`, `area: pwa`, `area: devops`, `area: reporting`.
- Browse our roadmap milestones: **[GitHub Milestones](https://github.com/iykyk-vedant/Attandance-ERP-System-VESIT/milestones)**.

> [!IMPORTANT]
> **Claim Before You Code**: Please comment on an issue to get assigned before you start working on it. This prevents duplicate effort and ensures smooth collaboration!

---

## 🚀 Local Development Setup

### 1. Fork & Clone
```bash
git clone https://github.com/YOUR_USERNAME/Attandance-ERP-System-VESIT.git
cd Attandance-ERP-System-VESIT
```

### 2. Start PostgreSQL Database
```bash
docker compose up -d postgres
```
*(Or use `npm run docker:up`)*

### 3. Run the Spring Boot Backend
Ensure you have **Java 21 LTS** and **Maven** installed:
```bash
cd backend
mvn spring-boot:run
```
*(Or from the project root: `npm run dev`)*

The application will start on `http://localhost:8080`.

---

## 🧪 Testing & Validation

Before submitting a Pull Request, ensure all tests pass cleanly:

```bash
cd backend
mvn clean test
```
*(Or from root: `npm test`)*

Our automated test suite verifies:
1. **Predictor Math Engine**: Safe skips and consecutive recovery targets.
2. **Attendance Marking & Idempotency**: Atomic upsert, present/absent segregation, SHA-256 deduplication.
3. **Security & RBAC Matrix**: `@ves.ac.in` domain validation, JWT tokens, teacher subject allocation guards.

---

## 📜 Pull Request Guidelines

- **Branch Naming**: Use descriptive prefixes: `feat/issue-number-description`, `fix/...`, `docs/...`.
- **Commit Messages**: Follow Conventional Commits format (e.g. `feat(marking): add 10-second absentees fast toggle`).
- **One PR per Issue**: Keep pull requests focused on a single issue for faster reviews.
- **Strict Read-Only Boundary**: Never introduce Google API write scopes or write methods.
- **Environment Safety**: Never commit real secret keys or `.env` files.
