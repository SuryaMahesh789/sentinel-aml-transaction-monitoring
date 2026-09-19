# 🛡️ Sentinel — Real-Time AML Transaction Monitoring System

<p align="center">
  <strong>Real-Time Anti-Money Laundering Transaction Monitoring for MeridianTrust Bank</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen?logo=springboot" />
  <img src="https://img.shields.io/badge/PostgreSQL-17-blue?logo=postgresql" />
  <img src="https://img.shields.io/badge/Flyway-Database%20Migration-red" />
  <img src="https://img.shields.io/badge/API-REST-blue" />
  <img src="https://img.shields.io/badge/OpenAPI-Swagger-green?logo=swagger" />
  <img src="https://img.shields.io/badge/Tests-JUnit%20%7C%20Mockito-green" />
</p>

---

## 📌 Overview

**Sentinel** is a real-time Anti-Money Laundering (AML) transaction monitoring platform designed for **MeridianTrust Bank**.

The system ingests customer, account, and transaction data, normalizes transaction values to INR, evaluates transactions against configurable AML detection rules, calculates risk scores, and generates explainable alerts for suspicious activity.

The platform is designed with a **rule-based detection engine** where each AML scenario is implemented as an independent detection rule, making the system easier to extend, test, and maintain.

---

## 🎯 Problem Statement

Financial institutions process large volumes of transactions every day. Manually identifying suspicious activity is difficult, especially when suspicious behavior is distributed across multiple transactions or occurs over a period of time.

Sentinel addresses this by automatically identifying patterns such as:

- Large-value transactions
- Structuring / transaction splitting
- Rapid movement of deposited funds
- High-risk or sanctioned jurisdictions
- Unusual behavioral deviations from historical activity

Each detected pattern produces an explainable result that can be converted into a risk-scored alert for investigation.

---

# 🏗️ Architecture

```text
                         ┌─────────────────────────┐
                         │      API / Swagger      │
                         └────────────┬────────────┘
                                      │
                                      ▼
                         ┌─────────────────────────┐
                         │    REST Controllers     │
                         └────────────┬────────────┘
                                      │
                                      ▼
                         ┌─────────────────────────┐
                         │      Service Layer      │
                         └────────────┬────────────┘
                                      │
                                      ▼
                         ┌─────────────────────────┐
                         │    Detection Engine     │
                         └────────────┬────────────┘
                                      │
             ┌────────────────────────┼────────────────────────┐
             │                        │                        │
             ▼                        ▼                        ▼
      ┌─────────────┐         ┌─────────────┐         ┌─────────────┐
      │  CTR Rule   │         │ Structuring │         │    Rapid    │
      │             │         │    Rule     │         │  Movement   │
      └─────────────┘         └─────────────┘         └─────────────┘
             │                        │                        │
             └────────────────────────┼────────────────────────┘
                                      │
                         ┌────────────▼────────────┐
                         │    High-Risk Rule       │
                         │    Behavioral Rule      │
                         └────────────┬────────────┘
                                      │
                                      ▼
                         ┌─────────────────────────┐
                         │      Risk Scoring       │
                         │         0 – 100         │
                         └────────────┬────────────┘
                                      │
                                      ▼
                         ┌─────────────────────────┐
                         │         Alerts          │
                         └────────────┬────────────┘
                                      │
                                      ▼
                         ┌─────────────────────────┐
                         │    Case Management      │
                         └────────────┬────────────┘
                                      │
                                      ▼
                         ┌─────────────────────────┐
                         │      Audit Trail        │
                         └────────────┬────────────┘
                                      │
                                      ▼
                         ┌─────────────────────────┐
                         │       PostgreSQL        │
                         └─────────────────────────┘
