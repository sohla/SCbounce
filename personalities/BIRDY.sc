/*
gestures:    [shake, tilt, flick]
description: Two birds in one bush — shake to make a blackbird sing fluty phrases, and a whipbird answers with sparse cracking calls between them; tilt moves both up and down, flick your wrist for a shrill twiddle
internals:   Two engines in one personality, sharing one Group. (1) Birdsong's phrase queue: a Pdef whose \dur Pfunc pulls notes off a language-side blackbird motif queue, firing \labBirdChirp. (2) WHIPBIRD's caller: a second Pdef firing self-freeing \whipbird one-shots, its density driven by a \dur Pfunc so state ticks can thin it out. Both SynthDefs are copied VERBATIM from personalities/Birdsong.sc and personalities/WHIPBIRD.sc — do not retune them here. No samples. Patch trim defaults to 1.0 (0 dB, no ~cotfPatchTrims entry) — verify at bench.
sound:       a blackbird singing fluty scooped phrases with bright twiddles, punctuated every few bars by a whipbird's noise crack and rising whistle, both in a small forest reverb; still stick is silent
pitch:       blackbird as in Birdsong — idle C major pentatonic walked stepwise in the 1.4-2.6 kHz register, tilt shifts an octave with hysteresis, piece takes pitch classes from ctx.voicePool, shrill runs +12..+19 above. Whipbird as in WHIPBIRD — \freq is the START of its swoop and it peaks an octave above, based at MIDI 61; idle picks from whipIdleNotes by y tilt, piece takes a pitch class from ctx.voicePool, tuning pins to MIDI 69 so the whistle peaks on A5 (880 Hz).
rhythm:      blackbird phrases are beat-quantised on ~beatClock with phrase and pause length following accelMassFiltered energy; the whipbird fires one call every whipGap beats (idle 8, piece 4-10 by energy, tuning 6, curtain 14) so it reads as punctuation, not a duet
instruments: [oliveBird]
prints:      [oliveBird, whiteWing, greenHolder]
seats:       [3, 4, 5]
affinity:    [bird, birds, birdsong, blackbird, whipbird, chirp, whistle, whistling, crack, whip, dawn, morning, garden, forest, bush, jungle, rainforest, nature, wild, feathers, wings, spring, country, squeaking, glass, chipmunk]
register:    [wild]
family:      bird
*/

// -------------------------------------------------------------------------
// BIRDY — Birdsong and WHIPBIRD in one seat.
//
// Built by combining the two existing p-files rather than rewriting either:
//   * personalities/Birdsong.sc  — blackbird phrase engine + \labBirdChirp
//   * personalities/WHIPBIRD.sc  — whipbird caller + \whipbird
// Both SynthDefs are copied across UNCHANGED. If either voice needs to sound
// different it changes in its source file (or synths/whipBird.sc) and gets
// copied back over — not edited here.
//
// What had to be reconciled, because a personality has only one of each:
//
//  1. TWO Pdefs, one env. m.ptn names the blackbird; the whipbird gets
//     whipPtn = m.ptn ++ "whip". Both play on ~beatClock into one Group, and
//     ~init / ~deinit / ~onResync handle the pair.
//
//  2. ONE set of filter coefficients. The model is shared, and the two files
//     asked for different smoothing (accel decay 0.1 vs 0.15, rrate decay
//     0.2 vs 0.6). Birdsong's win: its flick detection needs rrate to fall
//     fast, and the whipbird only reads rrate for its curtain amp, where a
//     quicker decay just means it thins out sooner.
//
//  3. Whipbird density is now a \dur Pfunc reading `whipGap`, not the fixed
//     Pseq of the source file. In WHIPBIRD the ticks' \dur sets were dead
//     code (a Pbind key overrides the envir, which is why they sit commented
//     out there) — so its call rate could not be state-driven at all. Here it
//     can be, and it has to be: at the source Pseq's rate the whipbird talks
//     over the blackbird continuously instead of punctuating it.
//
//  4. Whipbird tuning now sets \freq. WHIPBIRD's ~tuningNext pins \pitchRand,
//     but that arg is inert — \whipbird declares it and never reads it, so
//     during tuning the call just kept whatever pitch idle/piece last left.
//     This pins \freq to MIDI 69 so the whistle peaks on A5, which is what
//     that file's `pitch:` header says it does.
//
//  5. Whipbird idle pitch now follows the tilt index. WHIPBIRD computes the
//     tilt index `n` and then calls idleNotes.choose anyway; its header says
//     tilt picks the note, so here tilt actually picks it.
//
// STANDARD CONTROL SURFACE (the blackbird half, refactored)
// ---------------------------------------------------------
// Birdsong drove its chirps from lexical closure vars: the state ticks set a
// dB RANGE (ampLoDb/ampHiDb) and the event handler re-derived each note's amp
// from gesture energy inside that range. Two consequences: Pdef(m.ptn).set(
// \amp, ...) did nothing, and because the quiet end of the range was still
// -32 dB, the bird sang continuously — there was no value any hook could
// write that meant "silent".
//
// Now BOTH voices are driven the ordinary way, with Pdef(...).set from the
// state ticks:
//
//   \amp     master gain. The ticks map gesture -> dB -> .dbamp exactly like
//            WHIPBIRD and the rest. The event handler multiplies it by the
//            per-note shaping factors only (ampScale for twiddles, freqComp
//            for register), so the level you command is the level you hear.
//   \pcs     pitch classes the phrases are built from
//   \notes   phrase length ceiling
//   \gapMin  } pause between phrases, in rest events; energy interpolates
//   \gapMax  } between them
//   \shrill  1/0, allow the high twiddle runs
//
// And the amp gate: below ampFloor (-60 dB) step() builds no phrase and
// spawns no synth, so the amp curves' -80/-90 dB floors mean actual silence
// at rest rather than a stream of very quiet chirps.
//
// Reading them back: a \dur Pfunc must use e[\key] — a bare ~key is nil
// there. The event handler can use ~key normally. (Verified, not assumed.)
// -------------------------------------------------------------------------

