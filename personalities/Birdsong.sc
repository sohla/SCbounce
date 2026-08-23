/*
gestures:    [shake, tilt, flick]
description: A blackbird — shake to make it sing little fluty phrases, tilt up for higher brighter notes, flick your wrist for a fast shrill twiddle
internals:   Pdef on ~beatClock firing short self-freeing \labBirdChirp synths (pure-tone + scoop glide + light FM, per-note FreeVerb2) into a dedicated Group. A language-side phrase queue models blackbird song: 3–8 fluty motif notes (1.4–2.6 kHz, scale-walked) then an optional high shrill run (4–8 kHz, fast FM), separated by energy-scaled pauses. No samples. Core UGens only. Proposed ~cotfPatchTrims 1.0 (0 dB) — verify at bench.
sound:       fluty whistling bird phrases with scooped slides, ending in quick bright twiddles; small room reverb; still stick is silent
pitch:       idle: C major pentatonic (+ occasional 7th) walked stepwise in the 1.4–2.6 kHz blackbird register, gyroYFiltered tilt shifts an octave up/down with hysteresis; tuning: A only (93/105) with a scoop that converges to pure A over 15 s; piece: pitch classes of ctx.voicePool placed in the same register; shrill runs sit +12..+19 above, clipped at MIDI 114
rhythm:      beat-quantised phrases on ~beatClock (note lengths scaled from clock tempo to ~120–360 ms); phrase length and pause length follow accelMassFiltered energy — gentle shake = single calls, hard shake = continuous song; rrateMassFiltered flick inserts a shrill run; curtain = sparse single calls
instruments: [oliveBird]
prints:      [oliveBird, whiteWing, thinViolin]
seats:       [3, 4, 5]
affinity:    [bird, birds, birdsong, blackbird, robin, nightingale, chirp, chirping, tweet, whistle, dawn, morning, garden, forest, sky, feathers, wings, flutter, spring, nature]
register:    [traditional]
family:      bird
*/

// -------------------------------------------------------------------------
// Birdsong — a synthesised blackbird.
//
// cotf: Ciaran's TEST patch for widening the personality pool (2026-08-23).
// Auditioned via the COTF Patch Lab (promoted from LAB_Birdsong); kept OUT of
// the super-seats auto-pick (admin disabled) and only ever hand-assigned to a
// seat for a consenting audition group. Not a Steph patch — please leave as is.
//
// Why a blackbird: Turdus merula song is the most "musical" of the common
// European songbirds — phrases of 2–3 s made of a few low, pure, fluty
// motif notes (below ~3.5 kHz, slow slides, loud) followed by a higher
// shrill "twiddle" (4–8 kHz, fast, quieter), then a pause of a few seconds.
// Each bird has 6–30 motifs and walks between them stepwise. That structure
// is exactly what this patch models: a motif queue + a shrill tail, pauses
// scaled by gesture energy.
//
// Shape: JUPITERSHARP's (Pdef spawns synths → dedicated Group, ~onResync
// re-anchor, ordered ~deinit). No buffers, so no `loading` flag is needed.
// Read audio/airkit-lab/BIBLE.md before changing hook bodies.
// -------------------------------------------------------------------------

var m  = ~model;
var ob = ~outBus ? 0; // read, never assign — captured NOW, ~init runs under topEnvironment.use

var group;           // dedicated Group for every chirp synth this personality spawns
var clock;           // ~beatClock, captured inside ~init under topEnvironment.use
var eventTypeName = (\labBird_ ++ m.ptn).asSymbol;   // env-unique custom event type

// ---- gesture state (written at tick rate, read by the pattern) ----------
var energy    = 0;    // accelMassFiltered — sustained shake energy
var prevEnergy = 0;
var twist     = 0;    // rrateMassFiltered — wrist flick
var tiltOct   = 0;    // -12 / 0 / +12 from gyroYFiltered, with hysteresis
var restFloor = 0.25; // a still stick reads ~0.2 (gravity) — below this: silence
var urgent    = false; // a fresh shake edge → start a phrase on the very next event
var lastFlick = 0;    // TempoClock.beats of the last flick-triggered shrill run
var muted     = false; // \silent handled here (there is no ~silentNext)

