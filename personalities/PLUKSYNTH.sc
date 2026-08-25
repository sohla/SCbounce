/*
gestures:    [beat, shake, tilt, twist]
description: A plucked string — strike it to make it ring, roll your wrist to open the string up from muted thud to bright wire, and twist to make it buzz
internals:   Engine B. Pdef on ~beatClock firing per-note self-freeing \plukString synths into a dedicated Group. The voice is \pluckedString from synths/reichString.sc: a Karplus-ish CombL tuned to 1/freq, excited by a phase-thrashed Impulse, plus 2nd/3rd sine partials and a detuned LFTri pair, all through one RLPF. Every expressive arg is mapped from a different gesture — roll to filterFreq, twist to filterRes, accel to decay, z-tilt to phs — so the five controls do not fight for the same sensor. No samples, no buffers. Core UGens only.
sound:       plucked wire — soft dark thuds when still, bright ringing plucks when struck hard; twisting adds a resonant buzz; long decays overlap into a Reich-ish shimmer
pitch:       set as \note + \root + \octave (never \freq, which would disable \octave). idle: pentatonic offsets [0,2,4,7,9,12] in \note above idleBase 57 (= root 9, octave 4), chosen by z tilt; tuning: converges on A (root 9, octave 5) via a §16 ramp riding \note over 15 s, entering ~4 semitones sharp; piece: pitch class from ctx.voicePool in \root, octave 3–6 from y tilt in \octave; curtain: holds its last pitch and just fades
rhythm:      one note per \dur ~beatClock tick. Idle 2 → 1 by accel; tuning sparse 1.0–1.7 random; piece picks [2, 1, 0.5] by accel; curtain fixed 2. \decay is gesture-driven, so hard playing also lengthens the ring and thickens the overlap
instruments: [thinViolin]
prints:      [thinViolin, compactViolin, greenHolder, whiteWing, boneRod]
seats:       [2, 3, 4]
affinity:    [pluck, plucked, string, strings, guitar, harpsichord, koto, banjo, zither, wire, harp, minimal, reich, pulse, repetitive]
register:    [traditional]
family:      pluck
*/

// -------------------------------------------------------------------------
// PLUKSYNTH — the composer's \pluckedString, made concert-safe.
//
// The voice is lifted from synths/reichString.sc. Three changes were needed
// to run it on a seat, and nothing else about the DSP was touched:
//
//  1. NAME. It is \plukString here, NOT \pluckedString. reichString.sc is a
//     bench file that also does `SynthDef(\pluckedString, ...).add`, and its
//     version hardcodes Out.ar(0, ...). Evaluating that file in the same
//     interpreter would silently replace this seat's voice with one that
//     writes to bus 0 — the seat would vanish from its own bus and bleed
//     into the composer's monitor instead. Same reasoning as Birdsong's
//     \labBirdChirp.
//
//  2. OUT BUS. `Out.ar(0, ...)` -> `Out.ar(out, ...)`. Bus 0 is hardcoded in
//     the bench version; COTF routes per seat through ~outBus.
//
//  3. CHANNEL COUNT. `tone = LFTri.ar([freq, freq * 1.007], 0, 1)` is TWO
//     channels, so `sig` became two channels, and `Pan2.ar(sig, pan)` on a
//     stereo input expands to FOUR — writing over buses out..out+3. The
//     detuning is the point (it is what beats and shimmers), so the pair is
//     kept and summed to mono before Pan2. Summing preserves the beating —
//     it is amplitude interference, not a stereo effect.
//
// One safety fix with no effect on the sound: CombL's maxdelaytime is now a
// constant 1/20 s instead of `delay`. maxdelaytime only sizes the delay
// buffer; sizing it from the freq control means a note is fine at its
// creation freq but clamps (i.e. plays the WRONG PITCH) if freq is ever
// lowered on a running synth. 50 ms of buffer costs nothing and removes the
// whole class of bug.
//
// `exciteRate` is the only added arg — reichString had Impulse.ar(1, ...)
// hardcoded. Default 1 is identical to the original; raising it thickens
// the excitation. See the note on `phs` in the SynthDef.
//
// Read code3.0/concert_p_files.md before changing hook bodies. Section
// numbers below refer to it.
// -------------------------------------------------------------------------

var m  = ~model;
var ob = ~outBus ? 0;   // capture NOW — ~init runs under topEnvironment.use,
                        // where a bare ~outBus reads nil (§7)

