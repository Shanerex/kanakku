# Statement import and settle-up detection (F-22, F-23; rows 24–26) — outline

Personal, not per group: statements belong to the user. Entry: Me → Statements, and a
"Find payments in your statement" link on the settle-up screen.

## Upload — `/me/statements/new`

1. **File**: FileDrop for CSV, XLS, XLSX or PDF. Supported banks listed as small chips (SBI,
   HDFC Bank, ICICI Bank, Axis Bank, Kotak) with "Other banks: we'll try to read the columns".
2. **Password** (only when the PDF is encrypted, detected after upload): SecretField
   "Statement password" with the fixed helper "Used once to open this file. Never saved." and
   a hint "Often your date of birth or customer ID; check your bank's email." Wrong password:
   "That password didn't open the file." (3 tries, then re-upload).
3. **Columns** (unknown layout only): a preview table of the first 10 rows with a Select above
   each column (Date, Description, Amount, Debit, Credit, Balance, Reference, Ignore), AI
   preselected and draft-styled; the user confirms.
4. **Reading**: job progress; "We delete the file as soon as it's read."

## Transactions — `/me/statements/:id`

Header: bank, account last 4 digits, date range, "Rows are deleted on 20 Nov" (30 days).
Table/list: date, counterparty (name or UPI ID), amount with direction (in `positive-text` ↓ /
out `text` ↑ — debits aren't bad, so no coral), UTR in mono. Duplicates of an earlier import
show "Already imported" muted.

## Matches (row 26)

A Card at top: "Found 4 payments that look like settle-ups" → list of draft settlements with
a confidence pill (High / Medium / Low as filled dots, with words), the evidence (statement
row SourceQuote) and the open debt it matches ("Arun owed you ₹1,240.00 in Goa Trip").
Confirm / Not a match. Payments both sides imported show once ("Arun imported this too").

Delete-now control: "Delete these rows now" (ConfirmDialog) for users who don't want to wait
30 days.