var m  = ~model;
var ob = ~outBus ? 0; // read, never assign — captured NOW, ~init runs under topEnvironment.use

var group;           // ONE dedicated Group for both voices' synths
var clock;           // ~beatClock, captured inside ~init under topEnvironment.use
var eventTypeName = (\birdy_ ++ m.ptn).asSymbol;   // env-unique custom event type
var whipPtn       = (m.ptn ++ "whip").asSymbol;    // second Pdef name — see note 1

// ---- gesture state (written at tick rate, read by both patterns) ---------
var energy    = 0;    // accelMassFiltered — sustained shake energy
var prevEnergy = 0;
var twist     = 0;    // rrateMassFiltered — wrist flick
var tiltOct   = 0;    // -12 / 0 / +12 from gyroYFiltered, with hysteresis
var restFloor = 0.25; // a still stick reads ~0.2 (gravity) — below this: silence
var urgent    = false; // a fresh shake edge → start a phrase on the very next event
var lastFlick = 0;    // TempoClock.beats of the last flick-triggered shrill run
var muted     = false; // \silent handled here (there is no ~silentNext)

// ---- blackbird musical state --------------------------------------------
// NB: the blackbird's CONTROLS are not here — they live on the Pdef and are
// written with Pdef(m.ptn).set(...) from the state ticks, exactly like every
// other p-file. See the "standard control surface" note in the header.
// What stays lexical is engine STATE (the queue and what it is mid-way
// through), which no tick should be reaching into.
var stateName = \idle;
var idlePcs   = [0, 2, 4, 7, 9];         // idle default: C major pentatonic
var flickPending = false;                // sense() raises it, step() consumes it
var ampFloor  = -60.dbamp;               // commanded \amp at/below this = don't sing at all
var tuneTime  = 0;                       // TempoClock.beats when tuning began (§15 reload guard)

// ---- whipbird state ------------------------------------------------------
var whipBase      = 61+12;                  // MIDI note the calls are based on (as WHIPBIRD)
var whipIdleNotes = [0, 4, 7, 11, 14];   // idle offsets above whipBase, picked by y tilt
var whipTuneMidi  = 69;                  // tuning pins here → whistle peaks A5 (880 Hz)
var whipTuneRamp  = 15.0;                // seconds for the tuning bend to reach true (§16)
var whipGap       = 8;                   // beats between calls — the \dur Pfunc reads this

// ---- blackbird phrase engine --------------------------------------------
var queue   = [];     // pending note Events — ALWAYS index with [\key] (pitfall 1)
var cur     = nil;    // the note the current pattern event is playing (nil = rest)
var gapLeft = 0;      // rest events remaining before the next phrase may start
var lastMidi = 95;    // where the previous phrase left off — the next one walks on from here

var midiLo = 86, midiHi = 100;   // fluty register ≈ 1.2–2.6 kHz before tilt shift

