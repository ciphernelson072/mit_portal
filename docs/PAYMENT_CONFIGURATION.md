# Payment Configuration

The finance page supports manual payment requests and Flutterwave Standard hosted checkout. Manual requests remain `PENDING` until finance staff reconcile and confirm them. Flutterwave payments are created and verified by the server; browser redirects and webhook payloads alone never mark a fee paid.

An imported bank statement entry automatically confirms a pending `BANK_TRANSFER` attempt only when both its provider reference and amount match exactly. Other channels, amount mismatches, failed attempts, and unknown references are not auto-confirmed and require review.

Refunds are recorded as `PENDING` requests; creating one does not represent a completed cash movement or reopen a fee balance. A different finance user must complete or reject the request. Completion requires a unique external bank/provider refund reference, updates the net paid balance, and is audit-logged. Repeating completion with the same reference is idempotent; reusing that reference for another refund is rejected. Apply `V39__harden_refund_review_workflow.sql` and `V40__prevent_duplicate_refund_references.sql` before using the reviewed refund endpoints. Finance should verify the actual external refund before marking it complete; the portal does not submit payouts to providers.

Fee responses include the original `amount`, net cash `amountPaid`, and outstanding `balance`. Net cash paid includes confirmed allocations and legacy completed transactions not yet linked to a payment attempt, minus completed refunds. Active fee waivers reduce the balance but are not counted as cash revenue. The legacy `paid` flag is not used to calculate financial totals.

The admin Finance module lists pending refunds. The API also exposes `GET /api/finance/refunds`, `POST /api/finance/refunds/{id}/complete` with `{"refundReference":"..."}`, and `POST /api/finance/refunds/{id}/reject` with `{"reason":"..."}`. The requester cannot approve their own refund.

Only an `ADMIN` may create, assign, or revoke role permissions. Do not delegate permission administration to users with only portal-settings access: permission assignment can grant finance and account-management capabilities.

## Flutterwave Standard

The portal starts a server-side payment at `https://api.flutterwave.com/v3/payments`, then redirects the student to the hosted `checkout.flutterwave.com` page. The Flutterwave account must support UGX and enable the desired checkout methods. The portal requests card and Uganda mobile-money options; Flutterwave account settings still control actual availability.

Configure the following in the private deployment environment. Leave both values blank to keep Flutterwave disabled:

| Variable | Purpose |
| --- | --- |
| `FLUTTERWAVE_SECRET_KEY` | Server-only Flutterwave test or live secret key. Never place in frontend code or logs. |
| `FLUTTERWAVE_WEBHOOK_SECRET_HASH` | Random webhook secret hash configured to exactly match the Flutterwave dashboard. |
| `FRONTEND_URL` | Public portal origin used for the hosted-checkout return URL. Production must use HTTPS. |

The application enables the Flutterwave destination only when the secret key, webhook hash, and a valid frontend URL are configured. Use test credentials and Flutterwave test transactions first. Move to live credentials only after verifying the redirect, webhook, and reconciliation flows.

Configure the webhook URL as:

`https://<portal-host>/api/public/finance/payments/flutterwave/webhook`

Set the same random secret hash in Flutterwave's webhook settings. The endpoint validates the `verif-hash` header using a constant-time comparison and then retrieves the transaction directly from Flutterwave. It checks the transaction ID, unique `tx_ref`, successful status, exact expected amount, and `UGX` currency before applying the payment. Unrecognized events/references do not alter fees. Enable webhook retries in the Flutterwave dashboard.

The browser return is only a prompt to verify. The portal calls Flutterwave's transaction-verification API from the server before updating the payment attempt. If a checkout expires while still pending, finance must close that attempt before a new checkout can be opened. Any verified charge arriving after an attempt has been closed is left for finance reconciliation.

Recommended Flutterwave setup sequence:

1. Create a Flutterwave merchant account and obtain test credentials from its API settings.
2. Configure `FLUTTERWAVE_SECRET_KEY` and a private random `FLUTTERWAVE_WEBHOOK_SECRET_HASH` on the server; do not commit these values.
3. Run the database migrations, including `V37__add_flutterwave_checkout_metadata.sql`.
4. Register the HTTPS webhook URL and matching secret hash in the Flutterwave dashboard; enable webhook retries.
5. Use a test student with a valid profile email, create a small UGX fee, and exercise success, failure, cancellation, duplicate webhook, and delayed webhook cases.
6. Confirm the student sees a receipt only after server verification. Reconcile Flutterwave settlement reports against portal allocations before switching to live mode.

Official references:

- [Flutterwave Standard](https://developer.flutterwave.com/docs/flutterwave-standard-1.md)
- [Transaction verification](https://developer.flutterwave.com/docs/transaction-verification.md)
- [Webhooks and signature validation](https://developer.flutterwave.com/docs/webhooks.md)
- [Security best practices](https://developer.flutterwave.com/docs/best-practices.md)
- [UGX payment methods](https://developer.flutterwave.com/docs/payment-methods.md)

## Other payment destinations

Configure settlement destinations through server environment variables, never browser code or committed credentials:

| Channel | Public destination variables |
| --- | --- |
| Bank transfer | `FINANCE_BANK_ACCOUNT_NAME`, `FINANCE_BANK_ACCOUNT_NUMBER`, `FINANCE_BANK_BRANCH` |
| PayPal | `FINANCE_PAYPAL_ACCOUNT` |
| Airtel Money | `FINANCE_AIRTEL_PAYEE`, `FINANCE_AIRTEL_MERCHANT_CODE` |
| MTN MoMo | `FINANCE_MTN_PAYEE`, `FINANCE_MTN_MERCHANT_CODE` |

Incomplete destinations are shown as unavailable, and the backend rejects attempts made against unavailable channels. The legacy direct Mastercard channel remains disabled. Never collect or store PAN, CVV, PIN, one-time codes, or provider passwords in this portal.

For production, keep `PAYMENT_WEBHOOK_SECRET` and Flutterwave values private, require HTTPS and secure auth cookies, reconcile provider settlement reports daily, and test a small-value transaction before announcing a channel. Public account identifiers are returned only to authenticated students; signing keys and credentials are never returned by the destinations endpoint.

Docker Compose requires `PAYMENT_WEBHOOK_SECRET` and refuses to start without it; there is no built-in signing-key fallback. Replace the placeholder in `.env.example` with a private random value before starting Compose. The `ops/start-local-dev.ps1` helper generates a separate random local-development value when it creates a new `.env`.