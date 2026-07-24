/*
Skeleton for composing a new personality from a prose spec. See
code3.0/personality_authoring.md for the workflow and phrasebook,
and code3.0/concert_p_files.md for the numbered recipes cited below.

To use: copy to `personalities/cotf_<name>.sc`, fill in every
`// TODO(...)` slot, delete slots you don't need, remove this
docstring. Add the file to `lists/list_cotf.sc`.

Defaults:
- Engine B (Pdef + per-event sampler) — swap block markers below for
  engine A (long-lived synth) or engine D (paused-Pdef + one-shots).
- Standard cleanup, group, reload guard, state-aware ~onResync.
- All state ticks scaffolded but blank — fill in per spec.
*/

/*
// TODO(front-matter): fill in the six keys. See concert_p_files.md §1.
gestures:    [beat, shake, tilt]
description: <one-line technical summary — what it *is*>
sound:       <one-line character summary — what you *hear*>
pitch:       <where notes come from>
rhythm:      <rhythmic behaviour>
instruments: [Lumivox | Gravitone | Velaphone | Aetherharp | Cellaris]
*/

var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init bodies run under topEnvironment.use
var lastTime = 0;     // throttle anchor for one-shot triggers
var tuneTime = 0;     // capture on \tuning entry for time-based envelopes
var group;            // dedicated Group (Pdef personalities need this — §5)

// var synth;         // uncomment for engine A (long-lived synth)
// var samplesLib;    // uncomment for engine B (sampler)

//------------------------------------------------------------
// TODO(sample-loader): if sampler engine, keep this block and set the
// folder path + parser. Delete the whole block for engine A / C.

var noteToMidi = { |noteName|
	var pattern = "([A-G](#|b)?)([0-9])";
	var noteNames = "C C# D D# E F F# G G# A A# B";
	var parts, note, octave, noteIndex;
	parts = noteName.findRegexp(pattern);
	if(parts.size < 3, { Error("Invalid note format: %".format(noteName)).throw});
	note = parts[1][1];
	octave = parts[3][1].asInteger;
	note = note.replace("Cb", "B").replace("Db", "C#").replace("Eb", "D#")
	           .replace("Fb", "E").replace("Gb", "F#").replace("Ab", "G#").replace("Bb", "A#");
	noteIndex = noteNames.split($ ).find([note]);
	(octave + 1) * 12 + noteIndex;
};

// TODO(sample-folder): point at the library. Phrasebook:
//   harp     → ~/Music/cotf_samples/harp
//   celesta  → ~/Music/cotf_samples/Celesta_ES_mf
//   dulcimer → ~/Music/cotf_samples/Celtic Hammered Dulcimer (add sampleFilter "Dlcmr-hrd")
//   marimba  → ~/Music/cotf_samples/... (see marimba1/2 for parser variant)
var folder = PathName("~/Music/cotf_samples/harp");
var samplesLib;

//------------------------------------------------------------
// Filter tuning — smooth the raw sensor signals before use.
// Higher attack = slower rise. Higher decay = slower release. See §9.
// TODO(filter-tuning): tweak if the personality's response feels off.

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.5;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay  = 0.5;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
// TODO(synthdef): pick a voice. Defaults to the standard sampler
// (same SynthDef used across harp1, celesta1, dulcimer1). Swap for
// engine A (\simple from cotf_simple2.sc) or engine C (synth voice)
// as needed.

