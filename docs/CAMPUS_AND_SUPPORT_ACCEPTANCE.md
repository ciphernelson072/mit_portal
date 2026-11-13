# Campus and Support Acceptance

## Supported environments

The automated browser evidence covers Chromium and an isolated Brave run. It
does not establish acceptance in Firefox, Safari/WebKit, older browser builds,
institution-managed browsers, assistive technologies, or low-bandwidth campus
networks.

Before launch, publish and test:

- minimum supported desktop/mobile browser versions;
- keyboard-only navigation and visible focus;
- labels, names, landmarks, contrast, zoom/reflow, dialog behavior, and screen
  reader announcements for critical flows;
- mobile navigation, file upload/download, print/export, and session timeout;
- institution-managed antivirus/proxy/firewall and captive-network behavior;
- offline/service-worker behavior and cache invalidation.

Critical flows include login/MFA/password change, admissions, grades/results,
finance/payment handoff, file upload, messages, and account recovery.

## Campus user acceptance

Run the synthetic-data matrix in `REMAINING_WORK_AND_DEPLOYMENT.md` with named
campus users from each role. Capture pass/fail, role, browser/device, network,
time, and sanitized defect references. Stop the pilot for cross-user exposure,
unauthorized role access, data loss, secret leakage, insecure transport, failed
restore, or unexpected external provider activity.

Record explicit acceptance for:

- student records and ownership boundaries;
- assigned-class and grade boundaries;
- administrator setup, exports, and audit review;
- admissions and account provisioning;
- email and payment test behavior;
- upload persistence and restore;
- session/logout behavior after timeout and credential revocation;
- support escalation and access recovery.

## Support ownership and automation

Name operational owners for identity/MFA, academic records, finance, email,
hosting/database, backup/restore, security incidents, privacy requests, and
provider escalations. Publish on-call hours, escalation targets, ticket fields,
severities, and expected response.

Automated deployment remains a decision gate until the release owner approves:

- protected branches and reviewed pull requests;
- signed or trusted build provenance;
- migration and rollback order;
- environment-specific secrets and health gates;
- database backup before migration;
- smoke tests after deployment;
- rollback and notification ownership.
