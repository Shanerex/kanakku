# Chat to ledger: WhatsApp import (F-19, row 21) — outline

Entry: Add sheet → "Import chat", or Group ⋯ → Import → WhatsApp chat.

Wizard steps: **Add chat · Pick dates · Find expenses · Review**.

1. **Add chat**: FileDrop for the "Export chat (without media)" `.txt` or `.zip`, or a
   "Paste text instead" tab with a TextArea. How-to disclosure with Android and iOS steps.
   Privacy note with `LockKey`: "We read the chat to find expenses, then delete it. Drafts keep
   only the lines they quote."
2. **Pick dates**: detected range shown ("2 Oct – 19 Oct · 1,248 messages · 6 people"), a date
   range picker defaulting to the full range, and **people mapping**: each chat sender →
   group member (MemberPicker, auto-matched by name/phone label), or "Not in this group".
3. **Find expenses**: job progress ("Reading messages… 640 of 1,248"); can be left, with a
   notification on finish.
4. **Review**: the drafts queue filtered to this import: "Found 23 possible expenses". Each
   DraftCard's SourceQuote shows the cited messages (sender, time, text) in a chat-bubble
   style (plain text, never rendered links). Possible duplicates carry a `draft` chip
   "Might already be in the group: *Cab to Baga* on 14 Oct" with "Not a duplicate" / "Discard".
   Bulk select and "Confirm 18".

Errors: unknown format → "We couldn't read this file. Try pasting the text instead." with the
first unreadable line number.
