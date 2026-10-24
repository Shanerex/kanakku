# Import and export (F-12, row 14)

Entry: Group settings → Import and export; and New group → "Import from Splitwise" link.

## Splitwise import wizard

ProgressSteps: **Upload · Match people · Review · Done**.

1. **Upload**: FileDrop for the Splitwise group CSV with a short how-to ("In Splitwise, open
   the group → Settings → Export as spreadsheet"). Parse errors show the line number in words.
2. **Match people**: for every name column in the CSV, a row "Splitwise name → Kanakku member"
   with a MemberPicker defaulting to a name match, or "New guest" (default when no match).
   Can't continue until each is mapped.
3. **Review**: counts ("142 expenses, 18 payments, 12 Jan – 3 Mar"), a preview list of the
   first 20, and the balance check: a table of each person's balance from the CSV next to
   Kanakku's computed balance, with a check mark per row. Any mismatch blocks import and shows
   the difference in paise.
4. **Done**: celebration, "Imported 160 entries" and "Go to group". The import is one activity
   item that can be undone as a whole within 24 h.

Long imports run as a job: the wizard shows a progress bar and can be left; a notification
says when it finishes.

## Export

Menu under Group ⋯ → Export: **CSV** (spreadsheet of expenses), **JSON** (full fidelity, for
moving to another Kanakku), **PDF trip report** (every expense and the settle-up plan). Each
starts a download; PDF runs as a job with a toast "Preparing your report…" then "Download".

Importing a Kanakku JSON: New group → "Import from a Kanakku export" uses the same wizard with
people matching skipped (members come from the file as guests).
