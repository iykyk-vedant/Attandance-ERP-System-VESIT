# OpenAttend — Administrator Setup Guide

This guide walks a college administrator through initializing OpenAttend from scratch to a live synchronized attendance dashboard.

---

## 📋 Prerequisites

- **Node.js 20+** and **npm**
- **Docker & Docker Compose** (for local PostgreSQL database)
- **Google Cloud Console Access** (to create a free Google Service Account)
- **Google Spreadsheet** containing attendance sheets marked by faculty via Google Forms.

---

## Step 1: Provision Google Service Account (Read-Only)

OpenAttend requires **read-only** access to poll attendance Google Sheets.

1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Create a new project named `OpenAttend-Sync`.
3. Enable the **Google Sheets API** and **Google Drive API** under **APIs & Services > Library**.
4. Go to **APIs & Services > Credentials > Create Credentials > Service Account**.
5. Name the service account `openattend-sheets-reader`.
6. Click **Keys > Add Key > Create New Key > JSON**.
7. Download the JSON key file and save its contents securely.

---

## Step 2: Share Google Spreadsheet

1. Open your target Google Sheet (e.g. `VESIT Attendance 2026`).
2. Copy the **Service Account Email** (e.g. `openattend-sheets-reader@project.iam.gserviceaccount.com`).
3. Click **Share** in the top-right corner of your Google Sheet.
4. Paste the service account email and set permission to **Viewer** (Read-Only).

---

## Step 3: Configure Environment (.env)

1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```
2. Set your environment variables in `.env`:
   ```env
   DATABASE_URL="postgresql://openattend:openattend_secure_password@localhost:5432/openattend?schema=public"
   JWT_ACCESS_SECRET="your_32_character_access_token_secret_key"
   JWT_REFRESH_SECRET="your_32_character_refresh_token_secret_key"
   GOOGLE_SERVICE_ACCOUNT_JSON='{"type":"service_account",...}'
   ALLOWED_EMAIL_DOMAINS="ves.ac.in"
   ```

---

## Step 4: Launch Database & Application

1. Start PostgreSQL database container:
   ```bash
   docker compose -f infra/docker/docker-compose.yml up -d
   ```
2. Run database migrations:
   ```bash
   npx prisma migrate dev
   ```
3. Start the application dev server:
   ```bash
   npm run dev
   ```
4. Access the admin interface at `http://localhost:3000`.

---

## Step 5: Admin Cold-Start Walkthrough

1. Log in with admin credentials (`admin@ves.ac.in` / `admin123`).
2. Navigate to **Admin > Sheet Connection**: Paste your Spreadsheet ID and click **Test Connection**.
3. Navigate to **Admin > Worksheet Mapping**: Map worksheet tabs (e.g., `CS401`) to subject codes and assign column roles (`Date`, `Roll No`, `Status`, `Faculty`).
4. Navigate to **Admin > Roster Import**: Upload your student roster CSV and preview record diffs (`CREATE` / `UPDATE` / `ERROR`) before committing.
5. Navigate to **Admin > Sync Control**: Click **Trigger Immediate Sync Run** to perform the initial sync.
