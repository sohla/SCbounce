/*
gestures:    [shake, tilt]
description: An eastern whipbird — a noise crack followed by a rising whistle, one call per pattern event; move more for louder, closer calls and tilt to move the call up and down
internals:   Engine C (concert_p_files.md §2B / authoring §2 C) — a Pdef on ~beatClock firing self-freeing \whipbird synths into a dedicated Group. No samples. The SynthDef is Steph's, copied verbatim from synths/whipBird.sc (whip crack = filtered noise burst, whistle = SinOsc on a 200→400→380 Hz Env, FreeVerb2 forest tail, DetectSilence frees the node). Pitch is a RATIO on that whole swoop (\pitchRand), not a note — see `pitch:`. Patch trim defaults to 1.0 (0 dB, no ~cotfPatchTrims entry) — verify at bench.
sound:       forest whipbird calls — a short cracking whip then a rising whistle, wide random pan, long reverb tail; quiet and sparse at rest, denser and louder with motion
pitch:       \pitchRand transposes the whole swoop, so the personality speaks in semitone offsets above the raw call (peak 400 Hz), not in absolute notes. Everything sits baseShift = +12 above that. Idle: y tilt picks from idleNotes [0,2,4,7,9,12,14,16]. Tuning: pinned to tunePitch 2.2 (peak ≈ A5, 880 Hz) with the §16 ramp — 0.85× flat converging to true over 15 s. Piece: ctx.voicePool.choose wrapped to a pitch class, +12 when y tilt is up. Curtain holds its last pitch.
rhythm:      one call per \dur tick on ~beatClock. Idle and piece pick dur by amp threshold (0.5 / 1 / 2); tuning is sparse (2–5, from accel); curtain holds long at 3.
instruments: [oliveBird]
prints:      [oliveBird, whiteWing, greenHolder]
seats:       [3, 4, 5]
affinity:    [whipbird, bird, birds, forest, bush, jungle, rainforest, australia, crack, whip, whistle, nature, wild, dawn]
register:    [wild]
family:      bird
*/

// -------------------------------------------------------------------------
// WHIPBIRD — the whipbird synth wired up as a COTF personality.
//
// Source of the voice: synths/whipBird.sc. The SynthDef below is that file's
// SynthDef UNCHANGED — do not retune it here; if the sound needs to change it
// changes in synths/whipBird.sc and gets copied back over.
//
// Shape: JUPITERSHARP's (Pdef → dedicated Group, ~onResync re-anchor, ordered
// ~deinit), minus the sample loading — there are no buffers, so there is no
// `loading` flag, no s.sync barrier and no custom event type.
//
// Deliberately simple first pass: the Pdef runs in every state, state ticks own
// every mapping (§22), no one-shots, no phrase logic.
// -------------------------------------------------------------------------

var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~outBus lives in d.env, and the Pbind
                      // is built inside topEnvironment.use{} where it's nil

var group;            // dedicated Group for the per-call synths (§5)
var lastTime = 0;     // dur-change throttle
var tuneTime = 0;     // TempoClock.beats when \tuning began (§15 ramp anchor)

// ---- live config knobs (the file IS the config — edit, save, hear it) ----
var baseShift  = 12;                        // semitones above the raw call for every state
var idleNotes  = [0, 2, 4, 7, 9, 12, 14, 16];  // idle offsets, selected by y tilt
var tunePitch  = 2.2;                       // \pitchRand pinning the whistle peak to ≈ A5 (880 Hz)
var tuneRamp   = 15.0;                      // seconds for the tuning bend to reach true (§16)

