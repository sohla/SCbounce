# COTF research data collection — one-page plan (for Steph)

**Status:** implemented on the COTF side, 2026-07-20. **Nothing here touches AirKit** — this
page exists so you have the framing and can be a co-author if you want to be. Read this once;
it doesn't require anything of your machine or your code.

## Framing

We're building a small dataset alongside the live show to back a CHI/NIME-oriented paper on
digital musical instruments (DMIs): does an audience member's movement behaviour while playing
a fictional gestural instrument (an AirStick) relate to their musical background, comfort with
movement-based interaction, and self-reported sense of control/enjoyment/expressiveness? The
AirStick's IMU stream is, functionally, a continuous record of "how does a first-time player
move a novel instrument," which is exactly the kind of meta-variable movement study the
CHI/NIME DMI-evaluation literature asks for and rarely gets at this scale (100+ people/session,
real performance stakes, not a lab task). The dataset is intentionally the "backbone" — always
current, one file, easy to hand off for analysis later.

## What's tapped, and where

A **read-only, flag-gated copy** of the AirStick OSC packets is teed off in the COTF server's
router (`server_backend/src/osc/airstick-router.ts`), on M0, **upstream of AirKit's input** —
it's a second `send()` of the same buffer the router already forwards to you, to a private
localhost port. **AirKit itself is completely untouched: no new code runs on M1, no new OSC
address exists on your side, and nothing about what reaches your devices changes in any way.**
If the research collector is off, crashed, or was never started, the show and your engine
behave exactly as they do today — the tee just vanishes into a closed socket.

A separate process on M0 (`cotf-research`, its own package, its own SQLite file) listens for
that tee, decodes the IMU floats itself, and computes movement features. It also polls the
COTF server's own read-only HTTP API (occupancy, group membership) to know when a "warm-up
phase" and "piece phase" start and end for each seat. None of this involves AirKit or M1 in
any way.

## The 8-feature battery (computed once per phase, per seat)

| # | Feature | What it captures |
|---|---|---|
| 1 | RMS linear acceleration | overall intensity of arm movement |
| 2 | RMS angular speed | overall intensity of rotation/gesture |
| 3 | Active movement ratio | fraction of the phase spent actually moving vs. still |
| 4 | Submovement rate | how many distinct movement "hits" per minute |
| 5 | Rotational smoothness (SPARC) | jerky vs. fluid gesture, rotational |
| 6 | Translational smoothness (LDLJ-A) | jerky vs. fluid gesture, translational |
| 7 | Orientation coverage entropy | how much of the "pointing sphere" they explored |
| 8 | Rotation/translation ratio | gesture style — a rotator vs. a mover |

Each feature is stored as the whole-phase value plus a median/IQR across overlapping 5 s
windows, so within-session stability is visible too. No raw movement is ever kept — samples
are buffered in memory only for the duration of a phase, then discarded once the features are
computed.

## Survey items (post-show, opt-in)

A link in the post-show video email (flag-gated, off unless explicitly enabled) points at a
one-screen survey: musician-identity rank, years of formal training, current playing
frequency, primary instrument, comfort with movement-based interaction, and four 1–7 Likerts
on control/enjoyment/expressiveness/confidence during the performance. This is the only place
audience-supplied text is collected for research; the kiosk's existing `experience_level` and
word-answer fields are the "always-on floor" and were already being collected for the show
itself.

## Anonymisation

Every participant gets a 16-character token = the first 16 hex characters of
`HMAC-SHA256(secret, audience_id)`. The token is computed independently in two places from the
same shared secret — the collector (when it writes a features row) and the COTF server's email
template (when it renders the survey link) — so they always agree without either side ever
telling the other the real `audience_id`. The research database stores **no name, no email, no
audience_id** — only the token, seat, show date, features, and survey answers. Someone holding
both the show database and the research database *and* the secret could re-link a token back to
a person; the research database alone is anonymous. This is deliberate — it's what the
blanket-consent ethics protocol requires and no more.

## Co-authorship

You're welcome to be a co-author on anything that comes out of this — the movement data only
exists because your engine (and the composer relationship it represents) is the actual
instrument audiences are playing. Nothing about authorship changes what's tapped or how; this
is just an early flag so it's not a surprise later. Ask Ciaran if you want more detail on the
ethics protocol or the eventual paper draft.

## The one sentence that matters

**AirKit is untouched. No research code runs on M1. The tap is on M0, upstream of AirKit's
input, and a dead or absent research collector changes nothing about how AirKit behaves.**
