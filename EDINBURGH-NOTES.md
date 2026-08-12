# Edinburgh patch notes for Steph

Append-only observations from the Fringe run — things noticed live that may (or may not)
want a patch-side look. Distinct from COTF-CHANGELOG.md (which records changes we made).
Newest first.

## 2026-08-12 (day 6)
- **Seat 5 struggles to cut through** — performers feel like they're not playing. (We
  live-trimmed the Room 3 seat 5 monitor +5 dB today via `/airkit/seatTrim 5 1.7783`,
  not yet baked into `~cotfSeatTrims` — but the underlying feel may be patch-side too.)
- **Seat 5 sliding notes maybe too jittery/subtle:** it sounds mostly out of tune —
  which is nice! — but perhaps too subtle to read as "me playing."
- **Sensitivity as a per-patch variable?** Could sensitivity be exposed per patch — or
  even just a toggle: `normal` (loads as today) vs `boost` (super sensitive) for
  performers who barely move the stick? Would give Room 2/3 staff a live lever for
  low-energy players (pairs with the 08-10 ALTOSYNTH dynamic-range note: squeeze both
  ends of the curve).
- **Seat 3 direction idea:** could really be a glistening, rapid patch — even *higher*
  than it is now?
- **Tuning state could be slightly softer overall:** people are hilariously moving too
  much during tuning and miss the actual oboe-then-orchestra tuning moment. (Extends the
  08-10 note about Jupiter sharp/altosynth/soprano tuning levels.)

## 2026-08-10 (day 5)
- **Balance vs group energy:** a lot of the time we want the instruments louder and are
  tempted to boost them — but then a REALLY active group comes in and it feels like
  they're playing so loud you can't hear the orchestra. Wondering whether this is a
  compression thing or a gesture-sensitivity thing (i.e. should heavy sustained movement
  saturate earlier rather than keep scaling?).
- **Room 2 could be even MORE expressive and complex.** People are really enjoying the
  playing-together part — we can literally sit for two minutes and they just play, so it's
  almost a piece in itself. Wonder about even bringing in a snippet of Beethoven under it?
- **Tuning state balance:** probably a little loud on Jupiter sharp, altosynth and the
  soprano voices — hard to hear the orchestra over the tuning notes, and people really
  move during tuning.
- **ALTOSYNTH dynamic range:** quite overwhelming when played consistently — it dominates
  the room when people move heavily, but with subtle movement people often say they
  couldn't hear their sound at all. Feels like the response curve needs squeezing from
  both ends.
- **Heavier instruments are played less** — wonder if physical weight should be more of a
  factor in how sensitive/rewarding those patches are.
- **Seat 1 (BASS BUZZ):** performers often say they couldn't hear themselves — maybe a sub
  thing? It's PUMPING in the room from our perspective, but a little more *buzz* (upper
  harmonics on the chair speaker) might help them locate themselves.
- **Sampler level-0 mid-piece (seen live 08-10, 15:17 piece):** SOPRANOVOICE (seat 5) was
  receiving fresh input but synthesising at level 0 mid-piece; recovered live with our
  `POST /api/airkit/reset-seat`. Same class as the "sampler personalities unproven in
  piece" note — flagging that it's now been observed in a real show.
- Patch idea: a **'bird' patch** — super tweety, high and fast twinkling lines.

## 2026-08-07 (day 2)
- Seat 3 (ALTOSYNTH), Room 2: could be more sensitive to movement in the **idle** state.
  It seems fine in the piece state, but in idle people are struggling to get sound out of
  it — you really have to shake it violently to match the volume of the other players.
- Spitball (no change requested, just a thought to kick around): keep each instrument's
  *structure* in place (pitch sets, rhythmic sequences etc.) but swap the **samples or
  effects** when there's a really strong match with the performer's word answer. We're
  seeing a lot of similar/same answers (e.g. "ocean" comes up heaps), and it could be
  smart to alter the patch on a super close match — e.g. anything with wind adds white
  noise to the patch, water swaps the percussion samples to water samples. Ciaran likes
  the idea; worth a chat before any patch work.

## 2026-08-06 (day 1)
- Seat 3 (ALTOSYNTH): during tuning this didn't sound like an A pitch-set — possibly a B?
  Reference note only, no change requested; flagging for when you're next in the patch.