var group;              // dedicated Group for every note this personality spawns (§5)
var tuneTime = 0;       // TempoClock.beats when tuning began (§15 reload guard)

// Idle scale — pentatonic + octave, offsets above idleBase. Live config
// knob: edit and save, the file reloads whole (§1).
var idleNotes = [0, 2, 4, 7, 9, 12];
var idleBase  = 57;     // A3 — low enough that long decays don't get muddy

//------------------------------------------------------------
// Filter tuning (§9). Accel rises fast and falls quickly so a put-down
// stick stops ringing hard; rrate is slower so the buzz swells and eases.
m.accelMassFilteredAttack = 0.85;
m.accelMassFilteredDecay  = 0.25;
m.rrateMassFilteredAttack = 0.97;
m.rrateMassFilteredDecay  = 0.5;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
// The voice — synths/reichString.sc's \pluckedString, per the header notes.
//
// `decay` does double duty and that is deliberate: it is both the CombL
// decay time (how long the string rings) and the Env.perc decay (how long
// the note lasts). One gesture therefore lengthens the ring AND the note.
// Watch the polyphony that implies — decay 1.8 against dur 0.5 is ~4
// overlapping notes per line (§12).
//
// `phs` drives a SinOsc whose output (50..600) is fed to Impulse's PHASE.
// That is far outside the usual 0..1, so the phase wraps continuously and
// the impulse fires densely rather than once per second — which is why the
// comb sings at all. Low phs = coarse, buzzy; high phs = finer, brighter.
SynthDef(\plukString, { |out = 0, freq = 440, amp = 0.3, pan = 0, gate = 1,
	attack = 0.001, decay = 1, filterFreq = 2000, filterRes = 0.5,
	phs = 10, exciteRate = 1|
	var sig, exciter, delay, env, tone;

	exciter = Impulse.ar(exciteRate, SinOsc.ar(phs).range(50, 600));
	delay   = freq.reciprocal;
	sig     = CombL.ar(exciter, 1/20, delay, decay);   // maxdelaytime fixed — see header
	sig     = sig + (SinOsc.ar(freq * 2) * 0.09);
	sig     = sig + (SinOsc.ar(freq * 3) * 0.06);
	sig     = RLPF.ar(sig, filterFreq, filterRes);
	env     = EnvGen.kr(Env.perc(attack, decay), gate, doneAction: Done.freeSelf);

	// The detuned pair, summed to mono so Pan2 stays stereo (header note 3).
	tone    = LFTri.ar([freq, freq * 1.007], 0, 1).sum * 0.5;

	sig     = (sig + tone) * env * amp;
	sig     = LeakDC.ar(sig);
	Out.ar(out, Pan2.ar(sig, pan));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	topEnvironment.use {
		group = Group.new;

		// Pbind holds routing only — every mapping lives in a state tick (§22).
		// \pan is the one allowed exception: a pure random pattern, no gesture.
		Pdef(m.ptn,
			Pbind(
				\instrument, \plukString,
				\out,   ob,
				\group, group,
				\pan,   Pwhite(-0.6, 0.6),
				\args,  #[]
			);
		);

		Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);

		// Seed the envir so a stickless seat is silent — SC's default event
		// amp is 0.1 and the state ticks are the only writers of \amp.
		Pdef(m.ptn).set(\amp, 0);
		Pdef(m.ptn).set(\dur, 1);

		// Pitch is set as \note + \root + \octave, NEVER as \freq. See the
		// note above the state ticks — one stray \freq set kills \octave
		// permanently, for this Pdef, in every state.
		Pdef(m.ptn).set(\note,   0);
		Pdef(m.ptn).set(\root,   idleBase.mod(12));
		Pdef(m.ptn).set(\octave, idleBase.div(12));

		// §15 reload guard: a load landing mid-tuning never sees the edge.
		if (~roomState == \tuning) { tuneTime = TempoClock.beats };
	};

	// Installed OUTSIDE topEnvironment.use so it lands in d.env — a global
	// slot would let the next personality load on ANY device clobber it and
	// strand this Pdef on seek (§6, §12).
	~onResync = { |idx|
		topEnvironment.use {
			Pdef(m.ptn).stop;
			s.bind { group.freeAll };
			Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		};
	};
};

//------------------------------------------------------------
// Idempotent cleanup — notNil guard handles titleView off+on firing
// ~deinit twice on the same env (§5). No buffers, so no load barrier.
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	if (group.notNil) {
		s.bind { group.freeAll };
		group.free;
		group = nil;
	};
};