// ---- WOBBLE: the FM "trill" on every chirp -------------------------------
// EDIT THE WOBBLE HERE. Nothing else in this file reads these numbers.
//
// \labBirdChirp multiplies the chirp's pitch by
//
//     1 + (trill * SinOsc.kr(trate))       <- line ~152, the `f = ...` line
//
// so there are TWO independent knobs per note:
//
//   trill  DEPTH — how far the pitch swings, as a fraction of the note.
//                  0.005 = ±0.5 %, about ±9 cents. 0 = dead straight tone.
//   trate  RATE  — how fast it swings, in Hz. 6 = a slow fluty flutter,
//                  80 = a fast buzzy twiddle.
//
// Each is a LO/HI pair; one value is picked at random per note, so the bird
// doesn't wobble identically every time. Narrow the pair to make it uniform.
//
// To reduce the wobble:
//   * less warble, same speed  -> lower the *Trill* numbers (depth)
//   * slower warble, same size -> lower the *Trate* numbers (rate)
//   * none at all              -> set the *Trill* pair to 0, 0
//
// Ordinary fluty motif notes (buildPhrase):
var motifTrillLo     = 0.003, motifTrillHi     = 0.007;  // depth, short notes
var motifTrillLongLo = 0.008, motifTrillLongHi = 0.014;  // depth, the held last note
var motifTrateLo     = 2.75,  motifTrateHi     = 4.5;    // rate Hz, all motif notes
                                                         //   (2026-08-26: halved from 5.5/9)

// The shrill twiddle run (shrillRun) — deliberately much deeper and faster:
var shrillTrillLo    = 0.035, shrillTrillHi    = 0.07;   // depth
var shrillTrateLo    = 7.5,  shrillTrateHi    = 15;     // rate Hz
                                                         

// Forward declarations — SC requires every `var` before the first statement; the
// helper functions below are assigned later.
var freqComp, scaleNotes, unitBeats, shrillRun, buildPhrase, gapFor, step, sense;

// Smoothing knobs — Birdsong's, see note 2 in the header block.
m.accelMassFilteredAttack = 0.95;
m.accelMassFilteredDecay  = 0.1;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay  = 0.2;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay  = 0.7;

//------------------------------------------------------------
// VERBATIM from personalities/Birdsong.sc — do not edit here.
// One chirp. Pure-ish tone (sine + a little 2nd/3rd harmonic + a breath of
// band-passed noise), a three-segment pitch glide (scoop in → hold → tail
// off), light FM "trill" (slow/shallow for fluty notes, fast/deep for the
// shrill twiddle), per-note small-room reverb, self-freeing.
SynthDef(\labBirdChirp, { |out=0, freq=2000, g0=0.9, g1=1.0, secs=0.15, amp=0.1, pan=0,
	trill=0.005, trate=1, bright=0.15, air=0.04, verb=0.22|
	var life  = secs + 0.6;   // amp env + reverb tail, then free
	var fenv  = EnvGen.kr(Env([g0, 1, 1, g1], [0.22, 0.38, 0.4] * secs, \exp));
	var aenv  = EnvGen.kr(Env([0, 1, 0.85, 0], [0.12, 0.55, 0.33] * secs, [-2, 0, -3]));
	var f     = freq * fenv * (1 + (trill * SinOsc.kr(trate, Rand(0, 2pi)))) * 0.5;
	var tone  = SinOsc.ar(f) + (SinOsc.ar(f * 2) * bright * 0.6) + (SinOsc.ar(f * 3) * bright * 0.2);
	var breath = BPF.ar(WhiteNoise.ar, f, 0.08) * air * 4;
	var sig   = (tone + breath) * aenv * amp;
	var st    = Pan2.ar(sig, pan);
	var wet   = FreeVerb2.ar(st[0], st[1], verb, 0.55, 0.6);
	Line.kr(0, 1, life, doneAction: Done.freeSelf);
	Out.ar(out, wet);
}).add;

//------------------------------------------------------------
// VERBATIM from personalities/WHIPBIRD.sc (itself verbatim from
// synths/whipBird.sc) — do not edit here.
SynthDef(\whipbird, {
    |out=0, pan=0, amp=0.3, gate=1, swoopDelay=0.03, gliss=0.01,pitchRand=1, freq = 440,
        reverbMix=0.3, reverbTime=2.0, reverbSize=0.8|

    var whipEnv, whistleEnv, whipOsc, whistleOsc, sig, mainEnv;
    var numCombs = 7;
    var numAllpass = 4;
    var room = reverbSize.clip(0.1, 0.9);

    mainEnv = EnvGen.kr(
        Env.asr(0.02, 1, 0.8),
        gate
    );
    whipEnv = EnvGen.ar(
        Env.perc(0.001, 0.03, curve: -8),
        gate
    );
    whistleEnv = EnvGen.ar(
        Env(
            [0, 0, 1, 0],
            [gliss, 0.15, 0.2],
            curve: [0, 2, -4]
        ),
        gate
    );
    whipOsc = WhiteNoise.ar *
        BPF.ar(
            PinkNoise.ar,
            freq: XLine.kr(12000, 2000, 0.03),
            rq: 0.05,
        ) +
        Resonz.ar(
            PinkNoise.ar,
            XLine.kr(9000, 1500, 0.03),
            0.1,
            0.2
        );

    whistleOsc = SinOsc.ar(
        freq: Env(
			[freq, freq * 2, freq * 1.9],
            [swoopDelay, 0.15, 0.15],
            [\sine, \sine, -3]
        ).kr
    ) * whistleEnv;

    sig = (whipOsc * whipEnv * 0.2) + (whistleOsc * 0.2);
	sig = Pan2.ar(sig * 0.5, pan);
    sig = FreeVerb2.ar(
		sig[0], sig[1],
        mix: reverbMix,
        room: room,
        damp: 0.3
    );
    sig = sig + DelayN.ar(
		sig,
        0.1,
        [0.033, 0.039, 0.045].collect({ |t|
            sig * LFNoise2.kr(0.1).range(0.01, 0.02) * DelayC.ar(sig, 0.1, t)
        }).sum
    );
	DetectSilence.ar(sig, time:0.3, doneAction:2);
    Out.ar(out, sig * amp * mainEnv);
}).add;

