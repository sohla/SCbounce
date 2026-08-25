/*
gestures:    [shake, twist, tilt, roll]
description: A voice carried on the wind — shake to swell the choir, twist to place sung notes in time, and keep the stick moving to raise the gale until it whistles in tune
internals:   Three engines. (1) SOPRANOVOICE-shaped long-lived GrainBuf pad (\labWindPad, mono voice sample, amp from accel) and (2) Pdef PlayBuf chops (\labWindNote, amp + dur from rrate) — the pitch content, kept. (3) One long-lived \labWindAir synth: white noise through two wandering BPFs under a 1/f gust envelope (Farnell's wind model), plus a chorused very-narrow BPF whistle (rq 0.012) tuned 2 octaves above the pad's \freq — the aeolian tone speaks above force ~0.32. Wind = turning motion (rrate) max accel-above-restFloor, hard zero at rest in idle; \amp carries the level (-38..-16 dB), the internal wind value only shapes. \freq follows the pad everywhere it is set (idle wander, tuning ramp, ~onHalf voicePool). Core UGens only.
sound:       granular voice-choir pad with sung per-note chops of the same sample, inside a wind field — gusting filtered-noise air that, driven hard, whistles the note the voice is singing
pitch:       exactly SOPRANOVOICE — idle melody from idleNotes offsets above the sample pitch selected by y tilt over a pad an octave-ish down (5-beat wander), tuning rides the 0.7 -> 1.0 ramp onto \freq converging on A, piece hands both engines ctx.voicePool per half-bar (pc + baseMidi 60); the wind whistle tracks pad freq x 4 throughout
rhythm:      pad and wind continuous; chops per-note on ~beatClock with \dur from rrate (2.5 down to 0.5); gusts are stochastic (LFNoise, ~0.1-0.5 Hz)
instruments: [brownBall]
prints:      [brownBall, blackShaker, brownShaker, boneRod, shinyStick]
seats:       [3, 4, 5]
affinity:    [wind, breeze, storm, gale, whistle, air, sky, breath, blow, kite, autumn, voice, ghost]
register:    [traditional]
family:      voice
*/

// LAB_WindVoice — "a voice carried on the wind".
// The two SOPRANOVOICE engines are deliberately kept close to the original (the
// sung pitch content is the point); the wind is a third, buffer-free synth so it
// can live outside the sample read and outside the Pdef's group. Same lab
// weather language as LAB_RainKeys: ROLL the stick to bring the weather in.
// Mix target: unity trim (pitfall 17) — the air bed tops out around -24 dB.

var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init body runs under topEnvironment.use (pitfall 4)
var group;            // per-note pattern synths ONLY — pad and wind stay out so
                      // ~onResync's freeAll can't chop the continuous layers
var padSynth;         // ALWAYS nil-guard .set (pitfall 8 — nil.set kills the tick loop)
var windSynth;        // ditto
var sampleBuffer;
var baseMidi = 60;
var tuneTime = 0;     // TempoClock.beats captured on \tuning entry (§16 ramp)
var lastTime = 0;

// One sample — loaded MONO (readChannel [0]) because GrainBuf requires a mono
// buffer. \labWindNote reads mono too and pans in the SynthDef.
var samplePath      = "~/Music/cotf_samples/voice/aah.wav";
var samplePitchMidi = 68;   // intrinsic pitch of the file (G#4)

// Idle melody — semitone offsets from the sample pitch, selected by y tilt.
// Score pitch takes over in \piece via ~onHalf.
var idleNotes = [0, 4, 5, 7, 11, 7, 5, 4] - 12;

m.accelMassFilteredAttack = 0.98;
m.accelMassFilteredDecay  = 0.2;
m.rrateMassFilteredAttack = 0.9999;
m.rrateMassFilteredDecay  = 0.2;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
// GrainBuf `rate` is a pitch ratio — no BufRateScale here.
SynthDef(\labWindPad, { |out=0, bufnum=0, amp=0, freq=440, srcFreq=440, gate=1,
	grainDur=0.15, grainDensity=20, grainPos=0.5, grainPosSpread=0.1,
	attack=0.5, release=0.5, ffreq=2000, lagAttack=0.05, lagRelease=0.8|
	var env  = EnvGen.kr(Env.asr(attack, 1, release), gate, doneAction: 2);
	var rate = freq.lag(0.9) / srcFreq * 0.99;
	var trig = Impulse.kr(grainDensity);
	var pos  = grainPos + WhiteNoise.kr(grainPosSpread);
	var sig  = GrainBuf.ar(2, trig, grainDur, bufnum, rate, pos, 2, 0);
	var sub  = SinOsc.ar(freq * [1, 1.003], 0, 0.1);
	var filt = RLPF.ar(sig, ffreq.lag(0.04), 0.4) + sub;
	Out.ar(out, filt * env * amp.lagud(lagAttack, lagRelease));
}).add;

