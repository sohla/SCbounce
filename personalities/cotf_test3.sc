/*
gestures:    [beat, shake, tilt]
description: ONE mono sample serves two engines — a long-lived GrainBuf pad (amp from accel) + a Pdef PlayBuf pattern (amp + dur from rrate). Both read the same buffer, both track score via ~onHalf freq updates. Simplified rewrite: one buffer, canonical PlayBuf/GrainBuf rate idioms. Both engines are built inside the Buffer read's completion action — nothing touches the bufnum before the server has data behind it, which is what killed scsynth ("server exited with exit code 0") on load.
sound:       Voice sample used two ways at once: granular ambient pad + rhythmic per-note chops of the same source, tracking score pitch.
pitch:       ~onHalf sets \freq for both engines from ctx.voicePool.first wrapped to pitch class + baseMidi (60). Each SynthDef derives rate from freq/srcFreq.
rhythm:      Pad continuous; pattern per-note on ~beatClock with \dur from rrate.
instruments: [brownBall]
*/

var m = ~model;
var ob = ~outBus ? 0;
var group;              // holds padSynth AND per-note pattern synths
var padSynth;
var sampleBuffer;
var baseMidi = 60;
var tuneTime = 0;       // TempoClock.beats captured on \tuning entry (§16 ramp)

// One sample — loaded MONO (readChannel [0]) because GrainBuf requires
// a mono buffer. samplerVoice reads mono too and pans in the SynthDef.
var samplePath      = "~/Music/cotf_samples/voice/aah.wav";
var samplePitchMidi = 68;   // intrinsic pitch of the file (G#4)

m.accelMassFilteredAttack = 0.98;
m.accelMassFilteredDecay  = 0.2;
m.rrateMassFilteredAttack = 0.9999;
m.rrateMassFilteredDecay  = 0.2;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
// (a) grainPad — long-lived granular pad.
// GrainBuf's `rate` is a PITCH RATIO — internal BufRateScale handling.
// No external BufRateScale here.
SynthDef(\grainPad, { |out=0, bufnum=0, amp=0, freq=440, srcFreq=440, gate=1,
    grainDur=0.15, grainDensity=20, grainPos=0.5, grainPosSpread=0.1,
    attack=0.5, release=0.5, ffreq=2000, lagAttack=0.05, lagRelease=0.8|
	var env  = EnvGen.kr(Env.asr(attack, 1, release), gate, doneAction: 2);
	var rate = freq / srcFreq * 0.99;
	var trig = Impulse.kr(grainDensity);
	var pos  = grainPos + WhiteNoise.kr(grainPosSpread);
	var sig  = GrainBuf.ar(2, trig, grainDur, bufnum, rate, pos, 2, 0);
	var sub = LFTri.ar(freq, 0, 0.1);
	var filt = RLPF.ar(sig, ffreq.lag(0.1), 0.4) + sub;
	Out.ar(out, filt * env * amp.lagud(lagAttack, lagRelease));
}).add;

