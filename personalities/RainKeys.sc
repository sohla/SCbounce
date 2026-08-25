/*
gestures:    [beat, shake, tilt, roll]
description: A harp playing in the rain — notes come faster and thicker the more you play, tilt to move up and down the strings, keep the stick turning to bring the downpour in
internals:   Two engines. (1) JUPITERSHARP-shaped Pdef firing per-note harp sample synths (\labRainPluck, harp library, odd/even sparse-set resolver) — the pitched content, kept prominent. (2) One long-lived \labRainWeather synth: pink-noise rain bed + Dust-triggered broadband surface patter + four tuned droplet voices (Latch/Select freq, 0.94 -> 1.0 exp chirp per Farnell's bubble model, perc decay) whose f1-f4 follow the harp's idle transposition, ctx.voicePool on each half-bar in piece, and A in tuning. Rain = turning motion (rrate) max accel-above-restFloor, hard zero at rest. Patch self-pads -4 dB (Ciaran 2026-08-25) — propose ~cotfPatchTrims 1.0 at promotion. Core UGens only.
sound:       harp samples from ~/Music/cotf_samples/harp over a rain field — soft hiss bed, surface patter, and pitched droplets that land in tune with the harp
pitch:       harp exactly as JUPITERSHARP (\root from voice pool, octave from y tilt 5-7 idle / 5-8 piece, idle \ptch transposition table, tuning ramp 0.7 -> 1.0); droplets latch f1-f4 = idle transposition + [69,74,76,81], top of ctx.voicePool folded to midi 76-93 per half-bar in piece, A (880/1760) in tuning
rhythm:      harp one note per \dur clock tick, dur 0.5/1/2 by amp tier; rain is stochastic (Dust) — density from turning motion + play energy above rest (hard-silent at rest in idle), drizzle in tuning, thins to lone drips in curtain
instruments: [greenHolder]
prints:      [thinViolin, compactViolin, greenHolder, whiteWing, brownBall]
seats:       [2, 3, 4, 5]
affinity:    [rain, water, drops, drizzle, storm, puddle, river, weather, shower, mist, harp, piano, keys]
register:    [traditional]
family:      harp
*/

// LAB_RainKeys — "a harp playing in the rain".
// Pitched content is JUPITERSHARP's harp engine, deliberately kept close to the
// original (that sound is the point); the weather layer is one long-lived synth
// so the rain never depends on the pattern player and can outlast the harp in
// curtain. Mix target: unity trim (pitfall 17) — the bed tops out around -26 dB
// so the harp always reads on top.

var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init bodies run under topEnvironment.use (pitfall 4)
var lastTime = 0;     // \dur tier rate-limit window
var freqTime = 0;     // droplet-freq set rate-limit window (idle)
var tuneTime = 0;
var group;            // dedicated Group — the Pdef spawns synths (patterns.md §3)
var weather;          // the one long-lived \labRainWeather synth — ALWAYS nil-guard .set (pitfall 8)
var loading = false;  // ~init cancellation flag (pitfall 5)

var noteToMidi = { |noteName|
	var pattern = "([A-G](#|b)?)([0-9])";
	var noteNames = "C C# D D# E F F# G G# A A# B";
	var parts, note, octave, noteIndex;
	parts = noteName.findRegexp(pattern);
	if(parts.size < 3, { Error("Invalid note format: %".format(noteName)).throw });
	note = parts[1][1];
	octave = parts[3][1].asInteger;
	note = note.replace("Cb", "B").replace("Db", "C#").replace("Eb", "D#")
	           .replace("Fb", "E").replace("Gb", "F#").replace("Ab", "G#").replace("Bb", "A#");
	noteIndex = noteNames.split($ ).find([note]);
	(octave + 1) * 12 + noteIndex;
};

var folder = PathName("~/Music/cotf_samples/harp");
var samplesLib;

var eventTypeName = (\labRainKeysEvt_ ++ m.ptn).asSymbol;