//------------------------------------------------------------
// Filter tuning — accel rises fast and falls quickly so a stopped stick
// goes quiet within a call or two; rotation is slower (curtain reads it).
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.15;
m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay  = 0.6;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
// VERBATIM from synths/whipBird.sc — do not edit here.
SynthDef(\whipbird, {
    |out=0, pan=0, amp=0.3, gate=1, swoopDelay=0.03, gliss=0.01,pitchRand=1,
        reverbMix=0.3, reverbTime=2.0, reverbSize=0.8|

    var whipEnv, whistleEnv, whipOsc, whistleOsc, sig, mainEnv;
    var numCombs = 7;
    var numAllpass = 4;
    var room = reverbSize.clip(0.1, 0.9);

    // Main envelope for the whole sound
    mainEnv = EnvGen.kr(
        Env.asr(0.01, 1, 0.5),
        gate
		// doneAction:2
    );

    // Whip crack envelope
    whipEnv = EnvGen.ar(
        Env.perc(0.001, 0.03, curve: -8),
        gate
    );

    // Rising whistle envelope with adjustable delay
    whistleEnv = EnvGen.ar(
        Env(
            [0, 0, 1, 0],
            [gliss, 0.15, 0.2],
            curve: [0, 2, -4]
        ),
        gate
    );

    // Whip crack sound - enhanced with slight resonance
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

    // Rising whistle with more character
    whistleOsc = SinOsc.ar(
        freq: Env(
			[200, 200, 400, 380] * pitchRand,
            [swoopDelay, 0.15, 0.15],
            [\sine, \sine, -3]
        ).kr
    ) * whistleEnv;

    // Combine both sounds
    sig = (whipOsc * whipEnv * 0.2) + (whistleOsc * 0.2);
	sig = Pan2.ar(sig * 0.5, pan);
    // Forest-like reverb using feedback delay network
    sig = FreeVerb2.ar(
		sig[0], sig[1],
        mix: reverbMix,
        room: room,
        damp: 0.3
    );

    // Additional early reflections for forest feel
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
~init = ~init <> {
	topEnvironment.use {
		group = Group.new;

		// Pbind holds routing + the fixed forest character only. Everything
		// that varies per state (\amp, \dur, \pitchRand, \swoopDelay, \gliss)
		// is set from the state ticks — §22.
		Pdef(m.ptn,
			Pbind(
				\instrument, \whipbird,
				\out, ob,
				\group, group,          // route every call into our group (§5)
				\pan, Pwhite(-1.0, 1.0),
				// The SynthDef has a `gate`, so the event schedules gate=0 at
				// \sustain. Hold it well past the ~0.35 s call so the release
				// never truncates the whistle — DetectSilence frees the node.
				\sustain, 2,
				\reverbMix, 0.5,        // fixed character — same in every state
				\reverbTime, 3.0,
				\reverbSize, 0.7
			);
		);

		Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		Pdef(m.ptn).set(\amp, 0, \dur, 2, \pitchRand, baseShift.midiratio);

		// §15 reload guard: ~onRoomState only fires on a state CHANGE, so a
		// load that lands mid-tuning would never anchor the ramp.
		if (~roomState == \tuning) { tuneTime = TempoClock.beats };
	};

	// Installed OUTSIDE topEnvironment.use so it lands in d.env — a global
	// slot would be clobbered by the next personality load on any device (§6).
	~onResync = { |idx|
		topEnvironment.use {
			Pdef(m.ptn).stop;
			s.bind { group.freeAll };
			Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		};
	};
};

//------------------------------------------------------------
// No buffers and no custom event type, so teardown is just: stop generating
// calls, then free the in-flight ones. notNil-guarded — ~deinit can fire twice
// on the same env (§5).
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;

	fork {
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;                    // wait for /g_freeAll to complete
			group.free;
			group = nil;
		};
	};
};

//------------------------------------------------------------
// State-gated ticks (~30 Hz). These own every mapping.

// Idle: quiet, sparse calls in the far distance; y tilt walks the call up and
// down idleNotes. Palette: `low` (§17).
~idleNext = {|d, ctx|
	var amp = m.accelMassFiltered.lincurve(0, 0.5, -90, -25, -1);
	var n   = m.gyroYFiltered.lincurve(-1.0, 1.0, 0, idleNotes.size, -1)
		.asInteger.clip(0, idleNotes.size - 1);

	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\pitchRand, (baseShift + idleNotes[n]).midiratio);
	Pdef(m.ptn).set(\swoopDelay, 0.01);
	Pdef(m.ptn).set(\gliss, 0.01);

	case(
		{ amp > -30 }, {
			if (TempoClock.beats > (lastTime + 0.5), {
				Pdef(m.ptn).set(\dur, 1);
				lastTime = TempoClock.beats;
			})},
		{ Pdef(m.ptn).set(\dur, 2) });
};