//------------------------------------------------------------
// (b) samplerVoice — per-note PlayBuf, mono → Pan2.
// PlayBuf's `rate` is samples-per-output-sample — needs explicit
// BufRateScale for pitch correctness across SR mismatch (canonical idiom).
SynthDef(\samplerVoice, { |out=0, bufnum=0, amp=0.5, freq=440, srcFreq=440,
    gate=1, pan=0, attack=0.01, decay=0.1, release=0.1|
	var env = EnvGen.kr(Env.adsr(attack, decay, 0.07, release), gate, doneAction: Done.freeSelf);
	var lr  = (freq / srcFreq) * BufRateScale.kr(bufnum) * 0.99;
	var sig = PlayBuf.ar(1, bufnum, rate: lr, loop: 0);
	Out.ar(out, Pan2.ar(sig, pan, amp * env));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	topEnvironment.use {
		group = Group.new;

		// Buffer.readChannel is ASYNC — the bufnum exists client-side
		// immediately, but the server has no data behind it until the read
		// completes. Nothing may read that bufnum before then: a GrainBuf
		// started on an empty buffer (data NULL, 0 frames, sr 0) kills
		// scsynth outright ("Server exited with exit code 0" right after
		// this personality's init line). So padSynth AND the Pdef are both
		// built inside the completion action.
		sampleBuffer = Buffer.readChannel(s, samplePath.standardizePath, channels: [0], action: {|buf|
			postf("sample loaded (mono ch0): % (% frames, sr %)\n",
				samplePath, buf.numFrames, buf.sampleRate);

			// The action fires on a later thread — ~deinit may already have
			// run (fast personality switch). sampleBuffer is nil'd there;
			// bail rather than spawn a synth nothing will ever clean up.
			if (sampleBuffer.notNil) {
				topEnvironment.use {
					// padSynth is OUTSIDE the group. group holds ONLY the
					// Pdef's per-note synths, so ~onResync's group.freeAll
					// doesn't kill padSynth (avoids the s.bind/immediate race
					// that produces "Node not found" spam on seek). ~deinit
					// kills both in the correct order below.
					padSynth = Synth(\grainPad, [
						\out,     ob,
						\bufnum,  buf.bufnum,
						\srcFreq, samplePitchMidi.midicps,
						\freq,    samplePitchMidi.midicps,   // rate = 1 at rest
						\amp,     0,
						\attack,  0.5,
						\release, 1.5,
					]);

					Pdef(m.ptn,
						Pbind(
							\instrument, \samplerVoice,
							\out,        ob,
							\group,      group,
						);
					);
					Pdef(m.ptn).set(\bufnum,  buf.bufnum);
					Pdef(m.ptn).set(\srcFreq, samplePitchMidi.midicps);
					Pdef(m.ptn).set(\freq,    samplePitchMidi.midicps);
					Pdef(m.ptn).set(\amp,     0);
					Pdef(m.ptn).set(\dur,     1);
					Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);

					// §15 reload guard — ~onRoomState fires only on state
					// CHANGE, so a reload while already in \tuning never runs
					// its branch and tuneTime would stay 0 (ramp dead, ptch
					// pinned at 1.0). Capture it here as well.
					if (~roomState == \tuning, { tuneTime = TempoClock.beats });
				};
			};
		});
	};

	~onResync = { |idx|
		// nil-guard: if a resync fires post-~deinit (double-deinit pattern
		// or stale timing), group is nil — skip cleanly instead of throwing.
		if (group.notNil, {
			topEnvironment.use {
				Pdef(m.ptn).stop;
				s.bind { group.freeAll };   // Pdef synths only — padSynth untouched
				Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
			};
		});
	};
};

//------------------------------------------------------------
// harp1-style cleanup + simple1-style gate release for the long-lived
// padSynth. Capture refs and null vars synchronously so a double-fire
// ~deinit (titleView off+on) sees nil everywhere and no-ops.
~deinit = ~deinit <> {
	var g = group;
	var p = padSynth;
	var b = sampleBuffer;
	group = nil;
	padSynth = nil;
	sampleBuffer = nil;
	Pdef(m.ptn).remove;
	if (p.notNil) { p.set(\gate, 0) };   // padSynth releases over `release` (1.5s), auto-frees via doneAction:2
	fork {
		if (g.notNil) {
			s.bind { g.freeAll };
			s.sync;
			g.free;
		};
		2.0.wait;                        // wait for padSynth's release + auto-free
		if (b.notNil) { b.free };        // safe now — nothing reading it
	};
};

//------------------------------------------------------------
~onRoomState = { |ctx|
	switch(ctx.state,
		\idle,    { },
		\tuning,  {
			// Start of the §16 tuning ramp — ~tuningNext measures from here.
			tuneTime = TempoClock.beats;
		},
		\piece,   { },
		\curtain, { },
		\silent,  {
			padSynth.set(\amp, 0);
			Pdef(m.ptn).set(\amp, 0);
		}
	);
};

//------------------------------------------------------------
// State ticks — accel drives pad, rrate drives pattern. No cross-mixing.
// padSynth is nil until the sample finishes loading (and again after
// ~deinit); no guard needed — Nil:set is a no-op.

