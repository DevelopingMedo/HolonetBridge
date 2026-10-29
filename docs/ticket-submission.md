# Ticket submission language

Before every issue POST, the guild's retained authenticated Gitea client reads `/api/v1/user`. Generated bug headings and recurrence labels use that token account's current language, independently of Discord server/user/session language. Supported languages are `en`, `de`, `hr`, and `ru`; valid regional tags map to their base language. User-provided ticket text is not translated.

A missing, null, empty, malformed, wrong-type, or unsupported account language falls back explicitly to English. A failed account lookup (including redirects, transport errors, invalid JSON, or an invalid user response) prevents the POST and produces the existing localized failure reply, without exposing raw provider responses or exception details. There is no language cache or retry; changing the account language affects the next ticket. The existing `read:user` permission used by the authentication healthcheck is required.

Private replies and dialogs remain in the current Discord user's language. Resuming a session refreshes that user locale without changing draft/session IDs or contents; it does not select ticket labels or close the session.

Regression tests cover conflicting account/Discord/session languages, regional tags, English fallback, fresh language changes on the same provider, lookup failures without a POST, localized private outcomes, and persistent session locale refresh. `GiteaTicketProviderIntegrationTest` additionally changes account language only in a disposable Gitea Testcontainer; it is skipped when Docker is unavailable.
