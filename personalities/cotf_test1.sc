/*
gestures:    [beat, shake, tilt]
description: Worked example from prose spec — engine B sampler with per-state trigger switching (accel one-shots in idle/tuning, layered Pdef + one-shots in piece with rotation-density subdivision)
sound:       harp samples; low-vol accel-triggered hits in idle; A5-centred hits with ±2st pitch wander that settles in tuning; running pattern + gestural accents in piece with rotation-driven subdivision; faded low-register pattern in curtain
pitch:       idle/piece = voice-pool wrap-to-pc; tuning = fixed A5 (root 9, octave 6) with wander envelope on \ptch; curtain = voice-pool with low-octave choose
rhythm:      idle/tuning = accel-triggered one-shots, Pdef paused; piece = running Pdef with rotation-density \dur + accel-threshold one-shots layered on top (§20); curtain = quiet running Pdef, wider dur
instruments: [Aetherharp]
*/

// Composed from personality_authoring.md workflow. Recipes cited:
//   §15 accel one-shots (idle, tuning, piece one-shot layer)
//   §16 sampler + odd-note detune (Event.addEventType handler)
//   §17 amp palette — low (idle), low (tuning), expressive (piece), faded (curtain)
//   §18 pitch wander around target — tuning
//   §19 rotation-density subdivision — piece \dur
//   §20 layered running-Pdef + one-shots — piece
//   §5/§6/§15 group + state-aware ~onResync + reload guard

var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init bodies run under topEnvironment.use
var lastTime = 0;     // throttle anchor for one-shots (per state)
var tuneTime = 0;     // captured on \tuning entry for wander envelope
var group;            // dedicated Group — §5

//------------------------------------------------------------
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

var folder = PathName("~/Music/cotf_samples/harp");
var samplesLib;

// Unique per-env event type — see cotf_harp1.sc for rationale.
var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

//------------------------------------------------------------
// Filter tuning — copied from harp1 (same sensor use-shape).
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.3;
m.rrateMassFilteredAttack = 0.999;
m.rrateMassFilteredDecay  = 0.6;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
// Sampler SynthDef — shared shape across harp1/celesta1/dulcimer1.
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

	// Standard even-MIDI library lookup with odd-note detune (§16).
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

	// Standard customEvent — odd/even fallback resample. Used both by
	// the running Pdef (piece/curtain) and by the inline one-shot
	// events in ~idleNext / ~tuningNext / ~pieceNext (§15 idiom).
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

		// Running-Pdef backbone for \piece and \curtain. \root is
		// voice-pool wrap-to-pc; \dur / \amp / \octave / \ptch are set
		// by the state ticks.
		Pdef(m.ptn,
			Pbind(
				\instrument, \stereoSampler,
				\out, ob,
				\group, group,   // route into personality group — §5
				\type, eventTypeName,
				\note, 0,
				\root, Pfunc { ~scoreVoicePool.choose.wrap(0, 11).asInteger },
			);
		);

		Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		// cotf: seed envir so a stickless seat is silent — SC's Event default
		// amp is 0.1, and the ~*Next tick hooks (the only writers of \amp) run
		// only while the seat's device is enabled. Envir .set, not a Pbind key:
		// Pbind keys override the envir and would defeat the hooks' .set.
		Pdef(m.ptn).set(\amp, 0);
		Pdef(m.ptn).set(\dur, 1);

		// Reload guard (§15) — Pdef must be paused in \idle and \tuning
		// because both use accel one-shots only. ~onRoomState fires only
		// on state *change*, so a reload while already in one of those
		// states would leave the Pdef running.
		if ((~roomState == \idle) or: { ~roomState == \tuning }, {
			Pdef(m.ptn).pause;
			if (~roomState == \tuning, { tuneTime = TempoClock.beats });
		});

	};

	// ~onResync in d.env (per-device dispatch — no cross-device clobber).
	// Body in topEnvironment.use so ~beatClock / ~roomState resolve.
	// State-aware: after freeAll, only restart the Pdef when we're in a
	// state that uses the running pattern (§6).
	~onResync = { |idx|
		topEnvironment.use {
			Pdef(m.ptn).stop;
			s.bind { group.freeAll };
			if ((~roomState != \idle) and: { ~roomState != \tuning }, {
				Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
			});
		};
	};
};

