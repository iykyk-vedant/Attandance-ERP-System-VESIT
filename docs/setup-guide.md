# OpenAttend — Administrator Setup Guide (Java Spring Boot)

This guide walks a college administrator and developer through initializing OpenAttend from scratch using the **Java Spring Boot 3** backend and PostgreSQL.

---

## 📋 Prerequisites

- **Java 21 LTS** (`jdk-21` or OpenJDK 21+)
- **Apache Maven 3.9+** (or the included `./mvnw` wrapper)
- **Docker & Docker Compose** (for PostgreSQL database) or a managed PostgreSQL instance (e.g., Supabase / Neon / RDS)
- **Google Cloud Console Access** (to create a free Google Service Account with read-only sheets access)
- **Google Spreadsheet** containing attendance sheets marked by faculty.

---

## Step 1: Provision Google Service Account (Read-Only)

OpenAttend requires **read-only** access to poll attendance Google Sheets.

1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Create a new project named `OpenAttend-Sync`.
3. Enable the **Google Sheets API** and **Google Drive API** under **APIs & Services > Library**.
4. Go to **APIs & Services > Credentials > Create Credentials > Service Account**.
5. Name the service account `openattend-sheets-reader`.
6. Click **Keys > Add Key > Create New Key > JSON**.
7. Download the JSON key file.
8. *(Recommended for production / CI)* Base64-encode the JSON key:
   ```bash
   # Windows PowerShell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("path/to/service-account.json"))
   ```

---

## Step 2: Share Google Spreadsheet

1. Open your target Google Sheet (e.g. `VESIT Attendance 2026`).
2. Copy the **Service Account Email** (e.g. `openattend-sheets-reader@project.iam.gserviceaccount.com`).
3. Click **Share** in the top-right corner of your Google Sheet.
4. Paste the service account email and set permission to **Viewer** (Read-Only).

---

## Step 3: Configure Environment Variables

1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```
2. Configure your properties:
   ```env
   # PostgreSQL Connection
   DATABASE_URL="postgresql://postgres:password@localhost:5432/openattend?sslmode=disable"
   
   # JWT Configuration
   JWT_ACCESS_SECRET="your_32_character_super_secret_access_key_min_256bits"
   JWT_ACCESS_EXPIRY="15m"
   
   # Google Service Account (Base64 JSON or Raw JSON)
   GOOGLE_SERVICE_ACCOUNT_JSON="eyJ0eXBlIjoic2VydmljZV9hY2NvdW50Ii...=="
   ALLOWED_EMAIL_DOMAINS="ves.ac.in"
   
   # Sync & Application
   SYNC_DEFAULT_CRON="0 */15 * * * *"
   PORT=3000
   ```

---

## Step 4: Run Database Migrations & Start Backend

1. Start PostgreSQL database container (if running locally):
   ```bash
   docker compose -f infra/docker/docker-compose.yml up -d
   ```
2. Build and run the Spring Boot backend:
   ```bash
   cd backend
   mvn spring-boot:run
   ```
   *Note: Flyway automatically applies `V1__init_schema.sql` database migrations on startup.*

3. Verify health endpoint:
   ```bash
   curl http://localhost:3000/api/v1/health
   # Returns: {"status":"ok","service":"OpenAttend API"}
   ```

4. Open the Web Application at `http://localhost:3000` in your browser.

---

## Step 5: Admin Cold-Start Walkthrough

1. Log in with admin credentials (`admin@ves.ac.in` / `admin123`).
2. Navigate to **Admin > Sheet Connection**: Paste your Spreadsheet ID and click **Test Connection** to detect tabs.
3. Navigate to **Admin > Worksheet Mapping**: Map worksheet tabs (e.g., `CS401`) to subject codes and configure column roles (`Date`, `Roll No`, `Status`, `Faculty`).
4. Navigate to **Admin > Roster Import**: Upload your student roster CSV and preview record diffs (`CREATE` / `UPDATE` / `ERROR`) before committing.
5. Navigate to **Admin > Sync Control**: Click **Trigger Immediate Sync Run** to execute the initial sync and inspect the live `SyncLog`.