// The idle droplet chord, as MIDI, before the harp's \ptch transposition is
// added: A5, D6, E6, A6 — open fourths/fifths so the drips colour the harp
// without pinning it to a triad.
var idleDripMidi = [69, 74, 76, 81];

// Fold a MIDI note into the droplet sweet spot (~660-1760 Hz). Below midi 76
// drips read as "tone", above 93 as "tick" — this register is where the
// chirped-sine bubble model actually sounds like water.
var dripFold = { |midi|
	var n = midi.asInteger;
	while({ n > 93 }, { n = n - 12 });
	while({ n < 76 }, { n = n + 12 });
	n
};

//------------------------------------------------------------

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.1;
m.rrateMassFilteredAttack = 0.999;
m.rrateMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
// The harp voice — JUPITERSHARP's \stereoSampler, renamed (banned.synthdef-collision),
// otherwise identical: BufRateScale for tune-safety across sample rates, ~3-cent
// channel detune, FreqShift sparkle.
SynthDef(\labRainPluck, {|bufnum=0, out=0, amp=1, rate=1, start=0, pan=0, freq=440, ptch=1,
	attack=0.01, decay=0.1, sustain=0.3, release=1.7, gate=1, cutoff=20000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum) * ptch * 0.5;
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
	var sparkle = FreqShift.ar(sig, freq * 0.51 * ptch, 0, 0.3);
	Out.ar(out, (sig + sparkle) * amp * env * 0.631);   // -4 dB patch pad (Ciaran 2026-08-25)
}).add;

// The weather — one long-lived synth, three layers:
//   bed    : pink noise, LP wander 2.2-5.2 kHz + HP 420, slow undulation — "rain outside"
//   patter : Dust-triggered white-noise bursts through fixed BPFs — "drops on the roof"
//   drips  : 4 voices, Dust-triggered chirped sines (0.94 -> 1.0 exp over 45 ms,
//            perc decay) at latched f1-f4 — "drops in the water", in tune
// dens drives drips AND patter; bed is its own level. All controls lagged so the
// 30 Hz tick sets never zipper.
SynthDef(\labRainWeather, {|out=0, amp=0, bed=0, dens=0, f1=880, f2=1174.7, f3=1318.5, f4=1760, gate=1|
	var env = EnvGen.kr(Env.asr(0.6, 1, 0.15), gate, doneAction: 2);
	var ampL = amp.lag(0.12);
	var bedL = bed.lag(0.5);
	var densL = dens.lag(0.3);
	var wander = LFNoise2.kr(0.25).range(2200, 5200);
	var bedSig = HPF.ar(LPF.ar(PinkNoise.ar(1 ! 2), wander), 420)
	           * LFNoise2.kr(0.13).range(0.65, 1.0);
	// [COTF 2026-08-25 feedback] no Dust floors — dens 0 must mean genuinely no
	// drops (the +0.4/+0.02 constants kept the rain ticking on a still stick);
	// patter gain 3 -> 1.2 and decay 30 -> 18 ms, drip attack 1.5 -> 6 ms and
	// level variance halved ("slaps, not gentle").
	var pat = Mix.fill(2, { |i|
		var t = Dust.ar(densL * 1.2);
		var burst = WhiteNoise.ar * Decay2.ar(t, 0.001, 0.018);
		Pan2.ar(BPF.ar(burst, [4300, 6100].at(i), 0.5) * 1.2, (i * 1.4) - 0.7);
	});
	var drips = Mix.fill(4, { |i|
		var trig = Dust.kr(densL * 0.25);
		var base = Latch.kr(Select.kr(TIRand.kr(0, 3, trig), [f1, f2, f3, f4]), trig);
		var bend = EnvGen.kr(Env([0.94, 1.0], [0.045], \exp), trig);
		var aenv = EnvGen.kr(Env.perc(0.006, 0.16), trig) * TRand.kr(0.12, 0.5, trig);
		Pan2.ar(SinOsc.ar(base.lag(0.02) * bend) * aenv, TRand.kr(-0.7, 0.7, trig));
	});
	var sig = (drips * 0.4) + (pat * (0.15 + (bedL * 2))) + (bedSig * bedL);
	Out.ar(out, sig * ampL * env * 0.631);   // -4 dB patch pad (Ciaran 2026-08-25)
}).add;

