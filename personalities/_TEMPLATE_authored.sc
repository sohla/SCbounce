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
var ob = ~outBus ? 0; // capture NOW — ~outBus lives in d.env (this env), and
                      // the Pbind below is built inside topEnvironment.use{}
                      // where a bare ~outBus would read nil
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
var loading = false;   // load-cancel flag — see the s.sync barrier in ~init.
                       // ~init sets it true before the reads; ~deinit clears it so
                       // an unload landing mid-load makes ~init bail instead of
                       // building a Pdef the next ~deinit can't reach.

//------------------------------------------------------------
// GOTCHA: Event.addEventType(\name, ...) registers the handler on a
// class-level dict (Event.eventTypes). If every sampler personality
// registers under the SAME name (e.g. \customEvent), whichever loads
// LAST clobbers the others' handlers — including any already running
// on other devices. The closure captures THIS env's samplesLib, so
// device 1 (loaded earlier) will play device 2's samples the moment
// device 2 loads a sampler. Symptom: "my harp is playing dulcimer
// samples". Fix: make the event type name unique per env. m.ptn is
// a fresh 16-char random string per interpretPersonality call, so
// this is unique per device AND per reload. Remove on ~deinit so
// reloads don't leak entries.
var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

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

	loading = true;

	samplesLib = folder.entries.collect({ |path|
		var note = path.fileNameWithoutExtension.split($_).last;
		var buffer = Buffer.read(s, path.fullPath, action:{ |buf|
			postf("buffer alloc [%] \n", buf);
		});
		postf("loading sample : % \n", path.fileNameWithoutExtension);
		(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: noteToMidi.(note))
	});

	// ---------------------------------------------------------------
	// LOAD BARRIER (§27). Buffer.read is ASYNCHRONOUS: it returns a
	// Buffer with its bufnum already allocated, but the server hasn't
	// filled it yet. A note that fires against an unfilled buffer is not
	// an error — it is silence — so a pattern built and played before
	// the reads land can cost you the opening events with NOTHING in the
	// post window to say why.
	//
	// One s.sync is the whole barrier. ~init is called from
	// personalityController's load Routine, so s.sync yields that thread
	// and resumes when every outstanding /b_allocRead has completed.
	//
	// TWO RULES, both learned the hard way:
	//
	//  1. Do NOT gate on a per-file completion counter (counting calls to
	//     Buffer.read's `action:`). One unreadable file leaves the count
	//     permanently short and the pattern is never built at all — a
	//     silent seat with no error. Worse, the build then runs inside a
	//     /done responder, i.e. OFF the load Routine and outside d.env,
	//     so the ~onResync install below lands in the wrong environment
	//     and arrives after the controller has moved on.
	//
	//  2. Keep s.sync OUTSIDE topEnvironment.use. s.sync yields, and a
	//     Routine restores its OWN environment (d.env) when resumed, so
	//     anything after a yield inside a use block runs outside that
	//     block — ~beatClock / ~score* would read nil. If the reads must
	//     happen inside a use block (because they assign an env var, as
	//     cotf_drums* do with ~buffers), close that block first and open
	//     a second one for the build.
	// ---------------------------------------------------------------
	s.sync;
	postf("[%] all buffers loaded (%) \n", m.name, samplesLib.size);

	// The load can be CANCELLED under us: ~deinit may have run while we
	// were parked in s.sync (fast reload, or the COTF server reconciling
	// a hand-loaded personality back within ~10 s). The next load gets a
	// fresh env with a fresh m.ptn, so anything built now would be an
	// orphan Group + Pdef that no later ~deinit can reach — it keeps
	// firing synths at freed buffers until Server.killAll. Bail instead.
	if(loading.not or: { samplesLib.isNil },{
		postf("[%] load cancelled — unloaded while samples were loading \n", m.name);
	},{
		// Name any file that didn't come back rather than failing
		// silently. Still build: one bad sample costs its own notes,
		// not the whole seat.
		samplesLib.do({ |sample|
			if(sample.buffer.numFrames.isNil or: { sample.buffer.numFrames == 0 },{
				postf("[%] sample failed to load : % \n", m.name, sample.name);
			});
		});

		// Custom event handler — resolves note → bufnum + rate. Standard
		// even/odd fallback. Swap for findClosestSample variant on sparse
		// libraries, or the array-aware form for chord voicings (§16).
		// Registered under eventTypeName (per-env unique) — see comment
		// on the var declaration above for why the name is not just
		// \customEvent.
		Event.addEventType(eventTypeName, {|e|
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
					\type, eventTypeName,
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

		};

		// ~onResync MUST live in d.env (this personality env), NOT
		// topEnvironment. Conductor's beatSync OSCdef iterates ~devices and
		// dispatches per-env, so a global slot would let the last-loaded
		// personality clobber every other device's handler → their Pdefs
		// stay stranded on re-anchor (silent instrument after play/seek).
		// Body wraps in topEnvironment.use so ~beatClock / ~roomState /
		// ~scoreBeatsPerBar resolve.
		// TODO(onResync): state-aware if any state pauses the Pdef — only
		// restart if we're NOT in a paused-Pdef state (§6).
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
// Idempotent cleanup. notNil guards handle titleView off+on reload
// firing ~deinit twice on the same env (§5).
~deinit = ~deinit <> {
	// Cancel a load still parked in ~init's s.sync barrier — without this,
	// an unload landing mid-load is followed by ~init building a Pdef and
	// Group belonging to an env nothing will ever tear down again.
	loading = false;

	Pdef(m.ptn).remove;
	// Remove our per-env event type so reloads don't accumulate stale
	// entries in Event.eventTypes. See eventTypeName declaration.
	Event.eventTypes.removeAt(eventTypeName);

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
