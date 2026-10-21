# Payment Configuration

The finance page supports student-submitted payment requests. A request remains `PENDING`; it does not mark a fee paid. Finance staff must verify the provider transaction and confirm the attempt. Provider callbacks must use the signed webhook endpoint.

An imported bank statement entry automatically confirms a pending `BANK_TRANSFER` attempt only when both its provider reference and amount match exactly. Other channels, amount mismatches, failed attempts, and unknown references are not auto-confirmed and require review.

Configure settlement destinations through server environment variables, never browser code or committed credentials:

| Channel | Public destination variables |
| --- | --- |
| Bank transfer | `FINANCE_BANK_ACCOUNT_NAME`, `FINANCE_BANK_ACCOUNT_NUMBER`, `FINANCE_BANK_BRANCH` |
| PayPal | `FINANCE_PAYPAL_ACCOUNT` |
| Airtel Money | `FINANCE_AIRTEL_PAYEE`, `FINANCE_AIRTEL_MERCHANT_CODE` |
| MTN MoMo | `FINANCE_MTN_PAYEE`, `FINANCE_MTN_MERCHANT_CODE` |

Incomplete destinations are shown as unavailable, and the backend rejects attempts made against unavailable channels. Mastercard is deliberately unavailable until a hosted gateway integration exists. Add provider-side checkout-session creation, signed callback verification, idempotency, and reconciliation before enabling card payments. Never collect or store PAN, CVV, PIN, or provider passwords in this portal.

For production, keep `PAYMENT_WEBHOOK_SECRET` private, require HTTPS and secure auth cookies, restrict webhook access as appropriate, reconcile provider settlement reports daily, and test a small-value transaction before announcing a channel. Public account identifiers are returned only to authenticated students; signing keys and credentials are never returned by the destinations endpoint.

Docker Compose requires `PAYMENT_WEBHOOK_SECRET` and refuses to start without it; there is no built-in signing-key fallback. Replace the placeholder in `.env.example` with a private random value before starting Compose. The `ops/start-local-dev.ps1` helper generates a separate random local-development value when it creates a new `.env`.