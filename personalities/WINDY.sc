/*
gestures:    [shake, tilt]
description: Two long-lived drone synths held for the life of the seat — \forestBreeze (filtered pink-noise wind + brown-noise leaf litter + resonant creaking trees, each with its own reverb) and \syntheticLeaf (granulated noise through a moving band-pass, with a sub layer); accel drives both amps at IMU rate, ~onBeat retunes the leaf band-pass from the score voice pool
sound:       a wind bed — breeze rising and falling with the stick, dry leaves rustling over it, trees creaking underneath; the rustle is pitched and shifts with the harmony on every beat
pitch:       \forestBreeze is unpitched. \syntheticLeaf's \freq is the centre frequency of its band-pass — a colour, not a note. Idle: y tilt plays through idleNotes (A pentatonic, A3..E6), throttled to one change per 0.2 s. Tuning: glides from wherever idle left it to 440 Hz over 15 s, then pins A. Piece: retuned each beat to whichever note of ctx.voicePool is closest to the one already sounding, so it voice-leads through the harmony in small steps. Curtain: holds the piece's last pitch
rhythm:      none — both voices are continuous. The only beat-locked event is the leaf band-pass retune in ~onBeat, which runs in \piece only
instruments: [Aetherharp]
seats:       [1, 2, 3, 4, 5]
affinity:    [wind, windy, breeze, breezy, gust, gale, storm, noise, white noise, static, hiss, air, airy, sky, breath, blowing, rustle, rustling, leaves, trees, whoosh, draft, weather, hurricane, tornado]
register:    [wild]
family:      wind
*/

// -------------------------------------------------------------------------
// WINDY — a two-voice wind bed.
//
// Engine: §2A long-lived synths, in the §25 "two long-lived voices with
// per-voice amp" shape — both Synths created once in ~init, held in one
// personality Group, released together in ~deinit. No Pdef anywhere, so
// per §6 there is deliberately NO ~onResync: nothing here is scheduled on
// ~beatClock, so a seek has nothing to re-anchor. The drones just keep
// blowing across it.
//
// Voices, both copied VERBATIM from their source files:
//   \forestBreeze   synths/forest.sc   (master gain is \masterAmp, NOT \amp)
//   \syntheticLeaf  synths/leaves.sc   (master gain is \amp)
// If either voice needs to sound different it changes in synths/ and gets
// copied back over — not edited here.
//
// Both hold: \forestBreeze runs Env.asr(3, 1, 3) and \syntheticLeaf
// Env.asr(1.3, 1, 2.3, \welch), both gated, both doneAction: 2. So they
// sustain indefinitely and release cleanly when ~deinit sets \gate, 0 —
// which is why teardown waits before freeing the Group (§25).
//
// Level control is per §22: the state ticks own the mapping, one .set per
// line, gesture read inline. Amp curves are §17 palette values.
// -------------------------------------------------------------------------

var m  = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init's body runs under topEnvironment.use

var group;    // one Group for both voices — cleanup stays a single freeAll (§25)
var breeze;   // \forestBreeze  — the wind bed
var leaves;   // \syntheticLeaf — the pitched rustle on top

// ---- live config knobs (the file IS the config — edit, save, hear it, §1) --
// leafTrim sits the rustle under the breeze. NRT render of each voice alone
// at equal nominal gain, both carrying their ~init args: breeze rms 0.0134,
// leaf rms 0.0089 — the leaf is 3.5 dB QUIETER already (filterRQ 0.03 is a
// narrow band-pass), so -0.5 dB lands it about 4 dB under the bed: detail
// over the wind rather than replacing it.
//
// NB: measure with the ~init args, not the SynthDef defaults. On defaults
// the leaf measures 1.7 dB LOUDER than the breeze — the opposite sign — and
// a trim tuned that way buries it ~9 dB down.
//
// Leaf level is near-flat across the band-pass range (rms 0.0101 / 0.0089 /
// 0.0089 at 131 / 440 / 1760 Hz), so the ~onBeat retune doesn't jump it.
var leafTrim  = -0.5;       // dB, applied to every leaf amp
var leafFloor = 40;         // Hz — \freq below this makes the band-pass rumble
var leafCeil  = 2200;       // Hz — above this the rustle turns to whistle

