# Student Portal — System Analysis Presentation Pack

**Prepared:** 20 October 2026  
**Audience:** project presentation, system analysis, and design review  
**Scope:** current portal code and configured pilot architecture; diagrams are
deliberately bounded views, not an exhaustive inventory of every feature or
database table.

## Start here

- `Student_Portal_System_Analysis_With_Dashboard_Screenshots.pptx` —
  presentation deck with dashboard screenshots.
- `Student_Portal_System_Analysis_With_Dashboard_Screenshots.docx` — editable
  written companion with diagram and dashboard references.
- `diagrams\` — high-resolution PNGs for inserting into other documents.
- `screenshots\` — current student, teacher, and administrator dashboard
  layouts captured with clearly labeled illustrative mock data; these are not
  authenticated captures or live account data.
- `sources\` — editable SVG, Mermaid source, and local regeneration scripts.

The diagrams were checked against the Spring/JPA entities, Flyway/schema SQL,
security rules, portal pages, and deployment runbook. Relationships are shown
as database foreign keys only where the mappings or migrations enforce them.
For example, payment attempts reference fees and students, and reconciliation
entries reference payment attempts; admission approval-to-student provisioning
is a workflow and is not drawn as a direct foreign key. The diagrams show only
selected fields and workflows to remain readable in a presentation.

## Recommended presentation

Use the deck in this order:

1. State the project purpose, users, and system boundary.
2. Explain the architecture and trust boundaries.
3. Show the context and level-1 data flows.
4. Walk through the core academic ERD, then admissions/finance.
5. Explain the role use cases and one end-to-end sequence.
6. End with the design decisions, limitations, and next steps.

Use the Word companion as speaker notes and as a handout. Keep the live demo
separate: present synthetic records only and confirm the test environment is
available before the session.

## Do not include in a presentation or shared folder

- `.env`, `.env.cloudflare-tidb`, database dumps, backup archives, private
  application logs, or any credentials, tokens, signing keys, MFA keys, or
  provider secrets.
- Real student, staff, applicant, payment, or contact data; use synthetic
  screenshots and examples.
- The temporary Cloudflare Quick Tunnel URL as a permanent service address.
  It can change whenever the tunnel restarts.
- Demo usernames/passwords, even if they appear in local development
  documentation.
- Internal network addresses, database hostnames, or file paths that are not
  needed to explain the design.
- A claim that payments, email, backups, monitoring, MFA, or other external
  integrations are production-ready solely because a screen or configuration
  option exists.
- Treat workflow links (such as admission approval to account provisioning) as
  database foreign keys when no database constraint exists.
- A diagram presented as exhaustive: the project has many more entities and
  workflows than the selected views here.

## Do not forget

- Identify the diagram level, actor, and system boundary while presenting.
- Explain role-based access and that the backend, not the browser, is the
  authorization boundary.
- Mention the HttpOnly authentication cookie and HTTPS at the deployment edge;
  do not show or reveal a token.
- Explain the file store as private server-side storage, not a public file
  share.
- Label the Cloudflare Quick Tunnel as a temporary testing arrangement, not
  production architecture.
- Include the database engine/version and migration version only when verified
  for the specific environment being presented.
- Separate implemented application paths from provider connections and
  operational controls still awaiting acceptance.
- Use the accompanying current integration/release checklist to qualify
  readiness statements.
- Keep a copy of source diagrams and final Office files together, and make a
  dated backup before editing the presentation for another audience.

## Current code-informed limitations to call out

- The public test tunnel is temporary and can receive a new address after
  restart.
- Live smoke checks do not establish authenticated acceptance of every
  student, teacher, and administrator workflow.
- Some provider and institutional integrations remain partial, optional, or
  unconfigured; consult `docs/INTEGRATION_GAPS.md` in the project for current
  status.
- The diagrams intentionally omit many supporting domains (for example
  library, hostel, research, HR, procurement, scholarships, and campus events).
- Verify the active environment and status before quoting a runtime/schema
  version; pilot and local test configurations are different.

## Regeneration

Run from the project root with Node.js, installed Playwright, Java 17+, and
Maven dependencies already available:

```powershell
node .\docs\PORTAL_SYSTEM_ANALYSIS_2026-10-20\sources\capture-dashboards.js
node .\docs\PORTAL_SYSTEM_ANALYSIS_2026-10-20\sources\render-diagrams.js
node .\docs\PORTAL_SYSTEM_ANALYSIS_2026-10-20\sources\render-office.js
```

`render-office.js` uses the Apache POI dependency already declared by the
project; the script compiles a temporary Java helper and leaves the final
PowerPoint and Word documents in this pack. `capture-dashboards.js` uses the
current dashboard HTML and CSS while disabling application scripts and inserting
synthetic examples; it does not log in or call the portal API. The teacher
snapshot uses a capture-only section sizing adjustment so its summary and
workspace appear together. No application source or database is changed by
these presentation scripts.

The original diagram-only Office files are retained if they are already open in
an Office application; the updated versions use the filenames listed above.
