/*
gestures:    [beat, shake, tilt]
description: Pdef pattern firing per-note sample-playback synths on ~beatClock; state sets pitch/octave/dur; gesture drives amp and (in piece) octave via gyro tilt
sound:       harp sample library; soft rolling idle drone; tuning bends up over ~20 s; active arpeggios during piece
pitch:       \root from score voice pool wrapped to pitch class; \octave state-driven (idle 3–6, tuning 5, piece 4–9 from tilt, curtain last-value)
rhythm:      note per \dur ~beatClock tick; state sets dur (idle 0.5–1 by amp threshold, tuning 0.75, piece 0.5–1 by amp threshold, curtain 0.5)
instruments: [greenHolder]
*/

var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init bodies run under topEnvironment.use
var lastTime = 0;
var idleNotes = [0,2,4,5,7,5,4,2];
var tuneTime = 0;
var group;   // dedicated Group for this personality's synths — see
             // concert_p_files.md §5 (Pdef personalities need this)
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

// Unique per-env event type. Event.addEventType registers on a class-level
// dict, so multiple personalities sharing \customEvent clobber each other's
// handlers (last loader wins → cross-device sample bleed). m.ptn is a fresh
// 16-char random string per env, so this stays unique per device AND reload.
var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

// scope issue!?!
// var samplesLib = folder.entries.collect({ |path|
// 	var note = path.fileNameWithoutExtension.split($_).last;
// 	var buffer = Buffer.read(s, path.fullPath, action:{ |buf|
// 		postf("buffer alloc [%] \n", buf);
// 	});
// 	postf("loading sample : % \n", path.fileNameWithoutExtension);
// 	(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: noteToMidi.(note))
// });

//------------------------------------------------------------

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.1;
m.rrateMassFilteredAttack = 0.999;
m.rrateMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\stereoSampler, {|bufnum=0, out=0, amp=1, rate=1, start=0, pan=0, freq=440, ptch=1,
    attack=0.01, decay=0.1, sustain=0.3, release=1.2, gate=1,cutoff=20000, rq=1|
	
	var lr = rate * BufRateScale.kr(bufnum) * ptch;
    var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
	var sparkle = FreqShift.ar(sig, freq * 0.52 * ptch, 0,0.4);
    Out.ar(out, (sig + sparkle) * amp * env);
}).add;