// Idle plays through these with y tilt (ALTOSYNTH/BASSBUZZ idiom). MIDI
// numbers, low to high — tilt down = first, tilt up = last. A pentatonic
// spanning A3..E6, which sits inside [leafFloor, leafCeil] at both ends.
var idleNotes = [57, 60, 62, 64, 67, 69, 72, 76];

// Tuning glide (§16 shape, as ALTOSYNTH ~tuningNext): the leaf's distance
// from A is scaled down to nothing over tuneRamp seconds, then pinned exactly.
// Working in MIDI rather than Hz makes the glide constant-semitones-per-second
// instead of rushing the top of the sweep.
var tuneRamp    = 15.0;     // seconds to reach 440 Hz
var tuneTime    = 0;        // TempoClock.beats at \tuning entry (§15)
var tuneFromMidi = 69;      // where the glide starts — captured on that entry

// Where the leaf band-pass currently sits, in MIDI. Every hook that moves it
// writes this, so \tuning knows what note to glide away FROM.
var leafMidi  = 68.2;       // ~400 Hz, matching the ~init \freq below
var lastTime  = 0;          // throttle anchor for the idle note changes
var stateName = \idle;      // last state the ticks saw — ~onBeat reads it

//------------------------------------------------------------
// Filter tuning (§9). Wind should swell and subside, not flicker: slow rise,
// slow fall. Rotation is only read in curtain, where a slower decay lets the
// bed recede gradually instead of gating off between turns.
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.99;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay  = 0.6;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
// VERBATIM from synths/forest.sc — do not edit here.
SynthDef(\forestBreeze, {
    arg out=0,
    // Breeze controls
    breezeAmp=0.3, breezeCutoff=400, breezeQ=0.5, breezeSpeed=0.2, breezePan=0,
    // Leaves controls
    leavesAmp=0.2, leavesDensity=1.0, leavesBrightness=3000, leavesPan=0,
    // Tree controls
    treeAmp=0.15, treeResonance=100, treeSpeed=0.1, treePan=0,
    // Reverb controls for each element
    breezeVerb=0.3, leavesVerb=0.4, treeVerb=0.5,
    // Master controls
    masterAmp=1.0, gate=1;

    var breeze, leaves, trees, mainEnv;
    var breezeVerbed, leavesVerbed, treesVerbed;

    // Main envelope
    mainEnv = EnvGen.kr(
        Env.asr(3, 1, 3),
        gate,
        doneAction: 2
    );

    //------ Breeze Component ------//
    breeze = {
        var base, filtered, modulated;
        // Base noise with smooth fluctuation
        base = PinkNoise.ar * LFNoise2.kr(breezeSpeed).range(0.7, 1.0);

        // Multi-filtered noise for wind character
        filtered = RLPF.ar(
            base,
            LFNoise2.kr(breezeSpeed * 0.5).range(breezeCutoff * 0.7, breezeCutoff * 1.3),
            breezeQ
        );

        // Additional modulation layer
        modulated = filtered * LFNoise2.kr(
            breezeSpeed * 0.7,
            mul: 0.3,
            add: 0.7
        );

        Pan2.ar(modulated * breezeAmp, breezePan)
    }.();

    //------ Leaves Component ------//
    leaves = {
        var base, filtered, crackles;
        // Base texture
        base = BrownNoise.ar * leavesDensity;

        // High frequency content for leaf detail
        filtered = HPF.ar(base, leavesBrightness);

        // Add subtle crackles
        crackles = Dust2.ar(
            LFNoise2.kr(0.4).range(20, 50) * leavesDensity
        );

        // Combine and shape
        filtered = ((filtered * 0.7) + (crackles * 0.3)) *
        LFNoise2.kr(
            LFNoise2.kr(0.1).range(0.3, 0.7)
        ).range(0.5, 1.0);

        Pan2.ar(filtered * leavesAmp, leavesPan)
    }.();

    //------ Trees Component ------//
    trees = {
        var base, resonant, modulated;
        // Low frequency movement
        base = LFNoise2.ar(
            LFNoise2.kr(0.05).range(0.2, 0.4) * treeSpeed
        );

        // Resonant filter for wooden creaking
        resonant = Resonz.ar(
            base,
            LFNoise2.kr(treeSpeed * 0.3).range(treeResonance * 0.8, treeResonance * 1.2),
            0.1
        );

        // Add occasional creaks
        modulated = resonant + (
            Dust2.ar(0.5) *
            SinOsc.ar(
                LFNoise2.kr(0.1).range(treeResonance * 0.7, treeResonance * 1.1)
            ) * 0.1
        );

        Pan2.ar(modulated * treeAmp, treePan)
    }.();

    // Apply individual reverbs
    breezeVerbed = FreeVerb2.ar(
        breeze[0], breeze[1],
        mix: breezeVerb,
        room: 0.8,
        damp: 0.2
    );

    leavesVerbed = FreeVerb2.ar(
        leaves[0], leaves[1],
        mix: leavesVerb,
        room: 0.6,
        damp: 0.4
    );

    treesVerbed = FreeVerb2.ar(
        trees[0], trees[1],
        mix: treeVerb,
        room: 0.9,
        damp: 0.2
    );

    // Final mix and output
    Out.ar(out,
        (breezeVerbed + leavesVerbed + treesVerbed) *
        mainEnv *
        masterAmp.lagud(0.6, 6) * 4
    );
}).add;

