# Parity Audit — backend truth → mobile apps (2026-07-04)

**Mandate** (owner's instruction, autonomous run): the database / backend / portal / iPad
are the reference. Every field and endpoint exists for a reason; trace that reason and
make the **Android and iOS member apps** reflect it. Never fix in a narrow window —
every change re-checks the whole flow of its page (DB → API → DTO → UI). Small commits,
every finding logged here with its trace and resolution, branches pushed + PRs opened.

**Method**: two read-only auditors diffed (A) backend wire truth (openapi + module
services) vs Android DTOs/renderers, and (B) iOS API consumption vs Android (iOS is
living proof of contract usage). Findings are fixed in ranked order; each fix is one
commit referenced below.

---

## Findings ledger

Status: ☐ open · ◐ in progress · ✅ fixed (commit) · ✖ rejected (reason)

| # | Area | Trace (wire truth → client state) | Resolution |
|---|------|-----------------------------------|------------|
| 1 | Event detail end time | iOS shows "9:00 AM – 1:00 PM"; Android `EventDetail` has no end field → shows start only. Await auditor confirmation of `ends_at` on GET /events/{id}. | ☐ |
| 2 | Events "My RSVPs" | Android tab hardcodes 0; iOS computes quickRsvps. Trace per-occurrence RSVP source. | ☐ |
| 3 | Chat hub verse card | Android renders a STATIC Proverbs 27:9; `homeVerse(): TailoredVerse` endpoint exists. Wire it. | ☐ |
| 4 | Settings 2FA state | Android Settings seeds `twoFAon=false`; `me.profile.mfaEnabled` is on the wire. Thread it through. | ☐ |
| 5 | Profile field editing | iOS EditFieldSheet edits name/phone/dob/gender/country/city → backend endpoint exists; Android pencils are inert. Await endpoint identification. | ☐ |
| 6 | Prayer-wall reactions | Wire carries per-emoji `reactions[]`; iOS detail shows 5 quick reactions; Android only sends "pray". | ☐ |
| 7 | Chat prayer chip | `ChatMessage.aiTag == "prayer"` → iOS shows "🙏 I'm praying" chip; Android ignores `aiTag`. | ☐ |
| 8 | Radio extras | iOS radio uses reactions/chat/remind-me (verify endpoints); Android renders them inert. | ☐ |

(rows appended as auditors report)

---

## Commit log (small chunks, newest last)

- `…` — docs: open the parity audit ledger
