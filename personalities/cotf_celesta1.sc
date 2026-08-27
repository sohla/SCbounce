/*
gestures:    [beat, shake, tilt]
description: Pdef pattern firing per-note celesta sample synths; single-note melody from a local voice pool; gesture drives amp + octave; odd-MIDI notes resample from nearest even-MIDI sample
sound:       high-register bell timbre; crisp attack, long ringing tail; spacious 8th-note grid so tails bloom
pitch:       local voice pool wrapped to pitch class (root); \octave from tilt (6–8); even-MIDI sample set + midiratio resample for odd notes
rhythm:      per-note; \dur = 2 (8th grid)
instruments: [Lumivox]
*/

var m = ~model;
var lastTime = 0;
var group;   // dedicated Group for this personality's synths

// the conductor used to hand pitch material down as ~scoreVoicePool.
// standalone, the pool is local.
var voicePool = [0, 2, 4, 7, 9, 11];

//------------------------------------------------------------
// note-name → MIDI parser. handles the "INSTR_ES_mf_<NOTE>.wav" naming
// convention used by the celesta sample library. supports any file whose
// stem's last underscore-separated token matches [A-G](#|b)?[0-9] —
// e.g. C4, A#3, Bb5.
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

// sample folder, naming convention INSTR_ES_mf_NOTE.wav:
//   celesta → ~/Music/cotf_samples/Celesta_ES_mf (CE_ES_mf_*)
// the .split($_).last extraction works because the library puts the note
// token at the end of the underscore chain.
var folder = PathName("~/Music/cotf_samples/Celesta_ES_mf");
var samplesLib;

// Unique per-env event type — shared \customEvent registrations clobber
// each other across devices.
var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

//------------------------------------------------------------

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\stereoSampler, {|bufnum=0, out=0, amp=1, rate=1, ptch=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=1.2, gate=1,cutoff=20000, rq=1|
	// ptch multiplies the sample playback rate — continuous pitch bend.
	var lr = rate * BufRateScale.kr(bufnum) * ptch;
    var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
    Out.ar(out, sig);
}).add;

//------------------------------------------------------------
// visual : one mark per struck bell. the celesta's tail is the point of
// the instrument, so the mark is a slow star that opens and fades over
// the ring rather than snapping shut. Atlas grammar G8 (event-triggered).
//
//   pitch class -> horizontal position   (\sx -> \ex)
//   octave      -> vertical position     (\sy -> \ey)
//   amp         -> mark size
~init = ~init <> {

	var result;

	// linear scan through samplesLib for the buffer whose midiNote matches
	// the request. returns nil if not found (which the caller must handle —
	// the celesta range is C3–C8).
	var findSampleBuffer = {|note|
		var bufnum;
		samplesLib.do({|sample|
			if(sample.midiNote == note,{
				bufnum = sample.buffer;
			});
		});
		bufnum
	};

	// load every file in the folder, parsing the note token and computing
	// the target MIDI note.
	samplesLib = folder.entries.collect({ |path|
		var note = path.fileNameWithoutExtension.split($_).last;
		var buffer = Buffer.read(s, path.fullPath, action:{ |buf|
			postf("buffer alloc [%] \n", buf);
		});
		postf("loading sample : % \n", path.fileNameWithoutExtension);
		(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: noteToMidi.(note))
	});

	// custom event handler. odd-MIDI requests get resampled up a semitone
	// from the nearest lower even-MIDI sample (rate = 1.midiratio) — works
	// because celesta is provided as a mostly-even-MIDI sample set.
	// asInteger because SC's default ~octave is 5.0 (Float); the addition
	// promotes ~note to Float and .odd is not defined on Float.
	// Hands on to \customVisualEvent, which draws the mark and re-types
	// to \note itself.
	Event.addEventType(eventTypeName, {|e|
		~note = (~note + ~root + (12 * ~octave)).asInteger;
		if(~note.odd,{
			~bufnum = findSampleBuffer.(~note-1);
				~rate = 1.midiratio;
		},{
			~bufnum = findSampleBuffer.(~note);
				~rate = 1;
		});
			~type = \customVisualEvent;
			currentEnvironment.play;
	});

	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \stereoSampler,
			\group, group,   // route every event's synth into our group
			\type, eventTypeName,
			\note, 0,
			\root, Pfunc { voicePool.choose.wrap(0,11).asInteger },

			\shape, \star,
			\sx, Pfunc{|e| (e[\root] ? 0).wrap(0, 11).linlin(0, 11, -0.8, 0.8) },
			\ex, Pkey(\sx),
			\sy, Pfunc{|e| (e[\octave] ? 6).linlin(5, 8, 0.5, -0.7) },
			\ey, Pkey(\sy),
			\startSize, Pfunc{|e| (e[\amp] ? 0.2).linlin(0, 1, 30, 150) },
			\endSize, 220,
			\startColor, Color.new(0.8, 0.95, 1.0),
			\endColor, Color.new(0.4, 0.6, 1.0).alpha_(0.0),
			\startWidth, 3,
			\endWidth, 0.2,
			\duration, 2.5,
		);
	);

	Pdef(m.ptn).play(quant: 4);
	Pdef(m.ptn).set(\amp, 0);
	// Default dur = 2 (8th-note grid) — gives the celesta's bell tail
	// room to bloom.
	Pdef(m.ptn).set(\dur, 2);
	Pdef(m.ptn).set(\octave, 6);
	Pdef(m.ptn).set(\ptch, 1);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	// Remove our per-env event type from Event.eventTypes so reloads don't
	// accumulate stale entries.
	Event.eventTypes.removeAt(eventTypeName);

	// Kill synths first (latency-safe /g_freeAll), then free sample
	// buffers — order matters so no PlayBuf is still reading from a
	// buffer we're about to /b_free. fork so s.sync actually waits.
	// Both cleanups are notNil-guarded so ~deinit stays idempotent —
	// titleView off+on calls unLoadPersonality then loadPersonality,
	// each of which fires ~deinit on the same env, so without the
	// samplesLib guard we'd .free every buffer twice ("Cannot call
	// free on a Buffer that has been freed"). Match the group guard.
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
~next = {|d|
	var amp = m.accelMassFiltered.lincurve(0, 2.0, -70, -10, -1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 6, 8, 1).asInteger;

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\dur, 2);
	Pdef(m.ptn).set(\ptch, 1);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[(d.sensors.gyroEvent.y / pi.half)];//up down
};