// Tuning: the bird finds the A. Pinned to tunePitch, sparse, quiet, with a
// longer swoop; the §16 ramp starts it flat and converges over tuneRamp secs.
~tuningNext = {|d, ctx|
	var amp     = m.accelMassFiltered.lincurve(0, 1.0, -80, -30, -4);
	var dur     = m.accelMassFiltered.lincurve(0, 2.0, 5.0, 2.0, -4);
	var elapsed = TempoClock.beats - tuneTime;
	var bend    = if (elapsed < tuneRamp) {
		(elapsed / tuneRamp).linlin(0, 1, 0.85, 1.0)   // flat → true
	} { 1.0 };

	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\pitchRand, tunePitch * bend);
	Pdef(m.ptn).set(\swoopDelay, 0.06);
	Pdef(m.ptn).set(\gliss, 0.02);
	Pdef(m.ptn).set(\dur, dur);
};

// Piece: calls take their pitch class from the score's voice pool (read off
// ctx, never the bare global), y tilt lifts them an octave, amp scales with
// the score's loudness. Palette: `expressive` (§17).
~pieceNext = {|d, ctx|
	var pool = (ctx !? { ctx.voicePool }) ? [69];
	var loud = (ctx !? { ctx.loudness }) ? 1.0;
	var amp  = m.accelMassFiltered.lincurve(0, 0.5, -80, -2, -2);
	var oct  = if ((d.sensors.gyroEvent.y / pi.half) > 0.3, { 12 }, { 0 });
	var pc;

	if (pool.isEmpty, { pool = [69] });
	pc = pool.choose.asInteger.wrap(0, 11);

	Pdef(m.ptn).set(\amp, amp.dbamp * loud.linlin(0, 1, 0.3, 1.0));
	Pdef(m.ptn).set(\pitchRand, (baseShift + pc + oct).midiratio);
	Pdef(m.ptn).set(\swoopDelay, 0.003);
	Pdef(m.ptn).set(\gliss, 0.003);

	case(
		{ amp > -20 }, {
			if (TempoClock.beats > (lastTime + 0.5), {
				Pdef(m.ptn).set(\dur, 0.5);
				lastTime = TempoClock.beats;
			})},
		{ amp > -35 }, {
			if (TempoClock.beats > (lastTime + 0.5), {
				Pdef(m.ptn).set(\dur, 1);
				lastTime = TempoClock.beats;
			})},
		{ Pdef(m.ptn).set(\dur, 2) });
};

// Curtain: the bird recedes — rotation only, sparse, holding its last pitch.
// Palette: `faded` (§17).
~curtainNext = {|d, ctx|
	var amp = (m.rrateMassFiltered * 2.0).lincurve(0, 1.0, -80, -35, -4);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\dur, 3);
};

//------------------------------------------------------------
// Room-state routing. The Pdef stays playing in every state; per-state amp is
// set by the ticks above, so this is only one-shot entry logic.
~onRoomState = {|ctx|
	switch(ctx.state,
		\idle,    { },
		\tuning,  { tuneTime = TempoClock.beats },
		\piece,   { },
		\curtain, { },
		\silent,  { Pdef(m.ptn).set(\amp, 0) }
	);
};

//------------------------------------------------------------
// Beat-aligned hooks — nothing beat-locked in this first pass; the Pdef
// already rides ~beatClock and pitch comes from ctx in ~pieceNext.
~onTick    = {|ctx| };
~onHalf    = {|ctx| };
~onBeat    = {|ctx| };
~onBar     = {|ctx| };
~onPhrase  = {|ctx| };
~onSection = {|ctx| };
~onChord   = {|ctx| };
~onKey     = {|ctx| };
~onScale   = {|ctx| };

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 2;
~plot = { |d, p|
	[m.accelMassFiltered, m.gyroYFiltered];
};