// ---- musical state -------------------------------------------------------
var stateName = \idle;
var pcs       = [0, 2, 4, 7, 9];         // pitch classes in use (idle default: C maj pentatonic)
var idlePcs   = [0, 2, 4, 7, 9];
var ampLoDb   = -32, ampHiDb = -12;      // energy → dB range, per state
var gapMin    = 2, gapMax = 16;          // pause length (in rest events of 0.5 beat), per state
var maxNotes  = 8;                       // phrase length ceiling, per state
var allowShrill = true;
var tuneTime  = 0;                       // TempoClock.beats when tuning began (§15 reload guard)

// ---- phrase engine ---------------------------------------------------------
var queue   = [];     // pending note Events — ALWAYS index with [\key] (pitfall 1)
var cur     = nil;    // the note the current pattern event is playing (nil = rest)
var gapLeft = 0;      // rest events remaining before the next phrase may start
var lastMidi = 95;    // where the previous phrase left off — the next one walks on from here

var midiLo = 86, midiHi = 100;   // fluty register ≈ 1.2–2.6 kHz before tilt shift
// Forward declarations — SC requires every `var` before the first statement; the
// helper functions below are assigned later.
var freqComp, scaleNotes, unitBeats, mkNote, shrillRun, buildPhrase, gapFor, step, sense;

// Smoothing knobs. Energy rises fast and falls in ~0.3 s so a stopped stick
// goes quiet quickly; flick decays fast so one flick = one twiddle.
m.accelMassFilteredAttack = 0.95;
m.accelMassFilteredDecay  = 0.1;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay  = 0.2;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay  = 0.7;

//------------------------------------------------------------
// One chirp. Pure-ish tone (sine + a little 2nd/3rd harmonic + a breath of
// band-passed noise), a three-segment pitch glide (scoop in → hold → tail
// off), light FM "trill" (slow/shallow for fluty notes, fast/deep for the
// shrill twiddle), per-note small-room reverb, self-freeing.
// Name is lab-scoped — never one of the six show SynthDef names.
SynthDef(\labBirdChirp, { |out=0, freq=2000, g0=0.9, g1=1.0, secs=0.15, amp=0.1, pan=0,
	trill=0.005, trate=7, bright=0.15, air=0.04, verb=0.22|
	var life  = secs + 0.6;   // amp env + reverb tail, then free
	var fenv  = EnvGen.kr(Env([g0, 1, 1, g1], [0.22, 0.38, 0.4] * secs, \exp));
	var aenv  = EnvGen.kr(Env([0, 1, 0.85, 0], [0.12, 0.55, 0.33] * secs, [-2, 0, -3]));
	var f     = freq * fenv * (1 + (trill * SinOsc.kr(trate, Rand(0, 2pi))));
	var tone  = SinOsc.ar(f) + (SinOsc.ar(f * 2) * bright * 0.6) + (SinOsc.ar(f * 3) * bright * 0.2);
	var breath = BPF.ar(WhiteNoise.ar, f, 0.08) * air * 4;
	var sig   = (tone + breath) * aenv * amp;
	var st    = Pan2.ar(sig, pan);
	var wet   = FreeVerb2.ar(st[0], st[1], verb, 0.55, 0.6);
	Line.kr(0, 1, life, doneAction: Done.freeSelf);
	Out.ar(out, wet);
}).add;

//------------------------------------------------------------
// Helpers (lexical — no ~globals inside, so they are safe from any env).

// Loudness compensation: 2 kHz = unity, higher notes quieter, lower a touch louder.
freqComp = { |freq| (2000 / freq).sqrt.clip(0.45, 1.3) };

// Scale notes available right now, sorted, inside the (tilt-shifted) fluty register.
scaleNotes = {
	var lo = midiLo + tiltOct, hi = midiHi + tiltOct;
	var notes = [];
	(lo..hi).do { |n| if (pcs.includes(n % 12)) { notes = notes.add(n) } };
	if (notes.isEmpty) { notes = [lo + 9] }; // never empty
	notes
};

// Note length unit in clock beats: ~120 ms at the current tempo, rounded to
// a quarter-beat so phrases still land on the grid whatever the tempo is.
unitBeats = {
	var tempo = clock !? { |c| c.tempo } ? 4;
	((0.12 * tempo) / 0.25).round.max(1) * 0.25
};

mkNote = { |midi, beats, g0, g1, trill, trate, bright, air, ampScale, pan|
	(note: midi, beats: beats, g0: g0, g1: g1, trill: trill, trate: trate,
		bright: bright, air: air, ampScale: ampScale, pan: pan)
};

