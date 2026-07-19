# COTF Issue Tracking

This file is a shared log for ongoing issues discovered during venue testing and development. Both the COTF team and Steph edit in place as issues are investigated and resolved. 

**Convention:** each issue has a checkbox (`- [ ]` for open, `- [x]` for closed), a status tag (`[open]` = engineering investigation, `[steph]` = composer patch/design investigation, `[fixed]` = resolved and deployed), and pointers to evidence (logs, show bible rows, commits). When closing an issue, check the box and add a note: "Fixed in {commit/changelog ref} — {one-line description}."

---

- [ ] **[steph] Stuck note at day-one close** — one patch lagged and held a note at end of day (2026-07-18); engines were stopped to silence it. Suspects: harp/dulcimer template personalities flagged "not working yet" in commit `909ed5a`, or a Pdef release miss at curtain/idle. Evidence: show bible `bible.md` ledger row 2026-07-18 + `sc-room3-out.log` end-of-day tail. Containment: `/airkit/panic` (2026-07-19) now available for room transitions and close-down so no engine stop is required.

- [ ] **[steph] Hanging warm-up voices on room transition** — a harp lingered in Room 2 long after its performer moved to Room 3 (observed 2026-07-18). Likely a hanging OSC value or un-released voice when sticks transition Room 2 → Room 3. Containment: `/airkit/panic` (2026-07-19) fires on every room transition and clear. If the underlying release-miss is in the personality templates, it's the same family as the stuck-note issue above.

- [ ] **[open] Dead seat mid-piece** — at least two 2026-07-18 experiences had a seat go silent mid-show (groups 27–29 window; correlate `sc-room3-out.log` with the bible timelines). Root cause unknown. Detection: `/airkit/getLevels` (2026-07-19) meters per-seat linear peak since last poll, tapped pre-gain on the seat bus — staff can now see a dead/silent patch in a loud room. Recovery: `/airkit/resetSeat` (2026-07-19) tears down one seat only (duck monitor gain, stop Pdef, unload via shared path) with no engine restart; M0 re-pushes the saved personality ~300ms later.

- [ ] **[steph] Compositional balance notes** — observations from 2026-07-18, no engineering action now:
  - "Bigger sounds" sometimes **stomped on the harp** in the warm-up room.
  - Buzzy synth sounds **got lost in the wash** of real orchestral sounds.
  - In the score's **slower/quieter moments, participants played more** — there was space in the music to do so. An observation worth designing toward: quiet space invites participation.