SynthDef(\stereoSampler, {|bufnum=0, out=0, amp=1, rate=1, ptch=1, start=0, pan=0,
    attack=0.01, decay=0.1, sustain=0.3, release=1.2, gate=1|
	var lr = rate * BufRateScale.kr(bufnum) * ptch;
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
	Out.ar(out, sig);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	// Sample lookup helper — even-MIDI library with odd-note detune.
	// Swap for findClosestSample (§16) if the library is sparse/diatonic.
	var findSampleBuffer = {|note|
		var bufnum;
		samplesLib.do({|sample|
			if(sample.midiNote == note,{ bufnum = sample.buffer });
		});
		bufnum
	};

	samplesLib = folder.entries.collect({ |path|
		var note = path.fileNameWithoutExtension.split($_).last;
		var buffer = Buffer.read(s, path.fullPath, action:{ |buf|
			postf("buffer alloc [%] \n", buf);
		});
		postf("loading sample : % \n", path.fileNameWithoutExtension);
		(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: noteToMidi.(note))
	});

	// Custom event handler — resolves note → bufnum + rate. Standard
	// even/odd fallback. Swap for findClosestSample variant on sparse
	// libraries, or the array-aware form for chord voicings (§16).
	Event.addEventType(\customEvent, {|e|
		~note = (~note + ~root + (12 * ~octave)).asInteger;
		if(~note.odd, {
			~bufnum = findSampleBuffer.(~note - 1);
			~rate = 1.midiratio;
		}, {
			~bufnum = findSampleBuffer.(~note);
			~rate = 1;
		});
		~type = \note;
		currentEnvironment.play;
	});

	topEnvironment.use{
		group = Group.new;

		// TODO(pdef): fill in the pattern. Default: single-note melody
		// from voice pool at baseMidi. Common variants:
		//   - Fixed offset pattern (Pseq / Pfunc reading an array — see dulcimer1)
		//   - Chord voicings (\note as an array — see marimba2)
		Pdef(m.ptn,
			Pbind(
				\instrument, \stereoSampler,
				\out, ob,
				\group, group,   // route into personality's Group (§5)
				\type, \customEvent,
				\note, 0,
				\root, Pfunc { ~scoreVoicePool.choose.wrap(0, 11).asInteger },
			);
		);

		Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		// Sensible starting dur — state ticks override.
		Pdef(m.ptn).set(\dur, 1);

		// TODO(reload-guard): if any state pauses the Pdef (engine D or
		// mixed), mirror that state's entry setup here. Standard idiom:
		//
		// if (~roomState == \tuning) {
		//     Pdef(m.ptn).pause;
		//     tuneTime = TempoClock.beats;
		// };

		// TODO(onResync): state-aware if any state pauses the Pdef —
		// only restart if we're NOT in a paused-Pdef state (§6).
		~onResync = { |idx|
			Pdef(m.ptn).stop;
			s.bind { group.freeAll };
			Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		};
	};
};

//------------------------------------------------------------
// Idempotent cleanup. notNil guards handle titleView off+on reload
// firing ~deinit twice on the same env (§5).
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;

	fork {
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		};
		if (samplesLib.notNil) {
			samplesLib.do({|sample|
				postf("buffer dealloc [%] \n", sample.buffer);
				sample.buffer.free;
				s.sync;
			});
			samplesLib = nil;
		};
	};
};

//------------------------------------------------------------
// Room-state routing. Wrap the switch in topEnvironment.use if any
// branch calls Pdef.resume(~beatClock, quant: ~scoreBeatsPerBar * …)
// or otherwise needs ~beatClock (§7).
~onRoomState = {|ctx|
	switch(ctx.state,
		\idle,    {
			// TODO(state-entry): one-shot logic for entering idle.
		},
		\tuning,  {
			tuneTime = TempoClock.beats;
			// TODO(paused-pdef): if idle/tuning uses accel one-shots (§15),
			// pause + freeAll here:
			//   Pdef(m.ptn).pause;
			//   s.bind { group.freeAll };
		},
		\piece,   {
			// TODO(state-entry)
		},
		\curtain, {
			// TODO(state-entry)
		},
		\silent,  {
			Pdef(m.ptn).set(\amp, 0);
			// TODO(silent-mute): also mute long-lived synth / grain synth
			// if the personality has one:
			//   if (synth.notNil, { synth.set(\amp, 0) });
		}
	);
};

