# Super-Seats — how your personalities reach performers (and how to add one)

*A COTF ↔ Steph working doc. Brainstorm on it, edit it, argue with it — that's what it's for.*
*System-side design lives in the COTF repo: `docs/superpowers/specs/2026-08-15-super-seats-design.md`. Same terminology both sides.*

## What's changing, in one paragraph

Right now each chair has a fixed sound: seat 2 plays JUPITERSHARP for every group, all day. **Super-seats** unfixes that: each *performer* gets a sound picked from the whole pool of your personalities, matched to what they told us at the front desk (their word answer — lots of people write birdsong, wind, water, drums — plus a new "how adventurous do you want to sound" question). The sound binds to the person when their group locks in the corridor, follows them through Room 2 and onto the Room 3 stage, and goes back in the pool when they leave. So: someone who writes "drums" can actually get drums, even if the last group's drummer just walked off stage — as long as there's a drums-ish personality free. Nothing changes in how your engine runs: it's still `loadPersonality` on a device port; we just choose the index differently.

## What this needs from you

Three things, all in files you already own:

### 1. One naming rule

**Roster name = filename minus `.sc`.** If the file is `personalities/cotf_drums1.sc`, the entry in `lists/list_cotf.sc` is `"cotf_drums1"`. Today the roster holds `"PERCUSSION"` etc., which match no file — the system can't connect a roster entry to its header without this rule. First job of the rollout is migrating the current 5 sounds to follow it (we'll schedule that together — it touches live sound).

### 2. Header keys

The comment block at the top of each personality file is what the matcher reads. Existing keys stay as they are (`gestures, description, sound, pitch, rhythm, prints`). New **optional** keys:

```
/*
gestures:    [beat, shake]
description: Rolling hand drums that answer your movement
sound:       warm skins, low thud, dry
pitch:       low
rhythm:      driving
prints:      [brownShaker, blackShaker]
seats:       [1, 2, 3]          <- chairs this sound is allowed on. Leave out = any chair.
affinity:    [drums, heartbeat, thunder]   <- words/themes it answers to
register:    [traditional]      <- traditional, wild, or both. Leave out = both.
family:      drums              <- groups variants (drums1..4). Two performers CAN get two drums variants.
*/
```

**Every key is optional and nothing breaks if you skip them** — a personality with no header still works, it just gets matched generically (any seat, any register, no word affinity). Constrain `seats:` only when the sound physically needs it; over-constraining shrinks who can receive it.

- `affinity:` is the big one for matching — the words people actually write are things like *birdsong, wind, water, rain, thunder, drums, piano, bells, waves, heartbeat*. If your personality answers to a theme, say so.
- `register:` = who should get it. `traditional` → people who asked for "an instrument everyone would recognise". `wild` → people who asked for "a sound nobody's heard before". Both is fine and common.

### 3. Which chairs suit which sounds — the seat character table

The five Room 3 chairs are not identical: different speakers, EQ, and positions (the per-seat trims are baked in `~cotfSeatTrims`). Fill this in together so `seats:` choices are grounded:

| Seat | Speaker / EQ character | Position | Suits | Avoid |
|---|---|---|---|---|
| 1 | *(bass chair — biggest low-end)* | | deep / bassy sounds | |
| 2 | | | | |
| 3 | | | | |
| 4 | | | | |
| 5 | *(+5 dB trim baked)* | | | |

Working example from the brainstorm: a bassy sound might be `seats: [1, 2, 3]`; a traditional mid-range instrument might sit best `seats: [2, 3, 4]`.

## How the system uses it (so nothing is a mystery)

1. **Front desk:** performer answers the word question + the new 4-option "what should your instrument be?" question. (Their crafted instrument *name* and 3D print are chosen here, as now — deliberately independent of the sound.)
2. **Corridor lock:** the group of 5 is fixed. The system makes **one** assignment for the whole group: each performer ↔ one personality, respecting `seats:` (hard), no duplicates within the group (hard), and matching `affinity`/`register` + spreading usage across the day (soft). An LLM does the tasteful version; if it's slow or wrong, a plain scorer takes over; if all else fails, seats fall back to the current fixed sounds. The show can never be blocked by this step.
3. **Room 2 / Room 3:** their personality is loaded on whatever chair they sit in, in each room. It follows the person, not the chair.
4. **Between groups / idle:** chairs revert to the default per-seat sounds (today's exact setup).

A personality enters the pool automatically when it's **in the roster + its file parses + not switched off** on our Patches admin page. The Patches page shows every personality's status and, if it's out, exactly why.

## Rollout — so we both know where we are

- **Phase 0 (now):** this doc; agree header keys + naming rule; fill the seat table; migrate the current 5 sounds to `cotf_*` names with headers pinned to their current seat.
- **Phase 1 — shadow:** the picker runs silently on real groups and only *logs* what it would have chosen. With each current sound pinned to its own seat it must match today's behaviour exactly — free live testing, zero audience risk.
- **Phase 2 — live flip (bench day):** same 5 sounds, now flowing through the new path. Full walkthrough.
- **Phase 3 — your new files:** as `cotf_*.sc` personalities land with headers + roster entries, the pool just grows. **No code changes needed per patch** — that's the whole point.

## FAQ

**Do I have to do anything to my SC code?** No. Headers and roster only. `loadPersonality` semantics are untouched (additive-only rule stands).

**What if two people both want drums?** Make variants (`cotf_drums1..4`, same `family:`). Duplicates of a *personality* aren't allowed in one group; two variants are.

**What if I get a header wrong?** The sound stays available but matches generically. Worst case a sound never gets picked for the people it suits — visible on the Patches page, never a show-stopper.

**Can a sound be Room 2-only or Room 3-only?** Not in v1 — both rooms share the pool via the roster. Say if you need this.

**How many sounds do we need?** Minimum 5 eligible covering all seats (preflight checks this). More = better matching. No upper limit.

---
*Questions / pushback → edit this file or grab Ciaran. — COTF, 2026-08-15*