//------------------------------------------------------------
// PlayBuf `rate` is samples-per-sample — BufRateScale required.
SynthDef(\labWindNote, { |out=0, bufnum=0, amp=0.5, freq=440, srcFreq=440,
	gate=1, pan=0, attack=0.01, decay=0.1, release=0.3|
	var env = EnvGen.kr(Env.adsr(attack, decay, 0.07, release), gate, doneAction: Done.freeSelf);
	var lr  = (freq.lag(0.4) / srcFreq) * BufRateScale.kr(bufnum) * 0.99;
	var sig = PlayBuf.ar(1, bufnum, rate: lr * [1, 1.003], loop: 0);
	Out.ar(out, Pan2.ar(sig, pan, amp * env));
}).add;

//------------------------------------------------------------
// The wind — buffer-free, long-lived. Farnell's model: noise through slowly
// wandering band-passes, intensity under a 1/f-ish gust envelope; the whistle
// is a chorused pair of very narrow band-passes (the aeolian tone) tuned to
// freq x 4 — two octaves above the pad — that only speaks when force is high.
SynthDef(\labWindAir, { |out=0, amp=0, force=0, freq=233, gate=1|
	var env    = EnvGen.kr(Env.asr(0.8, 1, 0.2), gate, doneAction: 2);
	var f      = force.lag(0.7).clip(0, 1);
	var gust   = LFNoise1.kr(0.13).range(0.35, 1.0) * LFNoise2.kr(0.47).range(0.65, 1.0);
	var wind   = (f * gust).clip(0, 1);
	var n      = WhiteNoise.ar(1 ! 2);
	var c1     = LFNoise2.kr(0.18).range(280, 850) * (1 + wind);
	var c2     = LFNoise2.kr(0.11).range(900, 2600) * (1 + (wind * 0.5));
	var bed    = (BPF.ar(n, c1, 0.7) * 1.7) + (BPF.ar(n, c2, 0.5) * 0.8);
	var wf     = (freq.lag(1.2) * 4).clip(200, 6000) * LFNoise2.kr(0.3).range(0.996, 1.004);
	// [COTF 2026-08-25 feedback — "can't hear any wind"] the first version
	// double-attenuated: \amp mapped to -54..-24 dB externally AND the bed
	// scaled by wind^1.5 internally (another -8..-40 dB), so even a full gale
	// sat ~-32 dB. Now \amp carries the LEVEL and wind only SHAPES (0.25-1.0
	// tilt), and the whistle threshold drops 0.5 -> 0.32 — the gust envelope
	// averages ~0.55 at full force, so 0.5 was above its own ceiling most of
	// the time and the aeolian tone almost never spoke.
	var wAmt   = ((wind - 0.32).max(0) * 2.2).lag(0.5);
	var whistle = (BPF.ar(n, wf * 0.998, 0.012) + BPF.ar(n, wf * 1.002, 0.012)) * 34 * wAmt;
	var sig    = (bed * (0.25 + (0.75 * wind)) * 0.9) + whistle;
	// [COTF 2026-08-25 round 2] asymmetric level lag — wind rises in ~0.7 s and
	// dies away over ~3 s ("attack and release too sudden"), short enough not
	// to smear across piece dynamics.
	Out.ar(out, sig * env * amp.lagud(0.7, 3.0));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	topEnvironment.use {
		group = Group.new;

		// Read is async — pad + Pdef are built in the completion action.
		// A GrainBuf started on an empty bufnum kills scsynth outright.
		sampleBuffer = Buffer.readChannel(s, samplePath.standardizePath, channels: [0], action: {|buf|
			postf("[LAB_WindVoice] sample loaded (mono ch0): % (% frames)\n",
				samplePath, buf.numFrames);

			// ~deinit may have run already — it nils sampleBuffer.
			if (sampleBuffer.notNil) {
				topEnvironment.use {
					// pad stays OUT of group so ~onResync's freeAll
					// hits only the Pdef's per-note synths.
					padSynth = Synth(\labWindPad, [
						\out,     ob,
						\bufnum,  buf.bufnum,
						\srcFreq, samplePitchMidi.midicps,
						\freq,    (samplePitchMidi - 11 + 12).midicps,
						\amp,     0,
						\attack,  0.5,
						\release, 1.5,
					]);

					Pdef(m.ptn,
						Pbind(
							\instrument, \labWindNote,
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

		// The wind needs no buffer — born now, born silent; ticks raise it.
		windSynth = Synth(\labWindAir, [\out, ob, \amp, 0, \force, 0,
			\freq, (samplePitchMidi - 11 + 12).midicps]);
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
// Refs captured + vars nil'd synchronously so a double-fire no-ops
// (SOPRANOVOICE's reference teardown). Gates close synchronously — silent
// within 200 ms; the 2.0.wait outlasts the pad's 1.5 s release before the
// buffer free (freeing under a live GrainBuf kills the server).
~deinit = ~deinit <> {
	var g = group;
	var p = padSynth;
	var w = windSynth;
	var b = sampleBuffer;
	group = nil;
	padSynth = nil;
	windSynth = nil;
	sampleBuffer = nil;
	Pdef(m.ptn).remove;
	if (p.notNil) { p.set(\gate, 0) };
	if (w.notNil) { w.set(\gate, 0) };
	fork {
		if (g.notNil) {
			s.bind { g.freeAll };
			s.sync;
			g.free;
		};
		2.0.wait;                  // outlast the pad's release before the free
		if (b.notNil) { b.free };
		postf("[LAB_WindVoice] deinit done \n");
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
			// Three engines, three mutes — \silent has no tick hook to catch
			// the one you forgot.
			if (padSynth.notNil)  { padSynth.set(\amp, 0) };
			if (windSynth.notNil) { windSynth.set(\amp, 0) };
			Pdef(m.ptn).set(\amp, 0);
		}
	);
};

//------------------------------------------------------------
// Threshold-then-curve helper: hard zero below thresh (a curve's bottom is
// not necessarily silence — SOPRANOVOICE's 2026-08-19 scar).
~curveAbove = { |in, thresh = 0.2, inMax = 1.0, outMin = 0.3, outMax = 1.0, curve = -4|
	if (in < thresh) { 0 } {
		in.lincurve(thresh, inMax, outMin, outMax, curve)
	}
};

// Accel drives the pad, rrate drives the chops (SOPRANOVOICE's two axes), and
// ROLL (|gyroXFiltered|) drives the wind — with a play-energy floor so the air
// stirs whenever the performer does. A still, level stick = near-still air.
~idleNext = { |d, ctx|
	var amp  = m.rrateMassFiltered.lincurve(0.0, 0.2, -60, -6, -1).dbamp;
	var ampa = m.accelMassFiltered.lincurve(0, 2.0, 0.5, 1, -3).dbamp;
	var dur  = m.rrateMassFiltered.lincurve(0, 1.5, 2.5, 0.5, -3);
	var idx  = (d.sensors.gyroEvent.y / pi.half).linlin(-1, 1, 0, idleNotes.size, 1).asInteger;
	var rel  = m.accelMassFiltered.lincurve(0.0, 2.5, 0.1, 3.02, 1);
	// [COTF 2026-08-25] motion-based wind, silent at rest (same driver shape as
	// LAB_RainKeys): rotation rate or accel above the gravity restFloor — the
	// roll-ORIENTATION driver could hold wind on a stick lying rolled.
	var spin = (m.rrateMassFiltered - 0.05).max(0).clip(0, 1);
	var energy = ((m.accelMassFiltered - 0.25).max(0) * 0.8).clip(0, 1);
	var wind = max(spin, energy);

	if (padSynth.notNil) {
		padSynth.set(\amp,          amp * 0.3);
		padSynth.set(\ffreq,        1800);
		padSynth.set(\grainDur,     0.1);
		padSynth.set(\grainDensity, 50);
		padSynth.set(\lagAttack,    0.2);
		padSynth.set(\lagRelease,   1.7);
	};

	if (windSynth.notNil) {
		if (wind < 0.02) {
			windSynth.set(\amp, 0, \force, 0);
		} {
			windSynth.set(\amp, wind.lincurve(0, 1, -38, -16, 1).dbamp);
			windSynth.set(\force, wind);
		};
	};

	// rrate gate on the chops (amp *) — the accel curve bottoms ABOVE unity
	// dbamp, so without the gate a full chop fires off a still stick
	// (SOPRANOVOICE's live-flip scar, kept verbatim).
	Pdef(m.ptn).set(\amp,     amp * ampa * 2.0);
	Pdef(m.ptn).set(\dur,     dur);
	Pdef(m.ptn).set(\attack,  0.01);
	Pdef(m.ptn).set(\release, rel);

	if(TempoClock.beats > (lastTime + 5),{
		var padMidi = samplePitchMidi - [11, 18].choose;
		if (padSynth.notNil)  { padSynth.set(\freq, padMidi.midicps) };
		if (windSynth.notNil) { windSynth.set(\freq, padMidi.midicps) };
		lastTime = TempoClock.beats;
	});

	Pdef(m.ptn).set(\freq, (samplePitchMidi + 1 + idleNotes.wrapAt(idx)).midicps);
};

// Tuning: SOPRANOVOICE's ramp (0.7 -> 1.0 over 15 beats onto \freq, converging
// on A); the air falls to a calm breath, whistle drifting toward A with the pad.
~tuningNext = { |d, ctx|
	var tt      = 15.0;
	var elapsed = TempoClock.beats - tuneTime;
	var ptch    = if (elapsed < tt) { (elapsed / tt).linlin(0, 1, 0.7, 1.0) } { 1.0 };
	var tuneMidi = 69;
	var amp = m.rrateMassFiltered.lincurve(0, 0.4, -70, 1, -1).dbamp;
	var dur = m.rrateMassFiltered.lincurve(0, 1.5, 2.5, 0.5, -3);

	if (padSynth.notNil) {
		padSynth.set(\amp,          m.accelMassFiltered.lincurve(0, 1.0, -80, -8, -3).dbamp);
		padSynth.set(\ffreq,        1200);
		padSynth.set(\grainDur,     0.3);
		padSynth.set(\grainDensity, 12);
		padSynth.set(\lagAttack,    0.1);
		padSynth.set(\lagRelease,   2.1);
		padSynth.set(\freq,         (tuneMidi - 12).midicps * ptch);
	};
	if (windSynth.notNil) {
		windSynth.set(\amp, -34.dbamp);
		windSynth.set(\force, 0.3);
		windSynth.set(\freq, (tuneMidi - 12).midicps * ptch);
	};
	Pdef(m.ptn).set(\amp,  amp * 5);
	Pdef(m.ptn).set(\dur,  dur);
	Pdef(m.ptn).set(\freq, tuneMidi.midicps * ptch);
};

// Piece: SOPRANOVOICE behaviour; the wind's floor comes from the score's
// loudness so tuttis bring the gale even before the performer rolls into it.
// Roll drives BOTH the pad's brightness (x-tilt cutoff, as the original) and
// the wind force — deliberately correlated: leaning into the wind brightens it.
~pieceNext = { |d, ctx|
	var amp  = m.rrateMassFiltered.lincurve(0.0, 0.2, -60, -6, -1).dbamp;
	var dur  = m.rrateMassFiltered.lincurve(0, 1.8, 2.5, 0.8, -3);
	var loud = (ctx.loudness ? 0.3).clip(0, 1);
	var spin = (m.rrateMassFiltered - 0.05).max(0).clip(0, 1);
	var energy = ((m.accelMassFiltered - 0.25).max(0) * 0.8).clip(0, 1);
	var wind = max(max(spin, energy), loud * 0.5);

	if (padSynth.notNil) {
		padSynth.set(\amp,          amp);
		padSynth.set(\ffreq,        (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).lincurve(-0.5, 0.5, 1000, 13000, -2));
		padSynth.set(\grainPos,     rrand(0.05, 0.09));
		padSynth.set(\grainDur,     0.2);
		padSynth.set(\grainDensity, 80);
	};
	if (windSynth.notNil) {
		windSynth.set(\amp, wind.lincurve(0, 1, -42, -16, 1).dbamp);
		windSynth.set(\force, wind);
	};
	Pdef(m.ptn).set(\amp, amp * 3);
	Pdef(m.ptn).set(\dur, dur);
};

// Curtain: the voices settle; a dying breeze holds under them, whistle gone
// (force pinned below the whistle threshold).
~curtainNext = { |d, ctx|
	if (padSynth.notNil) {
		padSynth.set(\amp,   m.accelMassFiltered.lincurve(0, 1.0, -80, -30, -4).dbamp);
		padSynth.set(\ffreq, 400);
	};
	if (windSynth.notNil) {
		windSynth.set(\amp, -36.dbamp);
		windSynth.set(\force, 0.35);
	};
	Pdef(m.ptn).set(\amp, m.rrateMassFiltered.lincurve(0, 1.0, -80, -22, -4).dbamp);
	Pdef(m.ptn).set(\dur, m.rrateMassFiltered.lincurve(0, 1.0, 3, 1, -1));
};

//------------------------------------------------------------
// All three engines' freq tracks the score per half-bar — pad and chops as
// SOPRANOVOICE (pc + baseMidi), the whistle riding the same value. s.bind so
// the paired sets land on one latency timeline (no flam between engines).
~onHalf = { |ctx|
	var pitch = ((ctx.voicePool.last.asInteger % 12) + baseMidi).midicps;
	s.bind {
		if (padSynth.notNil)  { padSynth.set(\freq, pitch) };
		if (windSynth.notNil) { windSynth.set(\freq, pitch) };
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
~plot = { |d, p|
	// [wind drive (rrate), pad drive (accel)]
	[m.rrateMassFiltered, m.accelMassFiltered];
};
