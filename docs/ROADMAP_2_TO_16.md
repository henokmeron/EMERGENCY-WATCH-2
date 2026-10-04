# Platform Hardening Roadmap — Items 2–16

This records the requested fixes without renaming the repository.

2. Monitoring platform boundary: device acquisition stays here; patient intelligence/operations stays in the Lovable repository.
3. Core monitoring architecture: ingestion -> signal quality -> normalization -> baseline -> event engine -> verification -> priority -> operator -> escalation -> audit.
4. Signal-quality engine: distinguish invalid/noisy/missing/stale data from physiological abnormality.
5. Personal baseline: support patient-specific baselines and trends; do not substitute baseline for missing live telemetry.
6. Multimodal event engine: combine available signals and persistence rather than single thresholds alone.
7. Event verification: retain evidence/reasons and allow operator review/cancellation.
8. Patient timeline: preserve ordered raw/normalized readings and event/action timestamps.
9. Event replay: define a deterministic replay path for historical data.
10. Algorithm versioning: every generated event records engine/rules version.
11. Device capability matrix: capability metadata is provider/model-specific.
12. Corsano integration: use official API/SDK contracts only; no invented endpoints or payloads.
13. Regulatory boundary: separate device certification from the user's software claims and clinical workflow.
14. Security: least privilege, auditability, secrets separation, retention and incident response.
15. Demo/test/prod separation: simulator data must be explicitly labelled and never masquerade as live telemetry.
16. Clinical/evidence/commercial readiness: measure sensitivity, specificity, false alerts, missed events, detection latency, escalation latency, uptime and data completeness before making safety claims.

Status: this branch adds the shared contracts, documentation, database foundation and software interfaces needed to implement these areas safely. External items requiring real hardware, Corsano credentials, clinical data, regulatory review or production infrastructure cannot be truthfully completed in source code alone.
