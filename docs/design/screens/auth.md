# Auth screens (F-02, rows 2–3)

Signed-out screens share one layout: a centred 400 px card on `bg` with a playful
illustration band behind it (three soft identity-coloured blobs, `aria-hidden`). On phones the
card is full width with 16 px gutters and the blobs sit above it. Wordmark "kanakku" (Baloo 2
800, `primary`) and the tagline "Split trips, flats and dinners. Settle over UPI." at top.

Mock: [web-sign-in](../mocks/web-sign-in.html).

## Sign in — `/sign-in` · mobile: SignIn

Content, top to bottom:
1. h1 "Welcome back".
2. Provider buttons (row 3; render only those the admin enabled): "Continue with Google",
   "Continue with Microsoft", "Continue with Apple", then any custom OIDC providers by their
   configured name. Secondary buttons, full width, provider logo left (official marks, sized
   per each provider's brand guide; Apple's button is black in light mode and white in dark).
3. Divider "or".
4. Email, Password (with show/hide IconButton), "Forgot password?" ghost link right-aligned.
5. Primary lg button "Sign in".
6. "New here? Create an account" link.

States:
- Wrong credentials: InlineProblem above the fields, "That email and password don't match."
  (never say which one is wrong).
- Unverified: "Verify your email first. We sent a link to p•••@gmail.com." + "Resend link"
  ghost button (cooldown 60 s, shown as "Resend in 42 s").
- Rate limited (5 fails / 10 min): "Too many tries. Try again in 8 minutes." and the button
  disables with a live countdown.
- OIDC error on return: "Couldn't sign you in with Google. Try again." (no token detail).

API: `POST /api/v1/auth/sign-in` (session cookie + CSRF token); OIDC via
`/oauth2/authorization/{provider}`. After sign-in, go to the `returnTo` route or Home.

A11y: provider buttons are real buttons with text; password toggle has "Show password" label
and `aria-pressed`; errors are announced via `role="alert"`.

## Sign up — `/sign-up`

Display name, Email, Password with a strength meter (3 segments; rule text: "At least 10
characters. A passphrase works well."), checkbox-free (no marketing consent). Primary "Create
account". Provider buttons above as on sign in. On success → Verify email screen.

## Verify email — `/verify-email`

Illustration: an envelope blob with a sparkle. h1 "Check your inbox". Body "We sent a link to
priya@example.com. It expires in 24 hours." Buttons: "Resend link" (secondary, cooldown),
"Use a different email" (ghost). The link in the email lands on `/verify-email?token=…`, which
shows a spinner, then "Email verified" with a bounce check and continues to first-run profile.
Expired token: "This link has expired" + Resend.

## Forgot / reset password — `/forgot-password`, `/reset-password?token=…`

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