//------------------------------------------------------------
// Blackbird helpers, copied from Birdsong.sc (lexical — no ~globals inside,
// so they are safe from any env).

// Loudness compensation: 2 kHz = unity, higher notes quieter, lower a touch louder.
freqComp = { |freq| (2000 / freq).sqrt.clip(0.45, 1.3) };

// Scale notes available right now, sorted, inside the (tilt-shifted) fluty
// register. pcsIn comes from the pattern's \pcs key, not a lexical global.
scaleNotes = { |pcsIn|
	var lo = midiLo + tiltOct, hi = midiHi + tiltOct;
	var use = if (pcsIn.isNil or: { pcsIn.isEmpty }) { idlePcs } { pcsIn };
	var notes = [];
	(lo..hi).do { |n| if (use.includes(n % 12)) { notes = notes.add(n) } };
	if (notes.isEmpty) { notes = [lo + 9] }; // never empty
	notes
};

// Note length unit in clock beats: ~120 ms at the current tempo, rounded to
// a quarter-beat so phrases still land on the grid whatever the tempo is.
unitBeats = {
	var tempo = clock !? { |c| c.tempo } ? 4;
	((0.12 * tempo) / 0.25).round.max(1) * 0.25
};

// THE QUEUED NOTE. Both builders below just write one of these Events per
// note straight into `queue`; step() pulls them off and the event handler
// hands each field to \labBirdChirp as the arg of the same name. So editing
// a sound here is editing the number next to its own key — no indirection.
//
//   note      pitch, MIDI note number  -> \freq  (handler does .midicps)
//   beats     length in clock beats    -> \secs  (handler divides by tempo)
//   g0        pitch at the START of the glide, as a multiple of freq
//             (< 1 scoops UP into the note, > 1 falls into it)
//   g1        pitch at the END of the glide, same units (the tail-off)
//   trill     wobble DEPTH, fraction of the pitch   } see the WOBBLE block
//   trate     wobble RATE, Hz                       } near the top of the file
//   bright    level of the 2nd/3rd harmonics
//   air       level of the band-passed noise "breath"
//   ampScale  per-note level trim (twiddles sit under motif notes) — NOT the
//             master gain, which arrives from Pdef(m.ptn).set(\amp, ...)
//   pan       -1 .. 1

// The shrill run: 4–8 very fast alternating up/down sweeps, +12..+19 above
// the motif, quieter, with fast FM — the "twiddle" that ends a blackbird phrase.
shrillRun = { |fromMidi, k|
	var u = unitBeats.();
	var sb = (u * 0.5).max(0.25) * rrand(0.5, 1.5);
	var base = (fromMidi + 12 + 5.rand).clip(100, 112);
	k.collect { |i|
		var up = i.even;
		var midi = (base + (if (up) { rrand(1, 4) } { rrand(-4, -1) })).clip(98, 114);
		(
			note:     midi,
			beats:    sb,
			g0:       if (up) { rrand(0.72, 0.85) } { rrand(1.18, 1.35) },  // scoop up / fall in
			g1:       if (up) { rrand(1.04, 1.15) } { rrand(0.86, 0.95) },  // tail up / down
			trill:    rrand(shrillTrillLo, shrillTrillHi),   // WOBBLE depth
			trate:    rrand(shrillTrateLo, shrillTrateHi),   // WOBBLE rate, Hz
			bright:   0.25,
			air:      0.32,
			ampScale: 0.45 * (1 - (i * 0.04)),               // quieter, fading over the run
			pan:      rrand(-0.5, 0.5)
		)
	}
};