//------------------------------------------------------------
// VERBATIM from synths/leaves.sc — do not edit here.
SynthDef(\syntheticLeaf, {
    |out=0, pan=0, amp=0.1, grainDur=0.05, grainRate=20, freq=200,
     filterLFOFreq=5, filterLFOAmp=0.02, filterRQ=1, dustiness=0.2,
	leafType=0, gate=1, subAmp=0.01, subFreq=40, wobbleAmp=0.3|

    var sig, env, dust, filterEnv, leafNoise, sub, wobble;

    // Create base sound for granulation
    leafNoise = SelectX.ar(leafType, [
		PinkNoise.ar,  // Softer leaves
        BrownNoise.ar, // More crinkly leaves
        GrayNoise.ar   // Crisp leaves

    ]);
	wobble = LFNoise2.ar(3);
	sub = RLPF.ar(GrayNoise.ar, subFreq + wobble.range(0,30) ,0.3,subAmp);


    // Granular synthesis
    sig = GrainIn.ar(
        numChannels: 1,
        trigger: Impulse.ar(grainRate),
        dur: grainDur,
        in: leafNoise,
        pan: LFNoise1.kr(5)
    );

    // Add some dust for additional texture
    dust = Dust.ar(200 * dustiness) * 0.5;
    sig = sig + dust;

    // Moving filter
    filterEnv = LFNoise2.kr(filterLFOFreq).range(1.0-filterLFOAmp, 1.0+filterLFOAmp);
    sig = BPF.ar(sig, freq.lagud(1.5,2.0) * filterEnv, filterRQ) + sub;

    // Envelope
	env = EnvGen.kr(Env.asr(1.3, 1, 2.3, \welch), gate, doneAction: 2) * wobble.range(1.0, 1.0 + wobbleAmp);

    // Output
    Out.ar(out, Pan2.ar(sig * env * amp.lagud(0.3, 2) * 7, pan));
}).add;

