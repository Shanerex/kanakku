tags: [identity, groups, phone, privacy]
Decision: user search is POST with a body, and phone numbers leave identity only masked, except the owner's own profile.
Why: digits must not sit in URLs or logs, and masked output limits what a search can leak.
Consequence: GET user-search is removed; the web client moved to POST in the same change.