// The shrill run: 4–8 very fast alternating up/down sweeps, +12..+19 above
// the motif, quieter, with fast FM — the "twiddle" that ends a blackbird phrase.
shrillRun = { |fromMidi, k|
	var u = unitBeats.();
	var sb = (u * 0.5).max(0.25);
	var base = (fromMidi + 12 + 5.rand).clip(100, 112);
	k.collect { |i|
		var up = i.even;
		var midi = (base + (if (up) { rrand(1, 4) } { rrand(-4, -1) })).clip(98, 114);
		mkNote.(midi, sb,
			if (up) { rrand(0.72, 0.85) } { rrand(1.18, 1.35) },
			if (up) { rrand(1.04, 1.15) } { rrand(0.86, 0.95) },
			rrand(0.035, 0.07), rrand(55, 110), 0.35, 0.12,
			0.45 * (1 - (i * 0.04)), rrand(-0.5, 0.5))
	}
};

// A blackbird phrase: a few fluty motif notes walked stepwise (with a leap
// now and then), scooped in, some held long; then maybe the shrill tail.
buildPhrase = {
	var notes = scaleNotes.();
	var u = unitBeats.();
	var n, idx, out = [];
	var e = energy.clip(restFloor, 1.5);
	n = (2 + (e.linlin(restFloor, 1.5, 0, 5)).round + 2.rand).clip(1, maxNotes).asInteger;
	idx = notes.indexOfGreaterThan(lastMidi - 1) ? (notes.size - 1);
	idx = (idx + [-1, 0, 0, 1].choose).clip(0, notes.size - 1);
	n.do { |i|
		var step = [-3, -2, -1, -1, 0, 1, 1, 2, 3].choose;
		var beats, long;
		if (i == 0) { step = [-2, 0, 1, 2].choose };
		idx = if (notes.size > 1) { (idx + step).fold(0, notes.size - 1) } { 0 };
		long = (i == (n - 1)) and: { 0.45.coin };
		beats = if (long) { u * [2, 3].choose } { u * [1, 1, 1, 1.5, 2].choose };
		out = out.add(mkNote.(notes[idx], beats,
			[0.82, 0.88, 0.94, 1.0, 1.0, 1.08].choose,
			[1.0, 1.0, 1.0, 0.96, 1.04].choose,
			if (long) { rrand(0.008, 0.014) } { rrand(0.003, 0.007) },
			rrand(5.5, 9), rrand(0.1, 0.2), rrand(0.03, 0.06),
			1.0, rrand(-0.3, 0.3)));
		lastMidi = notes[idx];
	};
	if (allowShrill and: { (twist > 0.35) or: { 0.3.coin } }) {
		out = out ++ shrillRun.(lastMidi, rrand(4, 8));
	};
	out
};

gapFor = {
	energy.clip(restFloor, 1.3).linlin(restFloor, 1.3, gapMax, gapMin).round.asInteger
};