//------------------------------------------------------------
// Room-state routing. The Pdef keeps playing across every state; per-state
// amp comes from the ticks. \silent is one-shot here — it has no tick (§3).
~onRoomState = { |ctx|
	switch(ctx.state,
		\idle,    { },
		\tuning,  { tuneTime = TempoClock.beats },
		\piece,   { },
		\curtain, { },
		\silent,  { Pdef(m.ptn).set(\amp, 0) }
	);
};

//------------------------------------------------------------
// State ticks (~30 Hz). Every mapping is inline and per-state (§22).
//
// PITCH IS SET VIA THE EVENT CHAIN, NOT \freq.
//   midinote = \note + \root + (12 * \octave)      (measured, exactly this)
// \freq short-circuits that whole chain, so setting it makes \octave — and
// \note and \root with it — silently do nothing. \plukString takes Hz and
// has no \octave arg of its own; SC computes \freq from these three and
// hands it to the synth. Keep it that way: do NOT reintroduce a \freq set
// anywhere, including as a "quick override" — Pdef.set values persist in
// the envir, so one \freq set disables \octave for the rest of the load.
//
// The gesture split, held consistent across all four states so the
// instrument stays learnable:
//     roll  (gyro x)  -> filterFreq   how open the string is
//     twist (rrate)   -> filterRes    how much it buzzes
//     accel           -> decay        how long it rings, and note length
//     z tilt          -> phs          exciter texture
//     y tilt          -> octave       (piece only — idle/tuning pin it)

~idleNext = { |d, ctx|
	// Quiet and dark. Amp from rotation only, so a resting stick is silent
	// but a slow wrist turn still speaks. §17 `low`.
	var amp   = m.accelMassFiltered.lincurve(0, 1.0, -70, -5, -4);
	var ffreq = ((d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5) * 2).lincurve(-1, 1, 500, 4500, 3);
	var res   = m.rrateMassFiltered.lincurve(0, 1.2, 0.7, 0.25, -1);
	var dcy   = m.accelMassFiltered.lincurve(0, 2.0, 0.6, 1.8, -1);
	var phase = m.gyroZFiltered.fold(-0.5, 0.5).lincurve(-0.5, 0.5, 4, 18, 1);
	var pchi  = m.gyroZFiltered.fold(-0.5, 0.5).lincurve(-0.5, 0.5, 0, idleNotes.size - 0.001, -1).asInteger;

	Pdef(m.ptn).set(\amp,        amp.dbamp);
	Pdef(m.ptn).set(\dur,        m.accelMassFiltered.lincurve(0, 2.5, 2, 1, -1));
	// idleBase 57 == root 9 + (12 * octave 4). Split so \octave is live here
	// too — move idleBase and the root/octave pair follow it automatically.
	Pdef(m.ptn).set(\note,       idleNotes[pchi]);
	Pdef(m.ptn).set(\root,       idleBase.mod(12));
	Pdef(m.ptn).set(\octave,     idleBase.div(12));
	Pdef(m.ptn).set(\attack,     0.02);      // soft, no click
	Pdef(m.ptn).set(\decay,      dcy);
	Pdef(m.ptn).set(\filterFreq, ffreq);
	Pdef(m.ptn).set(\filterRes,  res);
	Pdef(m.ptn).set(\phs,        phase);
	Pdef(m.ptn).set(\exciteRate, 1);
	
};

~tuningNext = { |d, ctx|
	// The string finds its A. Enters ~4 semitones sharp and settles onto
	// MIDI 69 over 15 s (§16 ramp). Sparse, quiet, and progressively
	// brighter as it locks on, so the convergence is audible.
	var tt   = 15.0;
	var t    = ((TempoClock.beats - tuneTime) / tt).clip(0, 1);
	var amp  = (m.rrateMassFiltered * 2.0).lincurve(0, 1.0, -70, -28, -4);

	Pdef(m.ptn).set(\amp,        amp.dbamp);
	Pdef(m.ptn).set(\dur,        1.0.rrand(1.7));
	// A = root 9 + (12 * octave 5) = 69. The ramp rides \note, which takes
	// floats, so the convergence stays microtonal.
	Pdef(m.ptn).set(\note,       t.linexp(0, 1, 1, 0.0001) * 4);
	Pdef(m.ptn).set(\root,       9);
	Pdef(m.ptn).set(\octave,     5);
	Pdef(m.ptn).set(\attack,     0.01);
	Pdef(m.ptn).set(\decay,      t.linlin(0, 1, 0.8, 1.6));
	Pdef(m.ptn).set(\filterFreq, t.linexp(0, 1, 900, 5000));
	Pdef(m.ptn).set(\filterRes,  0.4);
	Pdef(m.ptn).set(\phs,        t.linlin(0, 1, 6, 14));
	Pdef(m.ptn).set(\exciteRate, 1);
};