// A blackbird phrase: a few fluty motif notes walked stepwise (with a leap
// now and then), scooped in, some held long; then maybe the shrill tail.
buildPhrase = { |pcsIn, maxN, shrillOK|
	var notes = scaleNotes.(pcsIn);
	var u = unitBeats.();
	var n, idx, out = [];
	var e = energy.clip(restFloor, 1.5);
	n = (2 + (e.linlin(restFloor, 1.5, 0, 5)).round + 2.rand).clip(1, maxN).asInteger;
	idx = notes.indexOfGreaterThan(lastMidi - 1) ? (notes.size - 1);
	idx = (idx + [-1, 0, 0, 1].choose).clip(0, notes.size - 1);
	n.do { |i|
		var step = [-3, -2, -1, -1, 0, 1, 1, 2, 3].choose;
		var beats, long;
		if (i == 0) { step = [-2, 0, 1, 2].choose };
		idx = if (notes.size > 1) { (idx + step).fold(0, notes.size - 1) } { 0 };
		long = (i == (n - 1)) and: { 0.45.coin };
		beats = if (long) { u * [2, 3].choose } { u * [1, 1, 1, 1.5, 2].choose };
		out = out.add((
			note:     notes[idx],
			beats:    beats,
			g0:       [0.82, 0.88, 0.94, 1.0, 1.0, 1.08].choose,   // mostly scoops in
			g1:       [1.0, 1.0, 1.0, 0.96, 1.04].choose,          // mostly straight out
			trill:    if (long) { rrand(motifTrillLongLo, motifTrillLongHi) }
			                    { rrand(motifTrillLo,     motifTrillHi)     },  // WOBBLE depth
			trate:    rrand(motifTrateLo, motifTrateHi),           // WOBBLE rate, Hz
			bright:   rrand(0.1, 0.2),
			air:      rrand(0.03, 0.06),
			ampScale: 1.0,                                         // motif notes at full level
			pan:      rrand(-0.3, 0.3)
		));
		lastMidi = notes[idx];
	};
	if (shrillOK and: { (twist > 0.35) or: { 0.3.coin } }) {
		out = out ++ shrillRun.(lastMidi, rrand(4, 8));
	};
	out
};

gapFor = { |gMin, gMax|
	energy.clip(restFloor, 1.3).linlin(restFloor, 1.3, gMax, gMin).round.asInteger
};

// Called from the blackbird \dur Pfunc on every pattern event: decides whether
// this event is a note (sets `cur`) or a rest (cur = nil), returns its length.
//
// Every knob it reads comes off the event — i.e. off Pdef(m.ptn).set(...) —
// so the state ticks steer the phrasing the same way they steer any other
// p-file's pattern. NB: a \dur Pfunc must read e[\key]; a bare ~key resolves
// to nil there (verified — the envir is on the event, not currentEnvironment).
//
// The \amp gate is what stops this thing singing forever: below ampFloor the
// engine builds nothing and spawns nothing, so "quiet" means actually silent
// rather than a stream of very quiet chirps.
step = { |e|
	var beats   = 0.5;
	var amp     = (e[\amp] ? 0);
	var gMin    = (e[\gapMin] ? 2);
	var gMax    = (e[\gapMax] ? 16);
	var maxN    = (e[\notes]  ? 8);
	var shrillOK = (e[\shrill] ? 1) > 0;
	cur = nil;
	if (muted.not and: { group.notNil } and: { amp > ampFloor }) {
		// A flick raised the flag at tick rate; spend it here, where we know
		// whether this state allows twiddles at all.
		if (flickPending) {
			flickPending = false;
			if (shrillOK and: { energy > restFloor }) {
				queue = shrillRun.(lastMidi, rrand(3, 6)) ++ queue;
			};
		};
		if (queue.isEmpty) {
			if ((energy > restFloor) and: { urgent or: { gapLeft <= 0 } }) {
				queue = buildPhrase.(e[\pcs], maxN, shrillOK);
				urgent = false;
			} {
				gapLeft = gapLeft - 1;
			};
		} {
			// Stick put down mid-phrase: let one more note out, then stop.
			if ((energy < (restFloor * 0.8)) and: { stateName != \tuning } and: { queue.size > 1 }) {
				queue = queue.keep(1);
			};
		};
		if (queue.notEmpty) {
			cur = queue.removeAt(0);
			beats = cur[\beats];
			if (queue.isEmpty) { gapLeft = gapFor.(gMin, gMax) };
		};
	} {
		// Commanded silent (or torn down): drop anything still queued so the
		// bird doesn't resume mid-phrase when the amp comes back up.
		queue = [];
	};
	beats
};

