<p align="center"> 
  <br/>
  <a href="https://github.com/iykyk-vedant/OpenAttend-VESIT/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg?color=3F51B5&style=for-the-badge&label=License&logoColor=000000&labelColor=ececec" alt="License: MIT"></a>
  <a href="https://github.com/iykyk-vedant/OpenAttend-VESIT/actions/workflows/ci.yml">
    <img src="https://img.shields.io/github/actions/workflow/status/iykyk-vedant/OpenAttend-VESIT/ci.yml?branch=main&label=CI%20Pipeline&logo=github&style=for-the-badge&logoColor=000000&labelColor=ececec" alt="CI Pipeline"/>
  </a>
  <br/>
  <br/>
</p>

<p align="center">
  <h1 align="center">OpenAttend — VESIT</h1>
</p>
<h3 align="center">High performance live companion attendance platform for VESIT</h3>
<br/>

> [!IMPORTANT]
> ⚠️ **OpenAttend** is a read-only companion attendance platform tailored for VESIT. Faculty mark attendance via Google Forms into Google Sheets while OpenAttend normalizes and syncs data into PostgreSQL in real time!

> [!NOTE]
> Detailed documentation, technical specs, and roadmap can be found in the [`docs/`](docs/) directory.

## Links

- [Documentation](docs/PRD.md)
- [Milestone Roadmap](docs/Milestone.md)
- [UI Specification](docs/UI.md)
- [Features](#features)
- [Quick Start](#quick-start-local-setup)
- [Docker Setup](#docker-deployment)
- [Testing](#testing)

## Features

| Features                                     | Student | Admin / Faculty |
| :------------------------------------------- | :-----: | :-------------: |
| Real-time Google Sheets Sync & Normalization | Yes     | Yes             |
| Idempotent Sync Engine (Zero Duplicates)    | Yes     | Yes             |
| Attendance Predictor (Safe Skips & Targets)  | Yes     | Yes             |
| SyncPulse Staleness & Live Status Indicator  | Yes     | Yes             |
| Role-Based Access Control (RBAC)             | Yes     | Yes             |
| Interactive Analytics & Visual Graphs        | Yes     | Yes             |
| User & System Admin Management               | No      | Yes             |

## Quick Start (Local Setup)

### Prerequisites
- **Node.js 20+**
- **Docker & Docker Compose**

### Running locally
```bash
# 1. Clone the repository
git clone https://github.com/iykyk-vedant/OpenAttend-VESIT.git
cd OpenAttend-VESIT

# 2. Start dev server
npm run dev
```
Open [http://localhost:3000](http://localhost:3000) in your browser.

## Docker Deployment

```bash
docker compose -f infra/docker/docker-compose.yml up --build -d
```

## Testing

```bash
# Run Predictor & Sync Unit Tests
npm test
```

## Repository activity

![Activities](https://repobeats.axiom.co/api/embed/iykyk-vedant/OpenAttend-VESIT "Repobeats analytics image")

## Star history

<a href="https://star-history.com/#iykyk-vedant/OpenAttend-VESIT&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=iykyk-vedant/OpenAttend-VESIT&type=date&theme=dark" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=iykyk-vedant/OpenAttend-VESIT&type=date" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=iykyk-vedant/OpenAttend-VESIT&type=date" width="100%" />
 </picture>
</a>

## Contributors

<a href="https://github.com/iykyk-vedant/OpenAttend-VESIT/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=iykyk-vedant/OpenAttend-VESIT" width="100%"/>
</a>
