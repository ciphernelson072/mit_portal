# Integration Status and Remaining Work

Last reviewed: 21 October 2026

This inventory compares the portal UI, Spring API/services, and external
systems. “Implemented” means the code path exists; it does not mean a live
provider or production environment has been configured or accepted. Integrations
that depend on institutional scope are explicitly marked as decisions rather
than assumed requirements.

## Confirmed integration work

| Integration | Frontend / caller | Backend / provider side | Status and work required |
|---|---|---|---|
| Email transport | New applications receive a receipt email when delivery is configured; admission enrollment emails the generated username and temporary password, with a private admin handoff only if delivery is unavailable. | `EmailService` supports SMTP and Resend HTTPS API. Local Gmail SMTP is configured and has previously passed an SMTP acceptance test; production delivery still requires an institution-approved sender/domain, SPF/DKIM/DMARC, and delivery monitoring. | **Local admission-email path is implemented and SMTP configured.** Keep secrets private, confirm institution approval for the sender, and complete production delivery/bounce monitoring. Hiring email, bounces, and delivery monitoring remain. |
| Admission and hiring email | Applicants receive a confirmation and application number immediately when mail delivery is available. After an administrator accepts and enrolls them, they receive their username and a generated one-time temporary password; if enrollment email is unavailable, the admin gets a private credential handoff. | Newly admitted student accounts are forced to change their temporary password before accessing student services. Hiring notification remains unwired. | **Admission email path is implemented.** Verify end-to-end inbox delivery in the deployment environment; hiring account handoff still needs its own secure setup flow. |
| Grade, CGPA, and announcement email | Grade and announcement screens are connected to portal APIs; no verified email preference/trigger path was found for these event types. | `sendGradeNotification`, `sendCGPAUpdateNotification`, and `sendAnnouncementNotification` exist but have no application call sites. | **Unwired event notifications.** Decide which are required, add user preferences/consent and event triggers, and test duplicate delivery, failure, and retry behavior. In-app notification behavior remains separate. |
| Payment provider processing | Finance UI lists channels and submits a payment attempt/reference. | Payment-attempt creation/review, bank reconciliation, and one generic signed callback endpoint exist. The callback accepts only a provider reference and status; its HMAC is over `reference.status`. There are no provider SDK/client adapters in the application. | **Partially wired; live provider processing is not established.** Choose providers and implement their checkout/session creation, provider-specific signature verification over the provider's documented payload, amount/currency/reference binding, idempotency/replay controls, event persistence, reconciliation, refunds, and sandbox/production tests. Do not represent destination/account configuration as proof of real online payment. |
| Bank transfer reconciliation | Student submits a reference; authorized staff can review or import reconciliation entries. | `ReconciliationService` can match imported references and exact amounts against pending bank-transfer attempts. | **Manual data path exists; bank statement source is not integrated.** Decide whether staff upload/import is the supported operating procedure or implement an authenticated bank statement feed/API with duplicate, discrepancy, and audit handling. |
| PayPal, Airtel Money, and MTN MoMo | The UI consumes channel availability from the destinations endpoint. | Availability is currently derived from configured account/payee/merchant text. No channel-specific provider client or callback adapter was found. | **Configuration may overstate capability.** Keep a channel unavailable until its complete provider flow and callbacks are connected and tested. Validate the production destination and provider account with finance. |
| Mastercard/card payments | UI presents the channel as unavailable and explicitly warns against entering card data in the portal. | Destination service reports Mastercard unavailable; no hosted card provider integration was found. | **Safely disabled, not implemented.** Only enable after selecting a PCI-compliant hosted checkout provider and verifying signed callbacks, refunds, reconciliation, and no card data reaches portal servers/logs. |
| Prometheus metrics | No monitoring client configuration is part of the portal UI. | The Spring-managed `micrometer-registry-prometheus` dependency is present and `/actuator/prometheus` is exposed. The endpoint remains behind authentication; isolated browser acceptance verified anonymous requests are rejected and an authenticated administrator receives metrics (14 Oct 2026). | **Application endpoint wired; external monitoring remains.** Configure scraping from an approved monitoring network, dashboards, central logs/error reporting, and tested alert delivery in the selected deployment. |
| Authenticator MFA | Login UI accepts an OTP challenge and profile security UI calls MFA status/setup/verify/disable APIs. | TOTP endpoints, secret encryption, and enforcement are implemented, but MFA is disabled by default and needs a 32-byte Base64 encryption key when enabled. | **Application flow exists; rollout is conditional.** Decide required roles/policy, provision and back up the encryption key securely, test enrollment/login/disable/session revocation and recovery procedures, and update the stale login-script comment that still says MFA is not implemented. Do not enable MFA without tested recovery/support procedures. |
| Central logs and error reporting | No application-side error-reporting integration was found. | Logging currently uses application/JUL logging; no log-forwarding, OpenTelemetry, or error-tracking SDK/exporter was found. | **Deployment integration missing.** Select the approved central logging/error-reporting platform, forward structured logs, set retention/access controls, redact credentials/tokens/PII, and exercise error and alert paths. |
| Database and upload backups | No operator-facing backup destination setup is part of the application workflow. | Backup/restore scripts and local status-marker configuration exist. | **Scripts exist; production scheduling and off-site storage are not integrated.** Choose encrypted off-host storage, schedule both database and upload backups, alert on stale/missing backups, set retention/access policies, and rehearse coordinated restore. |
| File storage | UI uploads and downloads through portal endpoints. | Application file service writes to the configured filesystem upload directory. No S3/object-storage client or shared-storage adapter was found. | **Local filesystem implementation only.** For a single host, provision persistent storage and test permissions/capacity/backups. For multiple instances, implement shared object storage or a supported shared filesystem, including private access, lifecycle/retention, malware scanning if required, and restore tests. |
| Rate limiting | Login and public admissions requests reach the application filter. | `RateLimitFilter` stores buckets in process memory. | **Single-instance only.** Either formally keep one instance behind the approved ingress or add a shared store/gateway policy and verify consistent limits across instances. Include proxy trust and spoofed-forwarded-header tests. |
| Browser UI to backend/database | Browser flows call portal APIs; the server-side feature endpoints and services are present for tested workflows. | On 21 Oct 2026, the isolated Playwright suite passed 79 tests against disposable H2/sample data and the current temporary TiDB-ready local run; the live deployment still has no accepted authenticated user workflow against production test records. Other browser tests use API fixtures, and the MySQL/Testcontainers profile has not been verified on this Windows host. | **Partial coverage.** Test the live instance with synthetic student, teacher, and administrator accounts. Add full-stack contract/workflow tests against disposable MySQL/TiDB for admissions-to-account provisioning, payment attempt/review, report approval/download, file upload/download, authorization/ownership, and notification delivery. Keep mocked UI tests as complementary tests. |
| CI/CD deployment | GitHub Actions runs verification; no deployment environment is configured by this repository. | CI builds/tests the artifact; no release/deploy workflow or infrastructure provisioning integration was found. | **Build pipeline present; deployment automation absent.** If automated delivery is required, define the target platform, protected environments/approvals, artifact provenance, secret injection, migration gate, health verification, and rollback/roll-forward procedure. Otherwise document the manual release owner and runbook. |