//------------------------------------------------------------
~init = ~init <> {

	var result;

	var findSampleBuffer = {|note|
		var bufnum;
		samplesLib.do({|sample|
			if(sample.midiNote == note,{
				bufnum = sample.buffer;
			});
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

	Event.addEventType(eventTypeName, {|e|
		// asInteger because SC's default ~octave is 5.0 (Float); the
		// addition promotes ~note to Float and .odd is not defined on Float.
		~note = (~note + ~root + (12 * ~octave)).asInteger;
		// Explicitly hand the actual played frequency to the SynthDef —
		// otherwise the default \note event recomputes ~freq from ~note +
		// ~root + 12*~octave, which double-counts because we've already
		// folded those in above. Now the SynthDef's \freq arg is the true
		// playing pitch (odd/even resample still lands on the intended
		// pitch: even → rate 1 at correct sample; odd → rate 1.midiratio
		// off the note-1 sample, which raises it back to the intended freq).
		~freq = ~note.midicps;
		if(~note.odd,{
			~bufnum = findSampleBuffer.(~note-1);
				~rate = 1.midiratio;
		},{
			~bufnum = findSampleBuffer.(~note);
				~rate = 1;
		});
			~type = \note;
			currentEnvironment.play;
	});

	topEnvironment.use{
		group = Group.new;

		Pdef(m.ptn,
			Pbind(
				\instrument, \stereoSampler,
				\out, ob,
				\group, group,    // route every event's synth into our group
				\type, eventTypeName,
				\note, 0,
				\root, Pfunc { ~scoreVoicePool.choose.wrap(0,11).asInteger},
				// \octave, 6,
				\args, #[]
			);
		);

		Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		// cotf: seed envir so a stickless seat is silent (SC Event default
		// amp=0.1 otherwise); first enabled tick overrides. The tick hooks
		// (~idleNext etc.) only run while the seat's device is enabled, so a
		// seat with this personality loaded and no stick connected would
		// otherwise run on Event defaults — louder (amp 0.1, one note per
		// beat) than a motionless performer, whose ~idleNext floors at -60 dB.
		// Must be a Pdef envir .set, NOT a Pbind key: Pbind keys override the
		// envir and would permanently defeat the hooks' .set.
		Pdef(m.ptn).set(\amp, 0, \dur, 2);
	};

	// ~onResync lives in this personality env (d.env) — dispatched per-device
	// from conductorController's beatSync OSCdef, so no cross-device clobber.
	// Body wraps in topEnvironment.use so ~beatClock etc. resolve.
	// On beat-clock re-anchor (seek): stop the Pdef so TempoClock doesn't
	// dump backlog, kill in-flight synths in the group so nothing is stuck
	// at sustain, then restart. freeAll wrapped in s.bind so it lands after
	// any /s_new bundle still in flight — see concert_p_files.md §6.
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
	Pdef(m.ptn).remove;
	Event.eventTypes.removeAt(eventTypeName);

	// Kill synths first (with latency-safe /g_freeAll), then free
	// sample buffers — order matters so no PlayBuf is still reading
	// from a buffer we're about to /b_free. fork so s.sync actually
	// waits for the server (s.sync is only meaningful inside a Routine).
	// Idempotent: notNil guards let ~deinit fire twice safely (unload+load).
	fork {
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;                    // wait for /g_freeAll to complete
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
// ~next = {|d|

// 	// var amp = m.accelMassFiltered.lincurve(0,2.4,-50,-10,-1);
// 	var amp = (m.accelMassFiltered + m.rrateMassFiltered).half.lincurve(0,1.5,-70,-18,-1);
// 	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1,1,4,9,1).asInteger;
// 	Pdef(m.ptn).set(\amp, amp.dbamp);
// 	Pdef(m.ptn).set(\octave, oct);

// 	// if(amp > -20, {
// 		// if(TempoClock.beats > (lastTime + 1),{
// 			Pdef(m.ptn).set(\dur, 0.25);
// 			// lastTime = TempoClock.beats;
// 		// },{
// 		// });
// 	// },{
// 		// Pdef(m.ptn).set(\dur, 1.0);

// 	// });


// };

//------------------------------------------------------------
// Room-state routing — Pdef stays playing across all states; per-state
// amp is set by the state-gated ticks (~idleNext etc.) so we only hear
// the arpeggio during \piece. This switch is a hook site for any
// one-shot state-entry logic (e.g. reset counters, re-seed patterns).
~onRoomState = {|ctx|
	switch(ctx.state,
		\idle,    { Pdef(m.ptn).set(\ptch, 1.0) },
		\tuning,  { tuneTime = TempoClock.beats },
		\piece,   { Pdef(m.ptn).set(\ptch, 1.0) },
		\curtain, { },
		\silent,  { Pdef(m.ptn).set(\amp, 0); }
	);
};

//------------------------------------------------------------
// State-gated ticks — ~next always runs (gesture → amp/octave), these
// override amp per state so silence is enforced regardless of gesture.
~idleNext    = {|d, ctx|
	var amp = (m.accelMassFiltered).lincurve(0, 3.0, -80, -18, -1);
	var notes = [0,7,12,16];
	var n = m.gyroYFiltered.lincurve(-1.0,1.0,0,notes.size,-1).asInteger;
	// Pdef(m.ptn).set(\octave, [4,5].choose );
	Pdef(m.ptn).set(\ptch, notes[n].midiratio);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	

	case(
		{ amp > -18.1 }, {
			if(TempoClock.beats > (lastTime + 0.25),{
				Pdef(m.ptn).set(\dur, 0.5);
				lastTime = TempoClock.beats;
			})},
		{ amp > -25 }, {
			if(TempoClock.beats > (lastTime + 0.5),{
				Pdef(m.ptn).set(\dur, 1.0);
				lastTime = TempoClock.beats;
			})},
		{Pdef(m.ptn).set(\dur, 2) });

};

~tuningNext  = {|d, ctx|
	var amp = ((m.rrateMassFiltered) * 2.0).lincurve(0, 1.0, -80, -24, -4);
	var tt = 15.0;

	Pdef(m.ptn).set(\octave, 5);
	Pdef(m.ptn).set(\root,0);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\ptch, 1);
	Pdef(m.ptn).set(\dur, 3.0.rrand(5.0));

	if( (TempoClock.beats-tuneTime) < tt, {
		var val = (TempoClock.beats-tuneTime) / tt;

		Pdef(m.ptn).set(\ptch, (val.linexp(0, 1, 1.08, 1.0)));
	},{
		Pdef(m.ptn).set(\ptch, 1.0);
	});


};

~pieceNext   = {|d, ctx|

	var amp = (m.accelMassFiltered).lincurve(0,3.0,-70,-9,-2);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1,1,5,9,1).asInteger;

	Pdef(m.ptn).set(\amp, amp.dbamp * ctx.loudness.linlin(0, 1, 0.1, 1.0));
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\ptch, 1);

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
		{Pdef(m.ptn).set(\dur, 2) });

};

~curtainNext = {|d, ctx|
	var amp = ((m.rrateMassFiltered) * 2.0).lincurve(0, 1.0, -80, -35, -4);
	Pdef(m.ptn).set(\amp, amp.dbamp); 
	Pdef(m.ptn).set(\dur, 3);
};


//------------------------------------------------------------
// Beat-aligned hooks. Empty stubs are placeholders — fill in as ideas
// arise. Basic ideas populated in ~onSection and ~onBar for testing.
~onTick    = {|ctx| /* every subdivision */ };
~onHalf    = {|ctx| /* on half-bar change */ };
~onBeat    = {|ctx| /* every true beat */ };
~onBar     = {|ctx|
	// "harp onBar %  (sec % / phr %)".format(ctx.barIdx, ctx.sectionId, ctx.phraseId).postln;
};
~onPhrase  = {|ctx| /* on phrase change */ };
~onSection = {|ctx|
	// Character shift per section: shorten dur in dev sections, hold
	// long in intro/coda. Only kicks in when Pdef is playing (\piece).
	// switch(ctx.sectionId,
	// 	"intro",  { Pdef(m.ptn).set(\dur, 1.0); },
	// 	"A",      { Pdef(m.ptn).set(\dur, 1.0); },
	// 	"dev",    { Pdef(m.ptn).set(\dur, 0.5); },
	// 	"B",      { Pdef(m.ptn).set(\dur, 0.5); },
	// 	"recap",  { Pdef(m.ptn).set(\dur, 1.0); },
	// 	"coda",   { Pdef(m.ptn).set(\dur, 2.0); }
	// );
};
~onChord   = {|ctx| /* on chord change: could tilt attack/decay */ };
~onKey     = {|ctx| /* on key change */ };
~onScale   = {|ctx| /* on active_scale change */ };

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;
	
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	// [m.accelMass, m.accelMassFiltered];

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].abs;
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];
	// [(1+m.accelMassFiltered) * (1+m.rrateMassFiltered).half.half,m.accelMassFiltered];
	// [(m.accelMassFiltered + m.rrateMassFiltered).half,m.accelMassFiltered];

	[(m.rrateMassFiltered * 2.0)];
	// [m.gyroXFiltered.fold(-0.5,0.5)];
	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [m.gyroYFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0,1,-1)];
	// [m.gyroXFiltered.fold(-0.5,0.5).linlin(-0.5,0.5,-10,10).lcurve];
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];



};