//------------------------------------------------------------
~init = ~init <> {

	var findSampleBuffer = {|note|
		var bufnum;
		samplesLib.do({|sample|
			if(sample.midiNote == note,{ bufnum = sample.buffer; });
		});
		bufnum
	};

	loading = true;

	// §12 reload guard: a load landing mid-\tuning never sees the edge.
	if (topEnvironment[\roomState] == \tuning, { tuneTime = TempoClock.beats });

	samplesLib = folder.entries.collect({ |path|
		var note = path.fileNameWithoutExtension.split($_).last;
		var buffer = Buffer.read(s, path.fullPath, action:{ |buf|
			postf("buffer alloc [%] \n", buf);
		});
		(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: noteToMidi.(note))
	});

	s.sync;
	postf("[LAB_RainKeys] all buffers loaded (%) \n", samplesLib.size);

	if(loading.not or: { samplesLib.isNil },{
		postf("[LAB_RainKeys] load cancelled — unloaded while samples were loading \n");
	},{
		// Name any file that didn't come back. Build anyway: one bad sample
		// costs its own notes, not the seat.
		samplesLib.do({ |sample|
			if(sample.buffer.numFrames.isNil or: { sample.buffer.numFrames == 0 },{
				postf("[LAB_RainKeys] sample failed to load : % \n", sample.name);
			});
		});

		// Sparse-library resolver — odd MIDI borrows the note below, resampled
		// up a semitone (JUPITERSHARP's idiom).
		Event.addEventType(eventTypeName, {|e|
			~note = (~note + ~root + (12 * ~octave)).asInteger;
			~freq = ~note.midicps;
			if(~note.odd,{
				~bufnum = findSampleBuffer.(~note - 1);
				~rate = 1.midiratio;
			},{
				~bufnum = findSampleBuffer.(~note);
				~rate = 1;
			});
			~type = \note;
			currentEnvironment.play;
		});

		// The weather synth is born silent; state ticks raise it. Created
		// outside topEnvironment.use so this env's captures stay obvious —
		// ob was captured at file top either way.
		weather = Synth(\labRainWeather, [\out, ob, \amp, 0, \bed, 0, \dens, 0]);

		topEnvironment.use{
			group = Group.new;

			Pdef(m.ptn,
				Pbind(
					\instrument, \labRainPluck,
					\out, ob,
					\group, group,   // route every event's synth into our group
					\type, eventTypeName,
					\note, 0,
					\root, Pfunc { ~scoreVoicePool.choose.wrap(0, 11).asInteger },
					\args, #[]
				);
			);

			Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
			// Seed envir so a stickless seat is silent (Event default amp is 0.1).
			Pdef(m.ptn).set(\amp, 0, \dur, 2);
		};

		~onResync = { |idx|
			topEnvironment.use {
				Pdef(m.ptn).stop;
				s.bind { group.freeAll };
				Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
			};
		};
	});
};

//------------------------------------------------------------
// Teardown — SOPRANOVOICE's idempotent shape: capture refs, nil the file vars
// SYNCHRONOUSLY, then fork the slow part. Weather releases via gate (0.15 s),
// never .free — silent within 200 ms either way.
~deinit = ~deinit <> {
	var g = group;
	var lib = samplesLib;
	var ws = weather;

	loading = false;
	group = nil;
	samplesLib = nil;
	weather = nil;

	Pdef(m.ptn).remove;
	Event.eventTypes.removeAt(eventTypeName);

	if (ws.notNil) { ws.set(\gate, 0) };

	fork {
		if (g.notNil) {
			s.bind { g.freeAll };
			s.sync;                    // wait for /g_freeAll before freeing the group
			g.free;
		};
		if (lib.notNil) {
			0.5.wait;                  // outlast the last pluck's release tail
			lib.do({|sample|
				sample.buffer.free;
				s.sync;
			});
		};
		postf("[LAB_RainKeys] deinit done \n");
	};
};

