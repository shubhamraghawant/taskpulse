# ⚡ TaskPulse — Distributed Task Scheduling & Worker Coordination Engine

![Java](https://img.shields.io/badge/Java-21-orange?logo=java)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen?logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql)
![Docker](https://img.shields.io/badge/Docker-Compose-blue?logo=docker)
![OpenAPI](https://img.shields.io/badge/OpenAPI-Swagger-green?logo=swagger)
![License](https://img.shields.io/badge/License-MIT-green)

A high-performance, fault-tolerant **distributed task scheduling and worker coordination engine** designed for cloud-native microservice clusters. Built with **Java 21, Spring Boot 3.2, PostgreSQL 16, and Docker**, featuring **distributed leader leasing**, **priority-aware worker pools**, **exponential backoff retries**, and a **dead-letter queue (DLQ)**.

---

## 🏛️ Architecture Overview

In distributed container environments (Kubernetes/ECS), multiple application replicas run simultaneously. TaskPulse solves the duplicate-execution and split-brain problem via an **atomic database leader lease**:

```
                       ┌──────────────────────────────────────────────┐
                       │          Client / Upstream Services          │
                       └──────────────────────┬───────────────────────┘
                                              │ HTTP REST / Swagger
                                              ▼
               ┌─────────────────────────────────────────────────────────────┐
               │              TaskPulse Multi-Node Cluster                   │
               │                                                             │
               │   ┌────────────────────────┐   ┌────────────────────────┐   │
               │   │  Node Alpha (LEADER)   │   │  Node Beta (FOLLOWER)  │   │
               │   │                        │   │                        │   │
               │   │ • Heartbeat Lease: OK  │   │ • Standby Heartbeat    │   │
               │   │ • Polls Due Tasks      │   │ • No Polling (Standby) │   │
               │   │ • PriorityWorkerPool   │   │ • Instant Failover     │   │
               │   └───────────┬────────────┘   └───────────┬────────────┘   │
               └───────────────┼────────────────────────────┼────────────────┘
                               │                            │
                               ▼ Atomic Lease & Pessimistic ▼
               ┌─────────────────────────────────────────────────────────────┐
               │                        PostgreSQL 16                        │
               │  ├── leader_leases  (Atomic heartbeat & failover locking)   │
               │  ├── tasks          (CRON, DELAYED, ONE_OFF definitions)    │
               │  └── task_executions (Full audit trail & telemetry)        │
               └─────────────────────────────────────────────────────────────┘
```

### Core Architectural Components
1. **Leader Election & Heartbeat Leasing (`LeaderElectionService`):**
   * Uses an atomic database conditional update to renew a 10-second lease key (`TASK_PULSE_LEADER`).
   * Only the elected active leader node queries and dispatches due tasks.
   * If the leader pod crashes, follower nodes detect the expired lease within 3 seconds and elect a new leader with zero job loss.
2. **Priority-Aware Worker Pool (`PriorityWorkerPool`):**
   * Employs an internal `PriorityBlockingQueue` ordering runnable jobs by priority (`CRITICAL` > `HIGH` > `NORMAL` > `LOW`) and timestamp.
   * Isolates task scheduling from execution threads, preventing long-running tasks from starving the dispatcher.
3. **Resilient Retry State Machine & DLQ (`TaskExecutorService`):**
   * Calculates exponential backoff on transient failures: $\text{delay} = 5 \times 2^{(\text{attempt} - 1)}$ seconds.
   * Automatically isolates chronically failing tasks into a `DEAD_LETTER` state once `maxRetries` is exhausted.
4. **Comprehensive Execution Telemetry (`TaskExecutionEntity`):**
   * Audits every task invocation with duration (ms), executing cluster node ID, attempt number, status, exit message, and stack traces.

---

## ✨ Features & Capabilities

- ⏱️ **Cron & Delayed Scheduling** — Support for 6-field Spring Cron expressions and ISO-8601 delayed timestamps.
- 🚦 **Task Lifecycle Controls** — Dynamically pause, resume, cancel, or trigger tasks immediately via REST.
- 🎯 **Priority Ingestion** — Critical incident remediation jobs preempt low-priority batch jobs.
- 📊 **Cluster Health & Queue Telemetry** — Real-time metrics on leader identity, worker pool saturation, and queue depth.
- 📖 **Interactive OpenAPI Documentation** — Full Swagger UI pre-configured at `/swagger-ui.html`.
- 🐳 **Turnkey Multi-Node Docker Compose** — Boots PostgreSQL + 2 clustered TaskPulse nodes (`node-alpha` & `node-beta`) demonstrating live leader coordination.

---

## 🚀 Quick Start

### Prerequisites
* **Docker** & **Docker Compose**
* **JDK 21** & **Maven 3.9+** (for local development)

### 1. Clone the repository
```bash
git clone https://github.com/shubhamraghawant/taskpulse.git
cd taskpulse
```

### 2. Launch Clustered System with Docker Compose
```bash
docker compose up --build -d
```
This starts:
* **PostgreSQL 16** (`:5432`)
* **TaskPulse Node 1 (Alpha)** on `http://localhost:8085`
* **TaskPulse Node 2 (Beta)** on `http://localhost:8086`

### 3. Verify Cluster Leadership & Health
```bash
curl http://localhost:8085/api/v1/cluster/status
```

---

## 📡 API Usage Guide

### 1. Schedule a Recurring Cron Task
```bash
curl -X POST http://localhost:8085/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "name": "billing-reconciliation",
    "description": "Daily settlement with banking payment gateway",
    "type": "CRON",
    "cronExpression": "0 0 2 * * *",
    "priority": "HIGH",
    "maxRetries": 3,
    "payload": "{\"partner\": \"stripe\", \"batchSize\": 1000}"
  }'
```

### 2. Schedule a Delayed One-Off Task
```bash
curl -X POST http://localhost:8085/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "name": "send-onboarding-followup",
    "type": "DELAYED",
    "scheduledAt": "2026-11-01T10:00:00Z",
    "priority": "NORMAL",
    "payload": "{\"userId\": \"usr_4920\"}"
  }'
```

### 3. Trigger Immediate Execution (Out-of-Schedule)
```bash
curl -X POST http://localhost:8085/api/v1/tasks/{task-id}/trigger
```

### 4. Pause and Resume a Task
```bash
# Pause
curl -X PATCH http://localhost:8085/api/v1/tasks/{task-id}/pause

# Resume
curl -X PATCH http://localhost:8085/api/v1/tasks/{task-id}/resume
```

### 5. Inspect Execution Audit History
```bash
curl "http://localhost:8085/api/v1/executions/task/{task-id}?page=0&size=10"
```

---

## 🧪 Testing

Run the full unit and integration test suite:
```bash
mvn clean test
```

---

## 📄 License
This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