//------------------------------------------------------------
// Both voices start now and run until ~deinit.
//
// The args below are the ones from the example instance at the BOTTOM of each
// source file — those are the author's chosen settings, not the SynthDef arg
// defaults, so a seat built on the defaults alone would not sound like the
// synth as auditioned. Copied from:
//   synths/forest.sc  ~forest = Synth(\forestBreeze, [...])
//   synths/leaves.sc  ~leaves = Synth(\syntheticLeaf, [...])
//
// Two deliberate departures, both about level rather than character:
//
//   * MASTER GAINS STAY 0. \masterAmp (breeze) and \amp (leaf) are what the
//     state ticks drive, and they only run while the seat's device is
//     enabled — a stickless seat must be silent, so it cannot inherit
//     \forestBreeze's masterAmp 1.0 or the leaves example's \amp, 0.5.
//     Everything else in those instances is character and is kept as-is.
//     (The leaf example's 0.5 is a level, so it is the one value not adopted;
//     if the seat reads quiet against it, raise the tick ceilings or
//     leafTrim, not this line.)
//
//   * \legato, 2 from the leaves example is dropped: \syntheticLeaf has no
//     such control (it is a pattern key, and that instance is a bare Synth),
//     so sending it would only draw a "Control not found" from the server.
~init = ~init <> {
	topEnvironment.use {
		group = Group.new;

		breeze = Synth(\forestBreeze, [
			\out,           ob,
			\masterAmp,     0,        // ticks own this
			\breezeAmp,     0.2,
			\leavesAmp,     0.3,
			\treeAmp,       0.35,
			\breezeCutoff,  500,
			\leavesDensity, 0.9,
			\treeSpeed,     0.1
		], group);

		leaves = Synth(\syntheticLeaf, [
			\out,       ob,
			\amp,       0,       // ticks own this
			\freq,      400,     // example's value; ~onBeat replaces it from the pool
			\grainDur,  0.1,
			\grainRate, 20,
			\filterRQ,  0.03,
			\dustiness, 0.1,
			\leafType,  0
		], group);

		// §15 reload guard: ~onRoomState only fires on a state CHANGE, so a
		// load that lands mid-tuning would never anchor the glide and the
		// rustle would sit on its start note for the whole state.
		if (~roomState == \tuning) {
			tuneTime     = TempoClock.beats;
			tuneFromMidi = leafMidi;
			stateName    = \tuning;
		};
	};
};

//------------------------------------------------------------
// Teardown (§25). Release both gates first so the wind falls away instead of
// being chopped, then free the Group. The waits cover the release tails
// (\forestBreeze 3 s, \syntheticLeaf 2.3 s) only partially — 0.5 s is the
// documented compromise between a clean tail and a fast personality swap.
// Idempotent: refs captured and nil'd before the fork, so a second ~deinit
// on the same env is a no-op.
~deinit = ~deinit <> {
	var g = group;
	var b = breeze, l = leaves;
	group = nil; breeze = nil; leaves = nil;

	fork {
		if (b.notNil) { b.set(\gate, 0) };
		if (l.notNil) { l.set(\gate, 0) };
		if (g.notNil) {
			0.5.wait;
			s.bind { g.freeAll };
			s.sync;
			g.free;
		};
	};
};

//------------------------------------------------------------
// Room-state routing. Both voices run in every state; per-state level is set
// by the ticks below, so this is one-shot entry logic only.
~onRoomState = {|ctx|
	switch(ctx.state,
		\idle,    { },
		\tuning,  {
			// Anchor the glide and capture where it starts from, so tuning
			// slides in from whatever note idle left the band-pass on.
			tuneTime     = TempoClock.beats;
			tuneFromMidi = leafMidi;
		},
		\piece,   { },
		\curtain, { },
		\silent,  {
			// Hard mute — no \silent tick runs, so nothing re-raises these.
			breeze !? { breeze.set(\masterAmp, 0) };
			leaves !? { leaves.set(\amp, 0) };
		}
	);
};

//------------------------------------------------------------
// State-gated ticks (~30 Hz). These own every mapping (§22). Accel drives
// both voices in all four states; only the curve and the ceiling change.
// NB: \forestBreeze's master gain is \masterAmp, \syntheticLeaf's is \amp.

// Idle: a bed you barely notice until someone moves, and y tilt plays the
// rustle through idleNotes — tilt down for the low end of the array, up for
// the high. Palette: `low` (§17).
//
// The 0.2 s throttle is the ALTOSYNTH/BASSBUZZ idiom: without it the note
// re-sets 30 times a second and chatters every time the wrist sits on a
// boundary between two array slots. (Those two also .defer(0.4) the set to
// lag the change behind the gesture; not copied here — the band-pass moves
// audibly enough on its own and it keeps AppClock out of the path.)
//
// `size - 0.001` rather than `size`: at tilt exactly +1, .linlin(-1, 1, 0,
// size) returns size, and idleNotes[size] is nil -> nil.midicps. The house
// files have that edge live; no reason to copy it.
~idleNext = {|d, ctx|
	var idx = (d.sensors.gyroEvent.y / pi.half)
		.linlin(-1, 1, 0, idleNotes.size - 0.001).asInteger.clip(0, idleNotes.size - 1);

	stateName = \idle;
	breeze !? { breeze.set(\masterAmp, m.accelMassFiltered.lincurve(0, 1.0, -80, -15, -1).dbamp) };
	leaves !? { leaves.set(\amp, (m.accelMassFiltered.lincurve(0, 2.0, -80, 0, -4) + leafTrim).dbamp) };

	if (TempoClock.beats > (lastTime + 2.0), {
		leafMidi = idleNotes[idx];
		leaves !? { leaves.set(\freq, leafMidi.midicps.clip(leafFloor, leafCeil)) };
		lastTime = TempoClock.beats;
	});
};

