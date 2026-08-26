/*
gestures:    [beat, shake, tilt]
description: A human voice, two ways at once — shake to swell a choir, twist to place sung notes in time
internals:   ONE mono vocal sample serves two engines at once — a long-lived GrainBuf pad (amp from accel) and a Pdef PlayBuf pattern (amp + dur from rrate). Both read the same buffer. Both engines are built inside the Buffer read's completion action, so nothing addresses the bufnum before the server has data behind it.
sound:       granular ambient pad with an LFTri sub under the grains, plus rhythmic per-note chops of the same voice over the top. Accel opens the pad, rotation drives the pattern.
pitch:       Idle plays a melody from idleNotes — semitone offsets above the sample pitch, selected by y tilt, over a pad held two octaves down. Piece hands pitch to ~onHalf: pad from ctx.voicePool.first, pattern from ctx.voicePool.choose, both wrapped to pitch class + baseMidi 60, so the two engines diverge. Tuning rides the §16 ramp (0.7 → 1.0 over 15 s) onto \freq rather than a \ptch control, converging on A (MIDI 69). Each SynthDef derives rate from freq/srcFreq with a fixed 0.99 detune.
rhythm:      Pad continuous; pattern per-note on ~beatClock with \dur from rrate (2.5 down to 0.5).
instruments: [brownBall]
prints:      [brownBall, blackShaker, brownShaker, boneRod, shinyStick]
seats:       [3, 4, 5]
affinity:    [voice, singing, choir, song, ghost, angels, carnival, carousel, merry go round]
register:    [traditional]
family:      voice
*/

/*

- too dynamic 



*/
var m = ~model;
var ob = ~outBus ? 0;
var group;              // holds padSynth AND per-note pattern synths
var padSynth;
var sampleBuffer;
var baseMidi = 60;
var tuneTime = 0;       // TempoClock.beats captured on \tuning entry (§16 ramp)
var lastTime = 0;

// One sample — loaded MONO (readChannel [0]) because GrainBuf requires
// a mono buffer. samplerVoice reads mono too and pans in the SynthDef.
var samplePath      = "~/Music/cotf_samples/voice/aah.wav";
var samplePitchMidi = 68;   // intrinsic pitch of the file (G#4)

// Idle melody — semitone offsets from the sample pitch, stepped one per
// pattern note. Score pitch takes over in \piece via ~onHalf.
var idleNotes = [0, 4, 5, 7, 11, 7, 5, 4] - 12;
var idleStep = 0;
var lastNoteTime = 0;

m.accelMassFilteredAttack = 0.98;
m.accelMassFilteredDecay  = 0.2;
m.rrateMassFilteredAttack = 0.9999;
m.rrateMassFilteredDecay  = 0.2;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
// GrainBuf `rate` is a pitch ratio — no BufRateScale here.
SynthDef(\grainPad, { |out=0, bufnum=0, amp=0, freq=440, srcFreq=440, gate=1,
    grainDur=0.15, grainDensity=20, grainPos=0.5, grainPosSpread=0.1,
    attack=0.5, release=0.5, ffreq=2000, lagAttack=0.05, lagRelease=0.8|
	var env  = EnvGen.kr(Env.asr(attack, 1, release), gate, doneAction: 2);
	var rate = freq.lag(0.9) / srcFreq * 0.99;
	var trig = Impulse.kr(grainDensity);
	var pos  = grainPos + WhiteNoise.kr(grainPosSpread);
	var sig  = GrainBuf.ar(2, trig, grainDur, bufnum, rate, pos, 2, 0);
	var sub = SinOsc.ar(freq * [1,1.003], 0, 0.1);
	var filt = RLPF.ar(sig, ffreq.lag(0.04), 0.4) + sub;
	Out.ar(out, filt * env * amp.lagud(lagAttack, lagRelease));
}).add;