## Optional integrations: decide whether they are in launch scope

These are not automatically blockers if product owners confirm they are not
required. If required, they need an explicit integration, security review,
failure handling, and end-to-end acceptance.

| Integration | Current evidence | If required |
|---|---|---|
| External identity / campus SSO | No OIDC, SAML, LDAP, or external identity-provider client/dependency was found; portal-managed authentication is implemented. | Select the campus identity provider, define account linking and role/attribute mapping, provisioning/deprovisioning, MFA ownership, and logout/session behavior; test with institutional accounts. |
| SMS delivery | No SMS provider SDK/client or SMS configuration was found. | Select an approved provider and use only for explicitly approved notifications or recovery; implement consent, delivery status/retries, abuse controls, and cost monitoring. Do not treat SMS as a substitute for authenticator-based MFA without a risk decision. |
| Generative AI assistant | The campus-support assistant deliberately returns deterministic, rule-based responses and does not call an external AI service. | If AI responses are a product requirement, select a provider, define privacy/data-retention boundaries, prevent student records from being sent without authorization, add prompt/output safety controls, cost/rate limits, audit policy, and fallback/support escalation. |
| LMS, HR, finance, or external student-information systems | No general-purpose outbound/inbound integration client was found beyond the portal's own APIs and manual bank reconciliation. | Inventory actual source-of-truth systems and required data flows first. For each approved system define ownership, identifiers, field mapping, consent/legal basis, synchronization direction/cadence, conflict handling, retries, audit, and reconciliation before implementation. |
| CAPTCHA / bot-management service | The current login implementation has no CAPTCHA integration; old fake CAPTCHA behavior was removed. | Decide whether public admissions/login need bot controls. Prefer edge WAF/bot management or a real provider with server-side token verification and accessibility/failure behavior; never restore a client-only fake challenge. |

## Release evidence required for every enabled integration

For each integration selected for launch, record:

1. Business owner, technical owner, provider/environment, and support contact.
2. Production endpoint/account and secret-manager references (never secret
   values) plus least-privilege access and rotation procedure.
3. Success, timeout, invalid-authentication, duplicate/replay, provider outage,
   and recovery behavior.
4. Security/privacy review, data retention, audit/log redaction, and access
   restrictions.
5. Staging test evidence using the provider sandbox or a representative test
   system, plus production smoke-test evidence that does not expose private
   student data.
6. Monitoring, alert ownership, retry/reconciliation procedure, and
   decommission/credential-revocation procedure.

Do not count a configured property, a UI label, a mocked browser response, or a
successful local build as proof that an external integration is operational.
Use this inventory together with the [release checklist](./RELEASE_CHECKLIST.md)
and [deployment guide](./DEPLOYMENT_GUIDE.md).