// Per-tick gesture read, shared by all state hooks. Cheap arithmetic only.
sense = { |d|
	prevEnergy = energy;
	energy = m.accelMassFiltered;
	twist  = m.rrateMassFiltered;
	if ((energy > (restFloor + 0.1)) and: { prevEnergy <= (restFloor + 0.1) }) { urgent = true };
	// Tilt → octave with hysteresis so a wrist on the boundary can't flap.
	tiltOct = switch(tiltOct,
		0,   { if (m.gyroYFiltered > 0.4) { 12 } { if (m.gyroYFiltered < -0.4) { -12 } { 0 } } },
		12,  { if (m.gyroYFiltered < 0.25) { 0 } { 12 } },
		-12, { if (m.gyroYFiltered > -0.25) { 0 } { -12 } }
	);
	// A wrist flick raises a flag (refractory 1.2 s); step() spends it on the
	// next pattern event, where \shrill and \amp are readable. Keeping the
	// decision there means the ticks never have to mirror pattern controls
	// into lexical vars just so this hook can see them.
	if ((twist > 0.8) and: { TempoClock.beats > (lastFlick + 1.2) }
		and: { energy > restFloor }) {
		lastFlick = TempoClock.beats;
		flickPending = true;
	};
};

//------------------------------------------------------------
~init = ~init <> {
	topEnvironment.use {
		clock = ~beatClock;
		group = Group.new;

		// ---- blackbird: custom event type + phrase-queue Pdef -------------
		Event.addEventType(eventTypeName, { |e|
			var n = cur;
			if (n.notNil and: { group.notNil } and: { muted.not }) {
				var tempo = clock !? { |c| c.tempo } ? 4;
				var midi  = n[\note];
				var freq  = midi.midicps;
				// \amp arrives from Pdef(m.ptn).set(\amp, ...) — the state
				// ticks own the level, as in every other p-file. The two
				// factors below are per-note SHAPING, not level: ampScale
				// makes twiddles sit under motif notes, freqComp evens out
				// perceived loudness across the register. Master gain in,
				// master gain out.
				var gain  = (~amp ? 0);
				~instrument = \labBirdChirp;
				~out    = ob;
				~group  = group;
				~freq   = freq;
				~g0     = n[\g0];
				~g1     = n[\g1];
				~secs   = (n[\beats] / tempo).clip(0.035, 0.6);
				~amp    = gain * n[\ampScale] * freqComp.(freq);
				~pan    = n[\pan];
				~trill  = n[\trill];
				~trate  = n[\trate];
				~bright = n[\bright];
				~air    = n[\air];
				~sustain = ~secs; // informational only — \labBirdChirp has no gate
				~type = \note;
				currentEnvironment.play;
			};
		});

		Pdef(m.ptn, Pbind(
			\type, eventTypeName,
			\dur,  Pfunc { |e| step.(e) }
		));
		Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		// Standard control surface, seeded silent. The state ticks are the
		// only writers; a stickless seat must not inherit SC's default amp
		// of 0.1, and with \amp at 0 step() builds no phrases at all.
		//   \amp    master gain (0 = silent, nothing spawns)
		//   \pcs    pitch classes the phrases are built from
		//   \notes  phrase length ceiling
		//   \gapMin/\gapMax  pause between phrases, in rest events
		//   \shrill 1/0 — allow the high twiddle runs
		Pdef(m.ptn).set(\amp, 0, \pcs, idlePcs, \notes, 8,
			\gapMin, 2, \gapMax, 16, \shrill, 1);

		// ---- whipbird: one-shot caller Pdef -------------------------------
		// Pbind holds routing + the fixed forest character only. Everything
		// that varies per state (\amp, \freq, \swoopDelay, \gliss) is set from
		// the state ticks; density comes through the \dur Pfunc reading
		// whipGap, so the ticks own that too (header note 3).
		Pdef(whipPtn,
			Pbind(
				\instrument, \whipbird,
				\out, ob,
				\group, group,          // same Group as the chirps (§5)
				\pan, Pwhite(-1.0, 1.0),
				\dur, Pfunc { whipGap },
				// The SynthDef has a `gate`, so the event schedules gate=0 at
				// \sustain. Hold it well past the ~0.35 s call so the release
				// never truncates the whistle — DetectSilence frees the node.
				\sustain, 2,
				\reverbMix, 0.5,        // fixed character — same in every state
				\reverbTime, 3.0,
				\reverbSize, 0.7
			);
		);

		Pdef(whipPtn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		// Seed silent — the ticks are the only writers of \amp and they run
		// only while the seat's device is enabled, so a stickless seat must
		// not inherit SC's default amp.
		Pdef(whipPtn).set(\amp, 0, \freq, whipBase.midicps);

		// §15 reload guard: a load mid-tuning never sees the \tuning edge.
		if (~roomState == \tuning) { tuneTime = TempoClock.beats; stateName = \tuning };
	};

	// Installed INSIDE ~init so captureResyncHook catches it (BIBLE §5.6).
	// A seek can re-anchor ~beatClock far backward; re-play BOTH Pdefs.
	~onResync = { |idx|
		topEnvironment.use {
			Pdef(m.ptn).stop;
			Pdef(whipPtn).stop;
			queue = []; cur = nil; gapLeft = 0;
			if (group.notNil) { s.bind { group.freeAll } };
			Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
			Pdef(whipPtn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		};
	};
};

//------------------------------------------------------------
// Teardown: stop generating on both patterns synchronously, drop the global
// event type, then free the in-flight voices (chirps ≤ ~1 s, whip calls ~0.35 s
// plus tail) → silent well inside 200 ms.
// Idempotent: refs captured and nil'd before the fork (SOPRANOVOICE shape).
~deinit = ~deinit <> {
	var g = group;
	group = nil;
	muted = true;
	queue = []; cur = nil;
	Pdef(m.ptn).remove;
	Pdef(whipPtn).remove;
	Event.eventTypes.removeAt(eventTypeName);
	if (g.notNil) {
		fork {
			s.bind { g.freeAll };
			s.sync;
			g.free;
		};
	};
};

//------------------------------------------------------------
// State ticks (~33 Hz). The blackbird half only sets lexical vars — its
// pattern reads them. The whipbird half pushes straight onto its Pdef.

~idleNext = { |d, ctx|
	// Whipbird: quiet, sparse calls in the far distance; y tilt walks the call
	// up and down whipIdleNotes (header note 5).
	// Amp range kept at WHIPBIRD's ear-tuned -90..-5. NRT says a \whipbird
	// call peaks ~7 dB below a \labBirdChirp at the same nominal amp, so this
	// range lands the two voices at comparable peaks; trimming it "to sit
	// under the blackbird" buries the whip instead of balancing it.
	var wAmp = m.accelMassFiltered.lincurve(0, 0.5, -90, 5, -1);
	var wN   = m.gyroYFiltered.lincurve(-1.0, 1.0, 0, whipIdleNotes.size, -1)
		.asInteger.clip(0, whipIdleNotes.size - 1);

	// Blackbird: gesture drives the level, as everywhere else. The floor is
	// -90 dB (below ampFloor) so a still stick is genuinely silent — the old
	// -32 dB floor is why it sang continuously no matter what you did.
	var bAmp = m.accelMassFiltered.lincurve(0, 0.5, -90, -20, -1);

	sense.(d);
	stateName = \idle;

	Pdef(m.ptn).set(\amp, bAmp.dbamp);
	Pdef(m.ptn).set(\pcs, idlePcs);
	Pdef(m.ptn).set(\notes, 8, \gapMin, 2, \gapMax, 16, \shrill, 0.2);

	whipGap = 8;
	Pdef(whipPtn).set(\amp, wAmp.dbamp);
	Pdef(whipPtn).set(\freq, (whipBase + whipIdleNotes[wN]).midicps);
	Pdef(whipPtn).set(\swoopDelay, 0.0);
	Pdef(whipPtn).set(\gliss, 0.07);
};

// Tuning: both birds find the A. Blackbird sings single A's whose scoop
// converges to pure over 15 s; whipbird pins to whipTuneMidi so its whistle
// peaks on A5, with the same §16 flat-to-true bend.
~tuningNext = { |d, ctx|
	var t       = (TempoClock.beats - tuneTime).clip(0, 15) / 15;
	var elapsed = TempoClock.beats - tuneTime;
	var bend    = if (elapsed < whipTuneRamp) {
		(elapsed / whipTuneRamp).linlin(0, 1, 0.85, 1.0)   // flat → true
	} { 1.0 };
	var wAmp    = m.accelMassFiltered.lincurve(0, 1.0, -80, -5, -4);

	var bAmp    = m.accelMassFiltered.lincurve(0, 1.0, -90, -20, -4);

	sense.(d);
	stateName = \tuning;

	Pdef(m.ptn).set(\amp, bAmp.dbamp);
	Pdef(m.ptn).set(\pcs, [9]);                          // A only
	Pdef(m.ptn).set(\notes, 2, \gapMin, 6, \gapMax, 20, \shrill, 0);
	// Ramp is applied through the scoop depth of queued notes: late in the
	// ramp every note is a clean A; early, it slides in from below.
	queue.do { |n| n[\g0] = n[\g0].blend(1.0, t); n[\g1] = 1.0 };

	whipGap = 6;
	Pdef(whipPtn).set(\amp, wAmp.dbamp);
	Pdef(whipPtn).set(\freq, whipTuneMidi.midicps * bend);
	Pdef(whipPtn).set(\swoopDelay, 0.06);
	Pdef(whipPtn).set(\gliss, 0.02);
};

// Piece: both take pitch from the score's voice pool (read off ctx, never the
// bare global, pitfall 3). The whipbird thins out as the blackbird gets busy —
// whipGap shortens with energy but never down to phrase density.
~pieceNext = { |d, ctx|
	var pool = (ctx !? { ctx.voicePool }) ? [69];
	var loud = (ctx !? { ctx.loudness }) ? 1.0;
	var wAmp = m.accelMassFiltered.lincurve(0, 1.5, -80, 10, -2);   // WHIPBIRD's range, see ~idleNext
	var wOct = if ((d.sensors.gyroEvent.y / pi.half) > 0.3, { 12 }, { 0 });
	var wPc;

	// Replaces the ampLoDb/ampHiDb-from-accel experiment that used to be here:
	// those set the dB RANGE the engine then re-mapped energy into, so accel
	// was fighting itself. Now it is one plain gesture -> level curve, the
	// same shape every other p-file uses.
	var bAmp = m.accelMassFiltered.lincurve(0, 2.5, -80, -20, -2);

	sense.(d);
	stateName = \piece;
	if (ctx.notNil and: { ctx.voicePool.notNil } and: { ctx.voicePool.notEmpty }) {
		Pdef(m.ptn).set(\pcs,
			ctx.voicePool.collect({ |n| n.asInteger % 12 }).asSet.asArray.sort);
	};
	Pdef(m.ptn).set(\amp, bAmp.dbamp * loud.linlin(0, 1, 0.3, 1.0));
	Pdef(m.ptn).set(\notes, 8, \gapMin, 1, \gapMax, 14, \shrill, 1);

	if (pool.isEmpty, { pool = [69] });
	wPc = pool.choose.asInteger.wrap(0, 11);

	whipGap = energy.clip(restFloor, 1.3).linlin(restFloor, 1.3, 10, 4).round.max(2);
	Pdef(whipPtn).set(\amp, wAmp.dbamp * loud.linlin(0, 1, 0.0, 1.0));
	Pdef(whipPtn).set(\freq, (whipBase + wPc + wOct - 12).midicps);
	Pdef(whipPtn).set(\swoopDelay, rrand(0.01, 0.1));
	Pdef(whipPtn).set(\gliss, rrand(0.01, 0.08));
};

// Curtain: both recede — the piece is over, decay, don't cut. Blackbird drops
// to sparse single calls; whipbird reads rotation only and holds its pitch.
~curtainNext = { |d, ctx|
	var wAmp = (m.rrateMassFiltered * 2.0).lincurve(0, 1.0, -80, -35, -4);

	var bAmp = (m.rrateMassFiltered * 2.0).lincurve(0, 1.0, -90, -24, -4);

	sense.(d);
	stateName = \curtain;

	Pdef(m.ptn).set(\amp, bAmp.dbamp);
	Pdef(m.ptn).set(\notes, 2, \gapMin, 10, \gapMax, 30, \shrill, 0);

	whipGap = 14;
	Pdef(whipPtn).set(\amp, wAmp.dbamp);
};

//------------------------------------------------------------
~onRoomState = { |ctx|
	switch(ctx.state,
		\silent,  {
			muted = true;
			queue = []; cur = nil;
			// No \silent tick runs, so nothing re-raises these.
			Pdef(m.ptn).set(\amp, 0);
			Pdef(whipPtn).set(\amp, 0);
			if (group.notNil) { s.bind { group.freeAll } };   // hard mute, < 200 ms
		},
		\tuning,  { muted = false; tuneTime = TempoClock.beats; queue = []; },
		\curtain, { muted = false; queue = queue.keep(2); },
		\idle,    { muted = false; },
		\piece,   { muted = false; }
	);
};

//------------------------------------------------------------
// Beat hooks: both patterns already ride ~beatClock; pitch comes from
// ctx.voicePool in ~pieceNext. ~onHalf refreshes the pool immediately on a
// half-bar change so a phrase built right after the change is in the new harmony.
~onHalf = { |ctx|
	if (ctx.notNil and: { ctx.voicePool.notNil } and: { ctx.voicePool.notEmpty }
		and: { stateName == \piece }) {
		Pdef(m.ptn).set(\pcs,
			ctx.voicePool.collect({ |n| n.asInteger % 12 }).asSet.asArray.sort);
	};
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 2;
~plot = { |d, p|
	[energy, twist, m.gyroYFiltered];
};