//------------------------------------------------------------
// State-gated ticks. Each fires at ~30 Hz while its state is current.
// Second arg `ctx` = trimmed score snapshot (state, voicePool,
// loudness, sectionId, etc.). See §11 for full ctx fields.
//
// Pick amp curve from §17 palette, trigger from §3.1, pitch from
// §3.2. `\ptch` bend / wander from §16 / §18. Rotation-density
// (§19) via `\dur`. Layered one-shots + Pdef (§20).

~idleNext = {|d, ctx|
	// TODO(idle-behaviour): resolve prose "in idle: …" phrases.
	// Default: quiet, wandering. Palette: `low` amp curve.
	//
	// var amp = m.accelMassFiltered.lincurve(0, 2.0, -70, -25, -4);   // §17 low
	// Pdef(m.ptn).set(\amp, amp.dbamp);
	// Pdef(m.ptn).set(\octave, [4, 5].choose);
	// Pdef(m.ptn).set(\dur, 2);
};

~tuningNext = {|d, ctx|
	// TODO(tuning-behaviour): resolve prose "in tuning: …" phrases.
	// Common: pitch bend or pitch wander. Palette: `mid` curve or `low`.
	//
	// var elapsed = TempoClock.beats - tuneTime;
	// var ptch = if (elapsed < 20.0) {
	//     (elapsed / 20.0).linlin(0, 1, 0.7, 1.0)  // §16 tuning ramp
	// } { 1.0 };
	// Pdef(m.ptn).set(\ptch, ptch);
};

~pieceNext = {|d, ctx|
	// TODO(piece-behaviour): resolve prose "in piece: …" phrases.
	// Common: expressive amp, tilt-driven octave, gesture-driven dur.
	//
	// var amp = m.accelMassFiltered.lincurve(0, 1.5, -70, -8, -1);   // §17 expressive
	// var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 4, 8, 1).asInteger;
	// Pdef(m.ptn).set(\amp, amp.dbamp * ctx.loudness.linlin(0, 1, 0.3, 1.0));
	// Pdef(m.ptn).set(\octave, oct);
	// Pdef(m.ptn).set(\ptch, 1);
};

~curtainNext = {|d, ctx|
	// TODO(curtain-behaviour): sensible default = `faded` curve,
	// wider dur, lower/higher registers per prose.
	//
	// var amp = m.rrateMassFiltered.lincurve(0, 1.0, -80, -30, -4);   // §17 faded
	// Pdef(m.ptn).set(\amp, amp.dbamp);
	// Pdef(m.ptn).set(\dur, 3);
};

// ~silent has no tick — handled inline in ~onRoomState above.

//------------------------------------------------------------
// Beat-aligned hooks. Fill in when the spec references beats /
// bars / sections. All are opt-in — empty stubs are fine.
~onTick    = {|ctx| };
~onHalf    = {|ctx| };
~onBeat    = {|ctx| };
~onBar     = {|ctx| };
~onPhrase  = {|ctx| };
~onSection = {|ctx|
	// TODO(section-shape): swap amp base per section if the spec asks
	// for character shift. Example:
	// switch(ctx.sectionId,
	//     "intro",  { Pdef(m.ptn).set(\amp, -18.dbamp); },
	//     "dev",    { Pdef(m.ptn).set(\amp,  -8.dbamp); },
	// );
};
~onChord   = {|ctx| };
~onKey     = {|ctx| };
~onScale   = {|ctx| };

//------------------------------------------------------------
// Debug plot (composer GUI). Pick what you want to visualise while
// tuning gesture responses. Standard picks:
//   [m.accelMassFiltered]                        — hit intensity
//   [m.rrateMassFiltered]                        — rotation
//   [(d.sensors.gyroEvent.y / pi.half)]          — up/down tilt
~plotMin = -1;
~plotMax = 1;
~plot = { |d, p|
	[m.accelMassFiltered];
};
