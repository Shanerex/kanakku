# Recurring expenses (F-09, row 11)

## Make an expense recurring

In the expense form's "More" disclosure: Switch "Repeat" reveals:
- Frequency chips: Monthly / Weekly / Every N days.
- Monthly: "on day [1–31]" (Select) with helper "If a month is shorter, it posts on the last
  day." Weekly: weekday chips. Every N days: Stepper.
- Ends: Never / On date (DateField) / After N times.
- Preview line: "Next: 1 Nov, 1 Dec, 1 Jan…" in the group's time zone (shown as "Asia/Kolkata"
  only when it differs from the device's).

Saving creates the rule and the first occurrence when its date is today or past.

## Rules list — `/groups/:id/settings/recurring`

Card per rule: CategoryBadge, description, Amount, "Monthly on the 1st · next 1 Nov", split
summary ("Equal, 3 people"), Switch "Active" (pause/resume), overflow: Edit rule, End rule.
Empty: "No recurring expenses. Rent and bills can add themselves." + "Add a recurring
expense".

## Upcoming occurrence actions

Rule detail lists the next 3 occurrences with per-row "Skip" and "Edit this one" (edits only
that occurrence before it posts). Skipped rows show struck-through with "Skipped by Priya ·
Undo skip". Past occurrences link to their posted expense.

Posted occurrences appear in the expense list with the Repeat icon and notify members
("Rent, Nov was added · you owe ₹12,000.00").
