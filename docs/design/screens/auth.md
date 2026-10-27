# Auth screens (F-02, rows 2–3)

Signed-out screens share one layout: a centred 400 px card on `bg` with a playful
illustration band behind it (three soft identity-coloured blobs, `aria-hidden`). On phones the
card is full width with 16 px gutters and the blobs sit above it. Wordmark "kanakku" (Baloo 2
800, `primary`) and the tagline "Split trips, flats and dinners. Settle over UPI." at top.

Mock: [web-sign-in](../mocks/web-sign-in.html).

## Sign in — `/sign-in` · mobile: SignIn

Content, top to bottom:
1. h1 "Welcome back".
2. Provider buttons (row 3; render only those the admin enabled; **v1 password-only: none are
   shown, and neither is the divider below**): "Continue with Google",
   "Continue with Microsoft", "Continue with Apple", then any custom OIDC providers by their
   configured name. Secondary buttons, full width, provider logo left (official marks, sized
   per each provider's brand guide; Apple's button is black in light mode and white in dark).
3. Divider "or" (hidden while no provider is enabled).
4. Email, Password (with show/hide IconButton), "Forgot password?" ghost link right-aligned
   (**v1: hidden**, there is no password reset yet).
5. Primary lg button "Sign in".
6. "New here? Create an account" link.

States:
- Wrong credentials: InlineProblem above the fields, "That email and password don't match."
  (never say which one is wrong).
- Unverified: "Verify your email first. We sent a link to p•••@gmail.com." + "Resend link"
  ghost button (cooldown 60 s, shown as "Resend in 42 s"). **v1: not shown**; password
  sign-ups are recorded as unverified but are not blocked (accounts-password requirements).
- Rate limited (5 fails / 10 min): "Too many tries. Try again in 8 minutes." and the button
  disables with a live countdown.
- OIDC error on return: "Couldn't sign you in with Google. Try again." (no token detail).

API: `POST /api/v1/auth/sign-in` (session cookie + CSRF token); OIDC via
`/oauth2/authorization/{provider}`. After sign-in, go to the `returnTo` route or Home (v1: Home
isn't built yet, so the fallback is `/me`; only same-site paths are accepted as `returnTo`).

A11y: provider buttons are real buttons with text; password toggle has "Show password" label
and `aria-pressed`; errors are announced via `role="alert"`.

## Sign up — `/sign-up`

Display name, Email, Password with a strength meter (3 segments; rule text: "8 to 128
characters. A passphrase works well."), checkbox-free (no marketing consent). Primary "Create
account". Provider buttons above as on sign in (v1: none). On success → the account is created,
the person is signed in, and they land on [/welcome](#first-run-profile--welcome--mobile-welcome)
(v1: no Verify email step).

### Mobile number at sign-up (F-27)

Indians find friends by phone, so the form asks for one. `GET /api/v1/auth/sign-up-options`
says whether the server can text codes (`phoneVerification`); the form waits for it before it
draws the phone field.

- **Field**: "Mobile number" under Email, `type="tel"`, `autocomplete="tel"`. Typed without a
  country code it is an Indian (+91) number; the helper says so: "Starts with +91 unless you
  type another country code."
- **With codes on** (provider set): the field is required and the helper adds "We'll text you
  a code to confirm it." "Create account" sends the form and swaps the card for the code step;
  no account exists until the code is confirmed.
- **With codes off** (no provider): the label reads "Mobile number (optional)" with the helper
  "Friends can find you by it. It stays unverified on this server." "Create account" works as
  before and may be sent with the field empty.
- Field errors: empty ("Enter your mobile number.", codes on only), not a number ("Enter a
  mobile number like 98765 43210."), verified on another account ("That number already
  belongs to another account. Sign in, or use a different number.").

### Code step (`PhoneCodeStep`, same route)

The card keeps its place and changes its content; the wordmark stays, the form fields go.

1. h1 "Enter your code". Body "We texted a 6-digit code to +91 ••••• ••10." (the masked number
   from the response, never the typed one).
2. One field "6-digit code": `inputmode="numeric"`, `autocomplete="one-time-code"`, Baloo 2
   `h1` size, wide letter spacing, centred, tabular numerals; digits only, 6 max. Helper "It
   works for 10 minutes."
3. Primary lg "Create account" (disabled until 6 digits).
4. Ghost "Resend code" with a 30 s cooldown shown as "Resend in 24 s". Ghost "Use a different
   number" returns to the form with everything kept.

States:
- Wrong code: field error "That code isn't right. Check the text and try again." (focus stays
  in the field, digits selected).
- Locked (5 wrong codes): InlineProblem "Too many wrong codes. Try again in 12 minutes."; the
  button disables.
- Expired: "That code has expired. Send a new one." with "Resend code" focused.
- Sent but not delivered, the provider failed (`smsSent` false): InlineProblem "We couldn't
  send the text. Try again, or finish now and verify your number later." and a secondary
  "Finish without verifying" button, shown only in this state. A later successful resend
  removes it.
- Sign-up gone (410 after an hour idle): "This sign-up timed out. Start again." and the form
  returns.
- Too many texts (429): "You asked for too many codes. Try again in 40 minutes."

After success the person is signed in as before and lands on `/welcome`.

API: `POST /auth/sign-up` (202 pending) → `POST /auth/sign-ups/{id}/verify` (a fresh
Idempotency-Key for every try: a wrong answer is stored under its key), `/resend`,
`/finish-unverified`.

## Verify email — `/verify-email`

> v1: not built. Email verification is out of scope for accounts-password; this screen returns
> with a later change.

Illustration: an envelope blob with a sparkle. h1 "Check your inbox". Body "We sent a link to
priya@example.com. It expires in 24 hours." Buttons: "Resend link" (secondary, cooldown),
"Use a different email" (ghost). The link in the email lands on `/verify-email?token=…`, which
shows a spinner, then "Email verified" with a bounce check and continues to first-run profile.
Expired token: "This link has expired" + Resend.

## Forgot / reset password — `/forgot-password`, `/reset-password?token=…`

> v1: not built (password reset is out of scope for accounts-password).

Forgot: Email field, "Send reset link"; always responds "If an account exists for that email,
we've sent a link." Reset: New password + strength meter, "Set password", then sign-in with a
success toast. Reset signs out other sessions (stated under the button).

## First-run profile — `/welcome` · mobile: Welcome

Shown once after the first sign-in. Card with three fields and a live preview of the user's
avatar (identity colour from their ID):
- Display name (prefilled from sign-up or provider; Apple sends it only once, so this is where
  a missing name is asked for).
- Default currency (Select, INR preselected for `en-IN` locales).
- UPI ID (optional) with helper "So friends can pay you with one tap. Looks like
  name@okbank." Validated loosely (`^[\w.\-]{2,256}@[a-zA-Z]{2,64}$`), never verified.
Primary "Let's go" → Home (empty state "Start your first group"). "Skip for now" ghost.

If the user arrived from an invite link, "Let's go" lands in that group instead.

> v1: "Let's go" saves and "Skip for now" doesn't; both land on `/me` until Home exists.