//------------------------------------------------------------
// Idempotent cleanup — notNil guards for double-~deinit safety (§5).
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
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
// Room-state routing. Wrapped in topEnvironment.use because the
// resume branches use ~beatClock / ~scoreBeatsPerBar (§7).
~onRoomState = {|ctx|
	topEnvironment.use {
		switch(ctx.state,
			\idle,    {
				// Pause Pdef + kill any in-flight tails — accel one-shots
				// only from here (§15).
				Pdef(m.ptn).pause;
				s.bind { group.freeAll };
			},
			\tuning,  {
				tuneTime = TempoClock.beats;
				Pdef(m.ptn).pause;
				s.bind { group.freeAll };
			},
			\piece,   {
				// Un-pause the backbone; ~pieceNext modulates it via §19
				// and fires §20 one-shots on top.
				Pdef(m.ptn).resume(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
			},
			\curtain, {
				Pdef(m.ptn).resume(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
			},
			\silent,  {
				Pdef(m.ptn).set(\amp, 0);
			}
		);
	};
};

//------------------------------------------------------------
// Idle: accel-threshold one-shots, low volume, voice-pool pitch.
// Pdef is paused; only inline events fire here. Throttle at 0.4 s.
~idleNext = {|d, ctx|
	var amp = m.accelMassFiltered.lincurve(0, 2.0, -70, -25, -4);   // §17 low
	if (m.accelMassFiltered > 0.5, {
		if (TempoClock.beats > (lastTime + 0.4), {
			(
				instrument: \stereoSampler,
				type:       eventTypeName,
				out:        ob,
				group:      group,
				note:       0,
				root:       (~scoreVoicePool ? [69]).choose.wrap(0, 11).asInteger,
				octave:     5,
				amp:        amp.dbamp,
				ptch:       1.0,
			).play;
			lastTime = TempoClock.beats;
		});
	});
};

//------------------------------------------------------------
// Tuning: accel-threshold one-shots at A5, pitch wanders ±2 semitones
// around A5 for 20 s then settles at true. §15 one-shot + §18 wander.
~tuningNext = {|d, ctx|
	var amp = m.accelMassFiltered.lincurve(0, 2.0, -70, -25, -4);   // §17 low
	var wanderDur    = 20.0;
	var wanderRangeSt = 2.0;
	var wanderRateHz = 0.3;
	var elapsed = TempoClock.beats - tuneTime;
	var envelope = if (elapsed < wanderDur, { 1.0 - (elapsed / wanderDur) }, { 0 });
	var offsetSt = envelope * wanderRangeSt * sin(elapsed * 2 * pi * wanderRateHz);
	var ptch = offsetSt.midiratio;

	if (m.accelMassFiltered > 0.5, {
		if (TempoClock.beats > (lastTime + 0.4), {
			(
				instrument: \stereoSampler,
				type:       eventTypeName,
				out:        ob,
				group:      group,
				note:       0,
				root:       9,       // A
				octave:     6,       // A5
				amp:        amp.dbamp,
				ptch:       ptch,    // wander offset ride
			).play;
			lastTime = TempoClock.beats;
		});
	});
};

//------------------------------------------------------------
// Piece: LAYERED (§20). Running Pdef with rotation-density on \dur
// (§19), tilt-driven octave, expressive amp curve. Accel-threshold
// one-shots layered on top with a slightly higher threshold + louder
// so they punch through the running pattern.
~pieceNext = {|d, ctx|
	// Running-Pdef modulation
	var dur     = m.rrateMassFiltered.linexp(0.01, 1.0, 2.0, 0.25);   // §19
	var pdefAmp = m.accelMassFiltered.lincurve(0, 1.5, -70, -8, -1);   // §17 expressive
	var oct     = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 4, 7, 1).asInteger;
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\amp, pdefAmp.dbamp * ctx.loudness.linlin(0, 1, 0.3, 1.0));
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\ptch, 1);

	// Gestural one-shot on top — higher threshold to reserve for
	// stronger hits, slightly louder to sit above the pattern.
	if (m.accelMassFiltered > 0.7, {
		if (TempoClock.beats > (lastTime + 0.3), {
			(
				instrument: \stereoSampler,
				type:       eventTypeName,
				out:        ob,
				group:      group,
				note:       0,
				root:       (~scoreVoicePool ? [69]).choose.wrap(0, 11).asInteger,
				octave:     oct + 1,
				amp:        (pdefAmp + 3).dbamp,
				ptch:       1.0,
			).play;
			lastTime = TempoClock.beats;
		});
	});
};

//------------------------------------------------------------
// Curtain: quiet running Pdef in low register, faded curve.
~curtainNext = {|d, ctx|
	var amp = m.rrateMassFiltered.lincurve(0, 1.0, -80, -30, -4);   // §17 faded
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\dur, 3);
	Pdef(m.ptn).set(\octave, [3, 4].choose);
	Pdef(m.ptn).set(\ptch, 1);
};

//------------------------------------------------------------
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
~plotMax = 1;
~plot = { |d, p|
	[m.accelMassFiltered, m.rrateMassFiltered];
};
