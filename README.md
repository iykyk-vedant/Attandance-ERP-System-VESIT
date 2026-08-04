# OpenAttend — Live Companion Platform for VESIT Attendance

[![CI Pipeline](https://github.com/iykyk-vedant/OpenAttend-VESIT/actions/workflows/ci.yml/badge.svg)](https://github.com/iykyk-vedant/OpenAttend-VESIT/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

OpenAttend is a **read-only companion attendance platform** tailored for VESIT. Faculty continue marking attendance as before via Google Forms into Google Sheets. OpenAttend incrementally polls the spreadsheet via Google Sheets API, normalizes the logs into PostgreSQL, and serves a live real-time dashboard for students and admins.

---

## ⚡ Core Features

1. **Idempotent Sync Engine**: Zero duplicate records across repeated sync runs (`spreadsheets.readonly`).
2. **Attendance Predictor**: Calculates exact safe skips remaining or required consecutive attendance to reach 75%.
3. **Role-Based Access Control (RBAC)**: Dedicated views and permissions for Students, Faculty, and Admin.
4. **SyncPulse Indicator**: Real-time staleness tracking and live sync status breathing indicator.

---

## 🚀 Quick Start (Local Setup)

### Prerequisites
- Node.js 20+
- Docker & Docker Compose

### Running locally
```bash
# 1. Clone the repository
git clone https://github.com/iykyk-vedant/OpenAttend-VESIT.git
cd OpenAttend-VESIT

# 2. Start dev server
npm run dev
```
Open [http://localhost:3000](http://localhost:3000) in your browser.

---

## 🐳 Docker Deployment

```bash
docker compose -f infra/docker/docker-compose.yml up --build -d
```

---

## 🧪 Testing

```bash
# Run Predictor & Sync Unit Tests
npm test
```

---

## 📜 Documentation

- [Milestone Roadmap](docs/Milestone.md)
- [Product Requirements Document (PRD)](docs/PRD.md)
- [UI Specification](docs/UI.md)