// Tuning: the wind comes up a little so the room can hear the seat is live,
// and the rustle finds A — the band-pass slides from wherever idle left it to
// 440 Hz over tuneRamp seconds, then holds there exactly.
// Palette: `mid` (§17). \forestBreeze is unpitched, so it just holds level.
//
// Same shape as ALTOSYNTH ~tuningNext: scale the distance-from-target down to
// zero across the ramp, then pin the target. Done in MIDI, so the glide is
// even in semitones rather than accelerating through the top of the sweep.
// The -2 curve means it moves quickly at first and settles slowly.
~tuningNext = {|d, ctx|
	var tt      = tuneRamp;
	var elapsed = TempoClock.beats - tuneTime;

	stateName = \tuning;
	breeze !? { breeze.set(\masterAmp, m.accelMassFiltered.lincurve(0, 2.0, -80, -15, -2).dbamp) };
	leaves !? { leaves.set(\amp, (m.accelMassFiltered.lincurve(0, 2.0, -80, 0, -2) + leafTrim).dbamp) };

	if (elapsed < tt, {
		var val = elapsed / tt;
		leafMidi = 69 + ((tuneFromMidi - 69) * val.lincurve(0, 1, 1, 0, -2));
	}, {
		leafMidi = 69;                       // 440 Hz exactly
	});
	leaves !? { leaves.set(\freq, leafMidi.midicps.clip(leafFloor, leafCeil)) };
};

// Piece: full dynamic, scaled by the score's loudness. Palette: `expressive`.
~pieceNext = {|d, ctx|
	stateName = \piece;   // ~onBeat only retunes the leaf in this state
	breeze !? { breeze.set(\masterAmp, m.accelMassFiltered.lincurve(0, 1.5, -80, -15, -1).dbamp * ctx.loudness.linlin(0, 1, 0.3, 1.0)) };
	leaves !? { leaves.set(\amp, (m.accelMassFiltered.lincurve(0, 1.5, -80, 0, -1) + leafTrim).dbamp * ctx.loudness.linlin(0, 1, 0.3, 1.0)) };
};

// Curtain: the wind drops. Rotation only, so a stick set down goes quiet.
// Palette: `faded` (§17).
// Holds whatever pitch the piece left the rustle on — nothing here moves it.
~curtainNext = {|d, ctx|
	stateName = \curtain;
	breeze !? { breeze.set(\masterAmp, m.rrateMassFiltered.lincurve(0, 2.0, -80, -30, -4).dbamp) };
	leaves !? { leaves.set(\amp, (m.rrateMassFiltered.lincurve(0, 2.0, -80, -30, -4) + leafTrim).dbamp) };
};

// ~silent has no tick — handled inline in ~onRoomState above.

//------------------------------------------------------------
// Beat-aligned hooks.
~onTick    = {|ctx| };
~onHalf    = {|ctx| };

~onBeat    = {|ctx|
	var pool = (ctx !? { ctx.voicePool }) ? [69];
	if (pool.isEmpty) { pool = [69] };
	if (stateName == \piece) {
		leafMidi = pool.minItem({ |n| (n.asInteger - leafMidi).abs }).asInteger
			.clip(leafFloor.cpsmidi, leafCeil.cpsmidi);
		leaves !? { leaves.set(\freq, leafMidi.midicps) };
	};
};

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
	[(d.sensors.gyroEvent.y / pi)];
};