//------------------------------------------------------------
// Room-state edges. Pdef + weather stay running everywhere; states set levels.
~onRoomState = {|ctx|
	switch(ctx.state,
		\idle,    { Pdef(m.ptn).set(\ptch, 1.0) },
		\tuning,  {
			tuneTime = TempoClock.beats;
			if (weather.notNil) { weather.set(\f1, 880, \f2, 880, \f3, 1760, \f4, 1760) };
		},
		\piece,   { Pdef(m.ptn).set(\ptch, 1.0) },
		\curtain, { },
		\silent,  {
			Pdef(m.ptn).set(\amp, 0);
			if (weather.notNil) { weather.set(\amp, 0) };
		}
	);
};

//------------------------------------------------------------
// Idle: JUPITERSHARP's harp behaviour, plus weather. [COTF 2026-08-25 feedback]
// Rain is now MOTION-gated, hard zero at rest: turning motion (rrate) or accel
// energy above the ~0.2 gravity restFloor (BIBLE §6) — the previous
// |gyroXFiltered| driver was roll ORIENTATION, so a stick lying rolled rained
// forever, and raw accel's gravity floor kept a drizzle on a still stick.
// Droplet freqs follow the harp's \ptch transposition so drips land in tune.
~idleNext = {|d, ctx|
	var amp = (m.accelMassFiltered).lincurve(0, 1.5, -90, -24, -1);
	var notes = [0, 2, 5, 7, 11, 12, 14, 16];
	var n = m.gyroYFiltered.lincurve(-1.0, 1.0, 0, notes.size, -1).asInteger.clip(0, notes.size - 1);
	var oct = (d.sensors.gyroEvent.y / pi.half).linlin(-1, 1, 5, 7).asInteger;
	var spin = (m.rrateMassFiltered - 0.05).max(0).clip(0, 1);
	var energy = ((m.accelMassFiltered - 0.25).max(0) * 0.7).clip(0, 1);
	var rain = max(spin, energy);

	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\ptch, notes[n].midiratio);
	Pdef(m.ptn).set(\amp, amp.dbamp);

	if (weather.notNil) {
		weather.set(\amp, 1);
		if (rain < 0.02) {
			// Genuinely silent at rest — no bed, no drops.
			weather.set(\dens, 0, \bed, 0);
		} {
			weather.set(
				\dens, rain.lincurve(0, 1, 0.6, 12, 2),
				\bed, rain.lincurve(0, 1, -50, -28, 2).dbamp
			);
		};
		// Re-tune the drips to the current transposition, rate-limited.
		if (TempoClock.beats > (freqTime + 0.5), {
			var dm = idleDripMidi + notes[n];
			weather.set(\f1, dm[0].midicps, \f2, dm[1].midicps, \f3, dm[2].midicps, \f4, dm[3].midicps);
			freqTime = TempoClock.beats;
		});
	};

	case(
		{ amp > -10.1 }, {
			if(TempoClock.beats > (lastTime + 0.25),{
				Pdef(m.ptn).set(\dur, 0.5);
				lastTime = TempoClock.beats;
			})},
		{ amp > -25 }, {
			if(TempoClock.beats > (lastTime + 0.5),{
				Pdef(m.ptn).set(\dur, 1.0);
				lastTime = TempoClock.beats;
			})},
		{ Pdef(m.ptn).set(\dur, 2) });
};

