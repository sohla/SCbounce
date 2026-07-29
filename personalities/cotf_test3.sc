/*
gestures:    [beat, shake, tilt]
description: Sample-based variant of cotf_test2. Two engines, each with its OWN buffer (assign same file for unified texture or different files for contrast). Pdef pattern uses a per-note sampler SynthDef (PlayBuf ADSR — like harp1); long-lived synth is a GrainBuf granular pad (continuous cloud). Both track score via ~onHalf freq updates. `under` idiom (§9) splits accel and rrate across all state ticks — accel drives the pad, rotation-isolated `under` drives the pattern.
sound:       Granular textured pad (accel-driven) + rhythmic sampler pattern (rotation-driven) tracking the same score notes. Pitch shift is derived internally via freq / srcFreq for each engine.
pitch:       ~onHalf sets \freq for both engines from ctx.voicePool.first wrapped to pitch class near baseMidi (60 = C4). Each SynthDef computes its own rate = freq / srcFreq.
rhythm:      Pad continuous (granular cloud); pattern per-note on ~beatClock with \dur driven by `under` per state.
instruments: [Template]
*/

var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init bodies run under topEnvironment.use
var padSynth;         // long-lived granular pad (simple2 style)
var group;            // dedicated Group for the Pdef's per-note synths (§5)
var baseMidi = 60;    // C4 — anchor pitch for voicePool wrap

// ============================================================
// BUFFER PATHS — EDIT ME
// ============================================================
// Each engine has its own buffer. Assign the same file for unified
// texture, or different files for contrast (e.g. pad on a long drone
// sample, pattern on a short struck note). srcFreq is the intrinsic
// pitch of each sample — the SynthDef computes rate = freq / srcFreq
// internally, so any single-note sample works as long as srcFreq is set.
var padSamplePath = "~/Music/cotf_samples/voice/aah.wav";
var padPitchMidi  = 68;   // G#4 — intrinsic pitch of the pad sample
var patSamplePath = "~/Music/cotf_samples/voice/aah.wav";
var patPitchMidi  = 68;   // G#4 — intrinsic pitch of the pattern sample

var padBuffer;   // granular pad
var patBuffer;   // sampler pattern

m.accelMassFilteredAttack = 0.98;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay = 0.1;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
// (a) Granular pad — continuous GrainBuf cloud from padBuffer. rate =
// freq / srcFreq, so grains sound at the requested pitch regardless of
// the source sample's intrinsic pitch. \grainDur is per-grain length,
// \grainDensity is grains/sec, \grainPos is normalised playhead
// position (0..1), \grainPosSpread adds continuous jitter to position.
// ASR envelope with gate — released by ~deinit's synth.set(\gate, 0).
SynthDef(\grainPad, {|out=0, bufnum=0, amp=0.0, freq=440, srcFreq=440, gate=1,
    grainDur=0.15, grainDensity=20, grainPos=0.5, grainPosSpread=0.1,
    attack=0.5, release=1.0, ffreq=2000,
    lagAttack=0.02, lagRelease=0.9|
	// Env.adsr with tiny decay + sustain 1 acts like ASR but is
	// battle-tested. doneAction 2 = Done.freeSelf.
	var env = EnvGen.kr(Env.adsr(attack, 0.01, 1, release), gate, doneAction: 2);
	var rate = freq / srcFreq;
	var trig = Impulse.kr(grainDensity);
	var pos  = grainPos + WhiteNoise.kr(grainPosSpread);
	var sig  = GrainBuf.ar(2, trig, grainDur, bufnum, rate, pos, 2, 0);
	var filter = RLPF.ar(sig, ffreq, 0.4);
	Out.ar(out, filter * env * amp.lagud(lagAttack, lagRelease));
}).add;