~pieceNext = { |d, ctx|
	// Full dynamic. Every gesture is live and each drives its own arg.
	// §17 `expressive`, scaled by score loudness.
	var amp   = m.accelMassFiltered.lincurve(0, 0.5, -70, -14, -1);
	var oct   = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 4, 6, 1).asInteger;
	var pitch = (ctx.voicePool ? [69]).choose.asInteger.wrap(0, 11);
	var durs  = [2, 1, 0.5];
	var di    = m.accelMassFiltered.lincurve(0, 2.0, 0, durs.size - 0.001, 1).asInteger;
	var ffreq = ((d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5) * 2).lincurve(-1, 1, 400, 9000, 3);
	var res   = m.rrateMassFiltered.lincurve(0, 1.5, 0.65, 0.12, -1);   // twist = buzz
	var dcy   = m.accelMassFiltered.lincurve(0, 2.0, 0.25, 1.4, -1);    // hit harder = ring longer
	var phase = m.gyroZFiltered.fold(-0.5, 0.5).lincurve(-0.5, 0.5, 5, 26, 1);

	Pdef(m.ptn).set(\amp,        amp.dbamp * ctx.loudness.linlin(0, 1, 0.3, 1.3));
	Pdef(m.ptn).set(\dur,        durs[di]);
	// Pitch class from the score in \root, y tilt in \octave — the two are
	// now independent, so the score can move the harmony without fighting
	// the player's register (and vice versa).
	Pdef(m.ptn).set(\note,       0);
	Pdef(m.ptn).set(\root,       pitch);
	Pdef(m.ptn).set(\octave,     oct);
	Pdef(m.ptn).set(\attack,     0.001);     // sharp — this is the pluck
	Pdef(m.ptn).set(\decay,      dcy);
	Pdef(m.ptn).set(\filterFreq, ffreq);
	Pdef(m.ptn).set(\filterRes,  res);
	Pdef(m.ptn).set(\phs,        phase);
	// Brightness thickens the excitation rather than the filter, so a bright
	// bar reads as more wire noise instead of just a higher cutoff.
	Pdef(m.ptn).set(\exciteRate, ctx.brightness.linlin(0, 1, 1, 4));
};

~curtainNext = { |d, ctx|
	// Decay, don't cut. Holds whatever pitch the piece left it on — no
	// \freq set here, deliberately — and just darkens and fades. §17 `faded`.
	var amp   = (m.rrateMassFiltered * 2.0).lincurve(0, 1.0, -80, -30, -4);
	var ffreq = ((d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5) * 2).lincurve(-1, 1, 400, 2600, 3);

	Pdef(m.ptn).set(\amp,        amp.dbamp);
	Pdef(m.ptn).set(\dur,        2);
	Pdef(m.ptn).set(\attack,     0.03);
	Pdef(m.ptn).set(\decay,      2.2);       // longest ring of any state
	Pdef(m.ptn).set(\filterFreq, ffreq);
	Pdef(m.ptn).set(\filterRes,  0.3);
	Pdef(m.ptn).set(\phs,        5);
	Pdef(m.ptn).set(\exciteRate, 1);
};

//------------------------------------------------------------
// Beat-aligned hooks. Pitch is handled in the state ticks; these are
// deliberately empty stubs.
~onTick    = { |ctx| };
~onHalf    = { |ctx| };
~onBeat    = { |ctx| };
~onBar     = { |ctx| };
~onPhrase  = { |ctx| };
~onSection = { |ctx| };
~onChord   = { |ctx| };
~onKey     = { |ctx| };
~onScale   = { |ctx| };

//------------------------------------------------------------
// Debug plot: the two gestures that fight hardest to be heard —
// accel (decay / note length) against rrate (buzz).
~plotMin = -1;
~plotMax = 2;
~plot = { |d, p|
	[m.accelMassFiltered, m.rrateMassFiltered, m.gyroZFiltered];
};
