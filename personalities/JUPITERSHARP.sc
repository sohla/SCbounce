/*
gestures:    [beat, shake, tilt]
description: Pdef pattern firing per-note harp sample synths on ~beatClock. \root comes from the score, everything else is state-driven: octave and dur per state, amp from gesture, and in idle a \ptch transposition picked by y tilt. Sparse sample set resampled via \ptch.
sound:       harp sample library from ~/Music/cotf_samples/harp; soft rolling idle figure, sparse tuning strokes, active arpeggios during piece
pitch:       \root from ~scoreVoicePool.choose wrapped to pitch class (0-11). \octave from y tilt — idle 5-7, piece 5-8; tuning pins octave 5 and root 0. Idle adds a \ptch transposition from the notes table [0,2,5,7,10,12,14,16] selected by m.gyroYFiltered. Tuning rides the §16 ramp on \ptch, 1.08 → 1.0 over 15 s.
rhythm:      one note per \dur ~beatClock tick. Idle and piece pick dur 0.5 / 1.0 / 2 by amp threshold; tuning is sparse and random (3-5); curtain holds its last value.
instruments: [greenHolder]
*/

/*

- compressor

*/

var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~outBus lives in d.env, and the Pbind
                      // is built inside topEnvironment.use{} where it's nil
var lastTime = 0;
var idleNotes = [0,2,4,5,8,5,4,2];
var tuneTime = 0;
var group;   // dedicated Group for this personality's synths — see
             // concert_p_files.md §5 (Pdef personalities need this)
var loading = false;   // true while ~init is waiting on the sample reads;
                       // ~deinit clears it so a load in flight bails out
                       // instead of building an unreachable Pdef. See ~init.
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

var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

//------------------------------------------------------------

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.1;
m.rrateMassFilteredAttack = 0.999;
m.rrateMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\stereoSampler, {|bufnum=0, out=0, amp=1, rate=1, start=0, pan=0, freq=440, ptch=1,
    attack=0.01, decay=0.1, sustain=0.3, release= 1.7, gate=1,cutoff=20000, rq=1|
	
	var lr = rate * BufRateScale.kr(bufnum) * ptch * 0.5;
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
	var sparkle = FreqShift.ar(sig, freq * 0.51 * ptch, 0,0.3);
    Out.ar(out, (sig + sparkle) * amp * env);
}).add;



//------------------------------------------------------------
~init = ~init <> {

	var findSampleBuffer = {|note|
		var bufnum;
		samplesLib.do({|sample|
			if(sample.midiNote == note,{
				bufnum = sample.buffer;
			});
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

	s.sync;
	postf("all buffers loaded \n");

	if(loading.not or: { samplesLib.isNil },{
		postf("JUPITERSHARP: load cancelled — unloaded while samples were loading \n");
	},{
		// Name any file that didn't come back, rather than failing silently.
		// We still build: one bad sample costs its own notes, not the seat.
		samplesLib.do({ |sample|
			if(sample.buffer.numFrames.isNil or: { sample.buffer.numFrames == 0 },{
				postf("JUPITERSHARP: sample failed to load : % \n", sample.name);
			});
		});

		Event.addEventType(eventTypeName, {|e|
			~note = (~note + ~root + (12 * ~octave)).asInteger;
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
~deinit = ~deinit <> {
	// Cancel any load still parked in ~init's s.sync — it checks this flag
	// before building the event type / group / Pdef, so an unload that lands
	// mid-load can't be followed by an orphan pattern from the old env.
	loading = false;

	Pdef(m.ptn).remove;
	Event.eventTypes.removeAt(eventTypeName);

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
	var amp = (m.accelMassFiltered).lincurve(0, 1.5, -90, -24, -1);
	var notes = [0,2,5,7,11,12,14,16];
	var n = m.gyroYFiltered.lincurve(-1.0,1.0,0,notes.size,-1).asInteger;
	var oct = (d.sensors.gyroEvent.y / pi.half).linlin(-1, 1, 5, 7).asInteger;

	Pdef(m.ptn).set(\octave, oct );

	Pdef(m.ptn).set(\ptch, notes[n].midiratio);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	
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
		{Pdef(m.ptn).set(\dur, 2) });

};

~tuningNext  = {|d, ctx|
	var amp = (m.accelMassFiltered).lincurve(0, 1.0, -80, -24, -4);
	var dur = (m.accelMassFiltered).lincurve(0, 3.0, 4.0, 1.0, -4);
	var tt = 15.0;
	var elapsed = TempoClock.beats - tuneTime;
	var ptch    = if (elapsed < tt) {
		(elapsed / tt).linlin(0, 1, 0.7, 1.0)   // flat → true
	} { 1.0 };	
	
	Pdef(m.ptn).set(\octave, 5);
	Pdef(m.ptn).set(\root,0);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\ptch, ptch);
	Pdef(m.ptn).set(\dur, dur);



};

~pieceNext   = {|d, ctx|

	var amp = (m.accelMassFiltered).lincurve(0,1.5,-80,-14,-2);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1,1,5,8,1).asInteger;

	Pdef(m.ptn).set(\amp, amp.dbamp);
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