//------------------------------------------------------------
// (b) Sampler voice — per-note PlayBuf with ADSR. rate = freq/srcFreq
// (same idiom as the pad). Pbind's default \note event sends gate=1 at
// start and gate=0 after \sustain, so each per-note synth self-frees
// via Done.freeSelf. ADSR sustain LEVEL hardcoded (0.7) to avoid the
// collision with Pbind's ~sustain (time) — see cotf_test2.sc.
SynthDef(\samplerVoice, {|out=0, bufnum=0, amp=0.5, freq=440, srcFreq=440, gate=1,
    start=0, pan=0, attack=0.01, decay=0.1, release=0.4|
	var env = EnvGen.kr(Env.adsr(attack, decay, 0.3, release), gate, doneAction: Done.freeSelf);
	var rate = freq / srcFreq;
	var lr = rate * BufRateScale.kr(bufnum);
	var sig = PlayBuf.ar(2, bufnum, rate: lr, startPos: start * BufFrames.kr(bufnum), loop: 0);
	Out.ar(out, Balance2.ar(sig[0], sig[1], pan, amp * env));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	topEnvironment.use{
		// Load both buffers (async — bufnums allocated synchronously so
		// setting them below is safe even before the files load).
		// PAD: mono only — GrainBuf treats bufnum as a mono stream;
		// giving it a stereo buffer reads interleaved L/R as one
		// stream → near-Nyquist garbage / silence. Use readChannel
		// [0] to force mono regardless of the source file.
		padBuffer = Buffer.readChannel(s, padSamplePath.standardizePath, channels: [0], action:{|buf|
			postf("pad sample loaded (mono ch0): % (% frames, % channels)\n", padSamplePath, buf.numFrames, buf.numChannels);
		});
		// PATTERN: stereo — samplerVoice uses PlayBuf.ar(2, ...) which
		// respects the buffer's channel count.
		patBuffer = Buffer.read(s, patSamplePath.standardizePath, action:{|buf|
			postf("pattern sample loaded: % (% frames, % channels)\n", patSamplePath, buf.numFrames, buf.numChannels);
		});

		// (a) Long-lived granular pad.
		padSynth = Synth(\grainPad, [
			\out,            ob,
			\bufnum,         padBuffer.bufnum,
			\srcFreq,        padPitchMidi.midicps,
			\freq,           ((~scoreVoicePool.first.asInteger % 12) + baseMidi).midicps,
			\amp,            0,
			\attack,         0.5,
			\release,        1.5,
			\grainDur,       0.15,
			\grainDensity,   20,
			\grainPos,       0.5,
			\grainPosSpread, 0.1,
			\ffreq,          2000,
			\lagAttack,      0.3,
			\lagRelease,     0.8,
		]);

		// (b) Pdef pattern — per-note samplerVoice into the dedicated group.
		group = Group.new;
		Pdef(m.ptn,
			Pbind(
				\instrument, \samplerVoice,
				\out,        ob,
				\group,      group,
				\args,       #[],
			);
		);
		// Static constants — set ONCE (§22).
		Pdef(m.ptn).set(\bufnum,  patBuffer.bufnum);
		Pdef(m.ptn).set(\srcFreq, patPitchMidi.midicps);
		// Silent defaults so pre-first-tick events don't scream.
		Pdef(m.ptn).set(\amp,     0);
		Pdef(m.ptn).set(\dur,     1);
		Pdef(m.ptn).set(\freq,    patPitchMidi.midicps);
		Pdef(m.ptn).set(\attack,  0.01);
		Pdef(m.ptn).set(\release, 0.4);

		Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
	};

	~onResync = { |idx|
		topEnvironment.use {
			Pdef(m.ptn).stop;
			s.bind { group.freeAll };
			Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		};
	};
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	// Truly idempotent — capture local refs and NULL the file-scope vars
	// synchronously BEFORE async work. A second ~deinit fire (titleView
	// off+on triggers this) then sees nil everywhere and skips cleanly.
	var g  = group;
	var p  = padSynth;
	var pb = padBuffer;
	var pt = patBuffer;
	group     = nil;
	padSynth  = nil;
	padBuffer = nil;
	patBuffer = nil;
	Pdef(m.ptn).remove;
	if (p.notNil) { p.set(\gate, 0) };
	fork {
		if (g.notNil) {
			s.bind { g.freeAll };
			s.sync;
			g.free;
		};
		if (pb.notNil) { pb.free };
		if (pt.notNil) { pt.free };
	};
};

//------------------------------------------------------------
~onRoomState = { |ctx|
	switch(ctx.state,
		\idle,    { },
		\tuning,  { },
		\piece,   { },
		\curtain, { },
		\silent,  {
			if (padSynth.notNil) { padSynth.set(\amp, 0) };
			Pdef(m.ptn).set(\amp, 0);
		}
	);
};

//------------------------------------------------------------
// State ticks — EVERY mapping inline, per state. `under` idiom (§9)
// splits accel from rrate: accel → pad amp; `under` (rrate isolated
// from accel) → pattern amp + dur.

// idle — quiet pad, sparse pattern. Grain cloud is wide/slow, low pos.
~idleNext = { |d, ctx|
	var under = m.accelMassFiltered.lincurve(0, 1.0, m.rrateMassFiltered.neg, 0, -1).neg.lincurve(0, 0.4, 0, 1, -1);
	padSynth.set(\amp,            m.accelMassFiltered.lincurve(0, 2.0, -80, -4, -1).dbamp);
	padSynth.set(\ffreq,          800);
	padSynth.set(\grainDur,       0.2);
	padSynth.set(\grainDensity,   100);
	padSynth.set(\grainPos,       0.2);
	padSynth.set(\grainPosSpread, 0.15);
	padSynth.set(\lagAttack,      0.03);
	padSynth.set(\lagRelease,     0.08);
	Pdef(m.ptn).set(\amp,   under.lincurve(0, 1.0, -70, -2, -4).dbamp);
	Pdef(m.ptn).set(\dur,   under.lincurve(0, 1.0, 2, 1, -1));
};

// tuning — pad muted; pattern sparse.
~tuningNext = { |d, ctx|
	var under = m.accelMassFiltered.lincurve(0, 1.0, m.rrateMassFiltered.neg, 0, -1).neg.lincurve(0, 0.4, 0, 1, -1);
	padSynth.set(\amp,            0);
	padSynth.set(\ffreq,          400);
	padSynth.set(\grainDur,       0.3);
	padSynth.set(\grainDensity,   6);
	padSynth.set(\grainPos,       0.5);
	padSynth.set(\grainPosSpread, 0.05);
	padSynth.set(\lagAttack,      0.4);
	padSynth.set(\lagRelease,     1.0);
	Pdef(m.ptn).set(\amp,   under.lincurve(0, 1.0, -70, -18, -4).dbamp);
	Pdef(m.ptn).set(\dur,   under.lincurve(0, 1.0, 3, 1, -1));
};

// piece — full mapping. Accel opens the pad, drives grain density up
// and grain dur down (denser, tighter cloud on impacts). y-tilt scans
// grain position through the sample. x-tilt drives pad filter cutoff.
// `under` opens the pattern amp + tightens dur.
~pieceNext = { |d, ctx|
	var under = m.accelMassFiltered.lincurve(0, 1.0, m.rrateMassFiltered.neg, 0, -1).neg.lincurve(0, 0.4, 0, 1, -1);
	padSynth.set(\amp,            m.accelMassFiltered.lincurve(0, 2.0, -60, -3, -1).dbamp);
	padSynth.set(\ffreq,          (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).lincurve(-0.5, 0.5, 500, 8000, 3));
	padSynth.set(\grainDur,       m.accelMassFiltered.lincurve(0, 2.0, 0.3, 0.05, 1));
	padSynth.set(\grainDensity,   m.accelMassFiltered.lincurve(0, 2.0, 15, 50, 1));
	padSynth.set(\grainPos,       (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 0.1, 0.9, 1));
	padSynth.set(\grainPosSpread, 0.2);
	padSynth.set(\lagAttack,      0.02);
	padSynth.set(\lagRelease,     0.6);
	Pdef(m.ptn).set(\amp,   under.lincurve(0, 1.0, -60, -3, -4).dbamp);
	Pdef(m.ptn).set(\dur,   under.lincurve(0, 1.0, 2, 0.25, -2));
};

// curtain — pad fading, pattern faded. Long grains, low density.
~curtainNext = { |d, ctx|
	var under = m.accelMassFiltered.lincurve(0, 1.0, m.rrateMassFiltered.neg, 0, -1).neg.lincurve(0, 0.4, 0, 1, -1);
	padSynth.set(\amp,            m.accelMassFiltered.lincurve(0, 1.0, -80, -30, -4).dbamp);
	padSynth.set(\ffreq,          400);
	padSynth.set(\grainDur,       0.5);
	padSynth.set(\grainDensity,   5);
	padSynth.set(\grainPos,       0.7);
	padSynth.set(\grainPosSpread, 0.1);
	padSynth.set(\lagAttack,      0.5);
	padSynth.set(\lagRelease,     1.4);
	Pdef(m.ptn).set(\amp,   under.lincurve(0, 1.0, -80, -22, -4).dbamp);
	Pdef(m.ptn).set(\dur,   under.lincurve(0, 1.0, 4, 1.5, -1));
};

//------------------------------------------------------------
// Beat-locked pitch — freq for BOTH engines updates each half-bar.
// Same pitch to both (unison). Each SynthDef derives its own rate from
// freq / srcFreq, so different padPitchMidi vs patPitchMidi is handled
// transparently. `pitch` captured once (reuse across two .set calls).
~onHalf = { |ctx|
	var pitch = ((ctx.voicePool.first.asInteger % 12) + baseMidi).midicps;
	s.bind {
		padSynth.set(\freq, pitch);
		Pdef(m.ptn).set(\freq, pitch);
	};
};

~onTick    = { |ctx| };
~onBeat    = { |ctx| };
~onBar     = { |ctx| };
~onPhrase  = { |ctx| };
~onSection = { |ctx| };
~onChord   = { |ctx| };
~onKey     = { |ctx| };
~onScale   = { |ctx| };

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d, p| [m.accelMassFiltered, m.rrateMassFiltered] };