// Called from the \dur Pfunc on every pattern event: decides whether this
// event is a note (sets `cur`) or a rest (cur = nil), returns its length.
step = {
	var beats = 0.5;
	cur = nil;
	if (muted.not and: { group.notNil }) {
		if (queue.isEmpty) {
			if ((energy > restFloor) and: { urgent or: { gapLeft <= 0 } }) {
				queue = buildPhrase.();
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
			if (queue.isEmpty) { gapLeft = gapFor.() };
		};
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
	// A wrist flick mid-phrase drops a shrill twiddle in right now (refractory 1.2 s).
	if (allowShrill and: { twist > 0.8 } and: { TempoClock.beats > (lastFlick + 1.2) }
		and: { energy > restFloor }) {
		lastFlick = TempoClock.beats;
		queue = shrillRun.(lastMidi, rrand(3, 6)) ++ queue;
	};
};

//------------------------------------------------------------
~init = ~init <> {
	topEnvironment.use {
		clock = ~beatClock;
		group = Group.new;

		Event.addEventType(eventTypeName, { |e|
			var n = cur;
			if (n.notNil and: { group.notNil } and: { muted.not }) {
				var tempo = clock !? { |c| c.tempo } ? 4;
				var midi  = n[\note];
				var freq  = midi.midicps;
				var db    = energy.clip(restFloor, 1.5).lincurve(restFloor, 1.5, ampLoDb, ampHiDb, -1.5);
				~instrument = \labBirdChirp;
				~out    = ob;
				~group  = group;
				~freq   = freq;
				~g0     = n[\g0];
				~g1     = n[\g1];
				~secs   = (n[\beats] / tempo).clip(0.035, 0.6);
				~amp    = db.dbamp * n[\ampScale] * freqComp.(freq);
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
			\dur,  Pfunc { step.() }
		));
		Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);

		// §15 reload guard: a load mid-tuning never sees the \tuning edge.
		if (~roomState == \tuning) { tuneTime = TempoClock.beats; stateName = \tuning };
	};

	// Installed INSIDE ~init so captureResyncHook catches it (BIBLE §5.6).
	// A seek can re-anchor ~beatClock far backward; re-play on the bar quant.
	~onResync = { |idx|
		topEnvironment.use {
			Pdef(m.ptn).stop;
			queue = []; cur = nil; gapLeft = 0;
			if (group.notNil) { s.bind { group.freeAll } };
			Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		};
	};
};

//------------------------------------------------------------
// Teardown: stop generating notes synchronously, drop the global event type,
// then free the in-flight chirps (all ≤ ~1 s long) → silent well inside 200 ms.
// Idempotent: refs captured and nil'd before the fork (SOPRANOVOICE shape).
~deinit = ~deinit <> {
	var g = group;
	group = nil;
	muted = true;
	queue = []; cur = nil;
	Pdef(m.ptn).remove;
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
// State ticks (~33 Hz). Only set lexical vars here — the pattern reads them.
~idleNext = { |d, ctx|
	sense.(d);
	stateName = \idle;
	pcs = idlePcs;
	ampLoDb = -32; ampHiDb = -12;
	gapMin = 2; gapMax = 16; maxNotes = 8; allowShrill = true;
};

// Tuning: the bird finds the A. Single calls on A, scoop converging to pure
// A over 15 s from tuneTime, sparse, quieter, no twiddles.
~tuningNext = { |d, ctx|
	var t = (TempoClock.beats - tuneTime).clip(0, 15) / 15;
	sense.(d);
	stateName = \tuning;
	pcs = [9];
	ampLoDb = -34; ampHiDb = -18;
	gapMin = 6; gapMax = 20; maxNotes = 2; allowShrill = false;
	// Ramp is applied through the scoop depth of queued notes: late in the
	// ramp every note is a clean A; early, it slides in from below.
	queue.do { |n| n[\g0] = n[\g0].blend(1.0, t); n[\g1] = 1.0 };
};

// Piece: pitch classes from the score's voice pool, fuller dynamics,
// twiddles allowed. Beat hooks are live but this patch reads the pool here
// (ctx.voicePool — never the bare global, pitfall 3).
~pieceNext = { |d, ctx|
	sense.(d);
	stateName = \piece;
	if (ctx.notNil and: { ctx.voicePool.notNil } and: { ctx.voicePool.notEmpty }) {
		pcs = ctx.voicePool.collect({ |n| n.asInteger % 12 }).asSet.asArray.sort;
	};
	ampLoDb = -30; ampHiDb = -8;
	gapMin = 1; gapMax = 14; maxNotes = 8; allowShrill = true;
};

// Curtain: the piece is over — decay, don't cut. Sparse single calls, low
// ceiling, no twiddles, no new long phrases.
~curtainNext = { |d, ctx|
	sense.(d);
	stateName = \curtain;
	ampLoDb = -40; ampHiDb = -24;
	gapMin = 10; gapMax = 30; maxNotes = 2; allowShrill = false;
};

//------------------------------------------------------------
~onRoomState = { |ctx|
	switch(ctx.state,
		\silent,  {
			muted = true;
			queue = []; cur = nil;
			if (group.notNil) { s.bind { group.freeAll } };   // hard mute, < 200 ms
		},
		\tuning,  { muted = false; tuneTime = TempoClock.beats; queue = []; },
		\curtain, { muted = false; queue = queue.keep(2); },
		\idle,    { muted = false; },
		\piece,   { muted = false; }
	);
};

//------------------------------------------------------------
// Beat hooks: the pattern already rides ~beatClock; pitch comes from
// ctx.voicePool in ~pieceNext. ~onHalf refreshes the pool immediately on a
// half-bar change so a phrase built right after the change is in the new harmony.
~onHalf = { |ctx|
	if (ctx.notNil and: { ctx.voicePool.notNil } and: { ctx.voicePool.notEmpty }
		and: { stateName == \piece }) {
		pcs = ctx.voicePool.collect({ |n| n.asInteger % 12 }).asSet.asArray.sort;
	};
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 2;
~plot = { |d, p|
	[energy, twist, m.gyroYFiltered];
};