// Tuning: sparse harp converging on A (JUPITERSHARP's ramp), rain thinned to a
// drizzle with A-pinned drips (set on the \tuning edge and in ~init's reload guard).
~tuningNext = {|d, ctx|
	var amp = (m.accelMassFiltered).lincurve(0, 1.0, -80, -24, -4);
	var dur = (m.accelMassFiltered).lincurve(0, 3.0, 4.0, 1.0, -4);
	var tt = 15.0;
	var elapsed = TempoClock.beats - tuneTime;
	var ptch = if (elapsed < tt) { (elapsed / tt).linlin(0, 1, 0.7, 1.0) } { 1.0 };

	Pdef(m.ptn).set(\octave, 5);
	Pdef(m.ptn).set(\root, 0);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\ptch, ptch);
	Pdef(m.ptn).set(\dur, dur);

	if (weather.notNil) {
		weather.set(\amp, 1, \dens, 1.2, \bed, -48.dbamp);
	};
};

// Piece: harp as JUPITERSHARP; the weather follows the score's loudness as its
// floor and the performer's rain gesture on top — a loud tutti brings the storm.
// Droplet freqs are re-tuned per half-bar in ~onHalf (fresh voicePool there).
~pieceNext = {|d, ctx|
	var amp = (m.accelMassFiltered).lincurve(0, 1.5, -80, -14, -2);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 5, 8, 1).asInteger;
	var loud = (ctx.loudness ? 0.3).clip(0, 1);
	// [COTF 2026-08-25] motion-gated like idle; the score's loudness stays as a
	// deliberate floor DURING the piece (the storm belongs to the music there).
	var spin = (m.rrateMassFiltered - 0.05).max(0).clip(0, 1);
	var rain = max(spin, loud * 0.6);

	// [COTF 2026-08-25 round 2] hard zero below the gravity restFloor — the
	// curve's bottom at accel ~0.2 (still stick) was still an audible note.
	Pdef(m.ptn).set(\amp, if (m.accelMassFiltered < 0.25) { 0 } { amp.dbamp });
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\ptch, 1);

	if (weather.notNil) {
		weather.set(
			\amp, 1,
			\dens, rain.lincurve(0, 1, 1.2, 14, 2),
			\bed, rain.lincurve(0, 1, -50, -28, 2).dbamp
		);
	};

	case(
		{ amp > -10.001 }, {
			if(TempoClock.beats > (lastTime + 0.5),{
				Pdef(m.ptn).set(\dur, 0.5);
				lastTime = TempoClock.beats;
			})},
		{ amp > -20 }, {
			if(TempoClock.beats > (lastTime + 0.5),{
				Pdef(m.ptn).set(\dur, 1.0);
				lastTime = TempoClock.beats;
			})},
		{ Pdef(m.ptn).set(\dur, 2) });
};

// Curtain: the harp thins out; the rain stays a moment longer, then it is just
// drips — the storm walking away.
~curtainNext = {|d, ctx|
	var amp = ((m.rrateMassFiltered) * 2.0).lincurve(0, 1.0, -80, -35, -4);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\dur, 3);
	if (weather.notNil) {
		weather.set(\amp, 1, \dens, 1.0, \bed, -46.dbamp);
	};
};

//------------------------------------------------------------
// Beat hooks. ~onHalf re-tunes the droplets from the fresh half-bar voice pool
// (top-of-pool picks, dulcimer-style offset-from-top, folded into the drip
// register) — only meaningful during transport, which is the only time these fire.
~onTick    = {|ctx| };
~onHalf    = {|ctx|
	if (weather.notNil and: { ctx.notNil }) {
		var pool = ctx.voicePool ? [69];
		var dm = 4.collect({ |i| dripFold.(pool.wrapAt(pool.size - 1 - i)) });
		weather.set(\f1, dm[0].midicps, \f2, dm[1].midicps, \f3, dm[2].midicps, \f4, dm[3].midicps);
	};
};
~onBeat    = {|ctx| };
~onBar     = {|ctx| };
~onPhrase  = {|ctx| };
~onSection = {|ctx| };
~onChord   = {|ctx| };
~onKey     = {|ctx| };
~onScale   = {|ctx| };

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d, p|
	// [rain gesture (turning motion), play energy]
	[m.rrateMassFiltered, m.accelMassFiltered];
};
