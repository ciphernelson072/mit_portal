# Privacy, Retention, Hosting, and Recovery Gates

## Privacy launch approval

The public privacy notice identifies portal data categories and applicants'
rights/contact gaps, but it is not institutional policy until an authorized
owner approves it. Before real data is used:

1. Publish approved purposes, legal basis, data subjects, recipients, retention,
   access/correction, deletion, and complaint processes.
2. Name a privacy officer or institutional contact and publish the support
   address or approved escalation route.
3. Define retention and legal-hold periods for applications, academic records,
   attendance, finance records, uploaded documents, audit logs, email logs,
   sessions, and backups.
4. Define when account deactivation differs from deletion and require correction
   or archival rules for records that cannot be erased without breaking legal
   accounting or academic obligations.
5. Complete a data inventory covering third parties, backups, exports, support
   copies, application logs, error reports, and monitoring systems.
6. Obtain required notices and consent for the institute's jurisdiction and
   user population. Avoid collecting document/card/payment data through the
   public admissions form.

## Hosting acceptance

The Render blueprint configures persistent uploads, HTTPS operation assumptions,
and environment-secret placeholders, but launch approval still requires:

- TLS certificate and custom-domain verification;
- database and upload persistence across restarts and redeployments;
- public health checks and private operational endpoints;
- secret access controls and rotation procedures;
- limits and monitoring for CPU, memory, storage, database connections, and
  provider dependencies;
- tested HTTPS behavior, forwarded headers, cookies, CORS, and logout;
- approved backup/restore ownership and service-provider retention.

Use a production-like staging environment before inviting real users. One
single-instance persistent disk is not shared-storage proof and is not
equivalent to multi-instance shared object storage.

## Recovery and rollback drill

Before each production release, record the application artifact, migration
version, database/geometry backup, and upload backup checksums. Exercise:

1. application-only rollback when the prior artifact is schema-compatible;
2. database-and-upload restore when it is not;
3. forward migration recovery and data-restoration limitations;
4. the target deployment's measured recovery point objective and recovery time
   objective;
5. downtime/communications and write-freeze instructions;
6. rollback owner, database owner, privacy owner, and release approver contacts.

Do not restore a backup over a live database while accepting users unless the
declared RPO and data-loss window have been approved. Rollback is not complete
until application health, critical workflows, audit history, uploads, and
database connectivity pass in the target environment.