//------------------------------------------------------------
// PlayBuf `rate` is samples-per-sample — BufRateScale required.
SynthDef(\samplerVoice, { |out=0, bufnum=0, amp=0.5, freq=440, srcFreq=440,
    gate=1, pan=0, attack=0.01, decay=0.1, release=0.3|
	var env = EnvGen.kr(Env.adsr(attack, decay, 0.07, release), gate, doneAction: Done.freeSelf);
	var lr  = (freq.lag(0.4) / srcFreq) * BufRateScale.kr(bufnum) * 0.99;
	var sig = PlayBuf.ar(1, bufnum, rate: lr * [1,1.003], loop: 0);
	Out.ar(out, Pan2.ar(sig, pan, amp * env));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	topEnvironment.use {
		group = Group.new;

		// Read is async — both engines are built in the completion action.
		// A GrainBuf started on an empty bufnum kills scsynth outright.
		sampleBuffer = Buffer.readChannel(s, samplePath.standardizePath, channels: [0], action: {|buf|
			postf("sample loaded (mono ch0): % (% frames, sr %)\n",
				samplePath, buf.numFrames, buf.sampleRate);

			// ~deinit may have run already — it nils sampleBuffer.
			if (sampleBuffer.notNil) {
				topEnvironment.use {
					// padSynth stays OUT of group so ~onResync's freeAll
					// hits only the Pdef's per-note synths.
					padSynth = Synth(\grainPad, [
						\out,     ob,
						\bufnum,  buf.bufnum,
						\srcFreq, samplePitchMidi.midicps,
						\freq,    (samplePitchMidi - 11 + 12).midicps,   // rate = 1 at rest
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

					// §15 reload guard — ~onRoomState only fires on change
					if (~roomState == \tuning, { tuneTime = TempoClock.beats });
				};
			};
		});
	};

	~onResync = { |idx|
		if (group.notNil, {
			topEnvironment.use {
				Pdef(m.ptn).stop;
				s.bind { group.freeAll };   // Pdef synths only
				Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
			};
		});
	};
};

//------------------------------------------------------------
// Refs captured + vars nil'd synchronously so a double-fire no-ops.
~deinit = ~deinit <> {

	var t0 = Main.elapsedTime;
	var tlog = topEnvironment[\airkitTraceLog];
	var tport = ~device !? { |dev| dev.port };
	var g = group;
	var p = padSynth;
	var b = sampleBuffer;
	group = nil;
	padSynth = nil;
	sampleBuffer = nil;
	Pdef(m.ptn).remove;
	if (p.notNil) { p.set(\gate, 0) };
	fork {
		if (g.notNil) {
			s.bind { g.freeAll };
			s.sync;
			g.free;
		};
		2.0.wait;                  // outlast padSynth's release before the free
		if (b.notNil) { b.free };
		tlog !? { |f|
			f.(tport, "deinit.done", "name=SOPRANOVOICE ms=%".format(
				((Main.elapsedTime - t0) * 1000).round.asInteger));
		};
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
// Accel drives pad, rrate drives pattern.
// padSynth is nil until the read lands — Nil:set is a no-op, no guard.
~curveAbove = { |in, thresh = 0.2, inMax = 1.0, outMin = 0.3, outMax = 1.0, curve = -4|
    if (in < thresh) { 0 } {
        in.lincurve(thresh, inMax, outMin, outMax, curve)
    }
};

~idleNext = { |d, ctx|

	var amp = m.rrateMassFiltered.lincurve(0.0, 0.2, -60, -6, -1).dbamp;
	var ampa = m.accelMassFiltered.lincurve(0, 2.0, 0.5, 1, -3).dbamp;
	var dur = m.rrateMassFiltered.lincurve(0, 1.5, 2.5, 0.5, -3);
	var noteStep = m.rrateMassFiltered.lincurve(0, 1.5, 1.2, 0.25, -3);   // seconds per note
	var samp = ~curveAbove.(m.accelMassFiltered, 0.2, 2.0, 0.3, 1.0, -4) ;
	var idx = (d.sensors.gyroEvent.y / pi.half).linlin(-1, 1, 0, idleNotes.size, 1).asInteger;

	var rel = m.accelMassFiltered.lincurve(0.0, 2.5, 0.1, 3.02,1);

	padSynth.set(\amp,          amp * 0.3);
	padSynth.set(\ffreq,        1800);
	padSynth.set(\grainDur,     0.1);
	padSynth.set(\grainDensity, 50);
	padSynth.set(\lagAttack,    0.2);
	padSynth.set(\lagRelease,   1.7);

	// [COTF 2026-08-19] restore the rrate gate on the idle chops (`amp *`) —
	// without it ampa is ~1.06 at REST (accel curve bottoms at 0.5 dB, and
	// .dbamp of that is >1), so a full-volume chop fired every couple of
	// seconds while the stick sat still (caught at the live-flip dry run,
	// seat 5). Rotation still drives the chops, per this file's own header;
	// the 2.0 top from the overnight rework is kept.
	Pdef(m.ptn).set(\amp,   amp * ampa * 2.0);
	Pdef(m.ptn).set(\dur,   dur);
	Pdef(m.ptn).set(\attack,  0.01);
	Pdef(m.ptn).set(\release,  rel);

	if(TempoClock.beats > (lastTime + 5),{
		padSynth.set(\freq, (samplePitchMidi - [11,18].choose).midicps);
		lastTime = TempoClock.beats;
	});


	// Step the melody. The interval follows the same rrate curve as \dur,
	// so one note lands per pattern event however fast it's running.
	// if (TempoClock.beats > (lastNoteTime + noteStep), {
	// 	idleStep = idleStep + 1;
	// 	lastNoteTime = TempoClock.beats;
	// });
	Pdef(m.ptn).set(\freq, (samplePitchMidi + 1 + idleNotes.wrapAt(idx)).midicps);
};

~tuningNext = { |d, ctx|

	var tt      = 15.0;
	var elapsed = TempoClock.beats - tuneTime;
	var ptch    = if (elapsed < tt) {
		(elapsed / tt).linlin(0, 1, 0.7, 1.0)   // flat → true
	} { 1.0 };
	var tuneMidi = 69;
	// var under = m.accelMassFiltered.lincurve(0, 1, m.rrateMassFiltered.neg, 0, -1).neg.lincurve(0, 0.4, 0, 1, -1	);
	var amp = m.rrateMassFiltered.lincurve(0, 0.4, -70, 1, -1).dbamp;
	var dur = m.rrateMassFiltered.lincurve(0, 1.5, 2.5, 0.5, -3);

	padSynth.set(\amp,          m.accelMassFiltered.lincurve(0, 1.0, -80, -8, -3).dbamp);
	padSynth.set(\ffreq,        1200);
	padSynth.set(\grainDur,     0.3);
	padSynth.set(\grainDensity, 12);
	padSynth.set(\lagAttack,    0.1);
	padSynth.set(\lagRelease,   2.1);
	padSynth.set(\freq,         (tuneMidi - 12).midicps * ptch);
	Pdef(m.ptn).set(\amp,  amp * 5);
	Pdef(m.ptn).set(\dur,  dur);
	Pdef(m.ptn).set(\freq, tuneMidi.midicps * ptch);
};

~pieceNext = { |d, ctx|

	var amp = m.rrateMassFiltered.lincurve(0.0, 0.2, -60, -6, -1).dbamp;
	var dur = m.rrateMassFiltered.lincurve(0, 1.8, 2.5, 0.8, -3);

	padSynth.set(\amp,          amp);
	padSynth.set(\ffreq,        (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).lincurve(-0.5, 0.5, 1000, 13000, -2));
	padSynth.set(\grainPos,    rrand(0.05,0.09));
	padSynth.set(\grainDur,     0.2);
	padSynth.set(\grainDensity, 80);

	Pdef(m.ptn).set(\amp, amp * 3);
	Pdef(m.ptn).set(\dur, dur);
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
	var pitcha = ((ctx.voicePool.last.asInteger % 12) + baseMidi).midicps;
	var pitchb = ((ctx.voicePool.last.asInteger % 12) + baseMidi).midicps;
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
