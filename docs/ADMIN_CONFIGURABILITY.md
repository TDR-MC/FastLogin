# Admin configurability

This registry separates runtime behavior from future operator-facing controls. It does not expose secrets or allow an
admin surface to weaken authentication policy.

| Surface | Runtime/config source of truth | Mutable fields and defaults | Validation and permissions | Admin UI readiness |
|---|---|---|---|---|
| Premium-name lookup uncertainty | `JoinManagement` enforces fail-closed behavior when `autoRegister: true`; `messages.yml` owns `premium-name-check-unavailable` | Localized disconnect copy; default: `Paid-account name verification is temporarily unavailable. Please try again shortly.` | Message editing may be delegated to trusted operators and should require non-blank text plus preview/audit. The fail-closed policy, resolver result and authentication state are system-owned security values and must not be editable from product admin. | Runtime/config: ready. Admin UI: not implemented. |
| Reserved premium-name preflight | `config.yml` owns `premiumNamePreflight.*`; `messages.yml` owns `premium-name-preflight` | Enabled flag (`false`), permit TTL (`30` seconds), maximum pending permits (`2000`), localized copy | Trusted-server-operator only. Existing hard maxima remain enforced. Preview/audit is required before a future publish action. | Runtime/config: ready. Admin UI: not implemented. |
| Cracked fallback after failed premium login | `config.yml` owns `secondAttemptCracked` | Enabled flag (`false`) | Security-owned exclusion: TDR keeps this disabled. A future product admin must not expose a control that permits offline fallback for protected paid-account names. | Runtime/config: ready. Admin UI: intentionally excluded. |
| Anti-bot overflow authentication gate | `config.yml` owns `anti-bot.*`; TDR default is `action: block` | Connection count (`600`), expiry (`10` minutes); overflow action is fixed to `block` in production | Limits may become trusted-operator controls with positive bounded validation. Overflow behavior is a system-owned security exclusion because `ignore` bypasses FastLogin authentication handling. | Runtime/config: ready. Admin UI: intentionally excludes overflow action. |