~idleNext = { |d, ctx|

	var under = m.accelMassFiltered.lincurve(0, 1, m.rrateMassFiltered.neg, 0, -1).neg.lincurve(0, 0.4, 0, 1, -1	);
	var amp = m.rrateMassFiltered.lincurve(0, 0.4, -60, 1, -1).dbamp;
	var dur = m.rrateMassFiltered.lincurve(0, 1.5, 2.5, 0.5, -3);
	padSynth.set(\amp,          m.accelMassFiltered.lincurve(0, 2.0, -80, -15, -3).dbamp);
	padSynth.set(\ffreq,        1800);
	padSynth.set(\grainDur,     0.2);
	padSynth.set(\grainDensity, 15);
	padSynth.set(\lagAttack,    0.07);
	padSynth.set(\lagRelease,   0.7);
	padSynth.set(\freq,         (samplePitchMidi - 11).midicps);
	Pdef(m.ptn).set(\amp,   amp * 3);
	Pdef(m.ptn).set(\dur,   dur);
	Pdef(m.ptn).set(\freq, [samplePitchMidi + 1].choose.midicps);
};

~tuningNext = { |d, ctx|

	var tt      = 5.0;
	var elapsed = TempoClock.beats - tuneTime;
	var ptch    = if (elapsed < tt) {
		(elapsed / tt).linlin(0, 1, 0.7, 1.0)   // flat → true
	} { 1.0 };
	var tuneMidi = 69;
	var under = m.accelMassFiltered.lincurve(0, 1, m.rrateMassFiltered.neg, 0, -1).neg.lincurve(0, 0.4, 0, 1, -1	);
	var amp = m.rrateMassFiltered.lincurve(0, 0.4, -60, 1, -1).dbamp;
	var dur = m.rrateMassFiltered.lincurve(0, 1.5, 2.5, 0.5, -3);

	padSynth.set(\amp,          m.accelMassFiltered.lincurve(0, 2.0, -80, -20, -3).dbamp);
	padSynth.set(\ffreq,        1200);
	padSynth.set(\grainDur,     0.3);
	padSynth.set(\grainDensity, 12);
	padSynth.set(\lagAttack,    0.1);
	padSynth.set(\lagRelease,   2.1);
	padSynth.set(\freq,         (tuneMidi - 12).midicps * ptch);
	Pdef(m.ptn).set(\amp,  amp * 3);
	Pdef(m.ptn).set(\dur,  dur);
	Pdef(m.ptn).set(\freq, tuneMidi.midicps * ptch);
};

~pieceNext = { |d, ctx|

	var under = m.accelMassFiltered.lincurve(0, 1, m.rrateMassFiltered.neg, 0, -1).neg.lincurve(0, 0.4, 0, 1, -1	);
	var amp = m.rrateMassFiltered.lincurve(0, 0.4, -60, 0, -1).dbamp;
	var dur = m.rrateMassFiltered.lincurve(0, 1.8, 2.5, 0.8, -3);

	padSynth.set(\amp,          m.accelMassFiltered.lincurve(0, 2.0, -60, -10, -1).dbamp);
	padSynth.set(\ffreq,        (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).lincurve(-0.5, 0.5, 100, 3000, -2));
	// padSynth.set(\grainDur,     m.accelMassFiltered.lincurve(0, 2.0, 0.3, 0.05, 1));
	// padSynth.set(\grainDensity, m.accelMassFiltered.lincurve(0, 2.0, 15, 50, 1));
	padSynth.set(\grainPos,    rrand(0.1,0.1));// (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 0.1, 0.9, 1));
	Pdef(m.ptn).set(\amp, amp * 10);
	Pdef(m.ptn).set(\dur, dur);//m.rrateMassFiltered.lincurve(0, 1.0, 2, 0.25, -1));
};

~curtainNext = { |d, ctx|
	padSynth.set(\amp,   m.accelMassFiltered.lincurve(0, 1.0, -80, -30, -4).dbamp);
	padSynth.set(\ffreq, 400);
	Pdef(m.ptn).set(\amp, m.rrateMassFiltered.lincurve(0, 1.0, -80, -22, -4).dbamp);
	Pdef(m.ptn).set(\dur, m.rrateMassFiltered.lincurve(0, 1.0, 3, 1, -1));
};

//------------------------------------------------------------
// Both engines' freq tracks score voicePool.first per half-bar.
~onHalf = { |ctx|
	var pitcha = ((ctx.voicePool.first.asInteger % 12) + baseMidi).midicps;
	var pitchb = ((ctx.voicePool.choose.asInteger % 12) + baseMidi).midicps;
	s.bind {
		padSynth.set(\freq, pitcha );
		Pdef(m.ptn).set(\freq, pitchb );
	};
};

~onTick    = { |ctx| 

};
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
~plot = { |d, p| [m.rrateMassFiltered] };
