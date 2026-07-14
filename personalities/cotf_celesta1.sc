
var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init bodies run under topEnvironment.use

//------------------------------------------------------------
// note-name → MIDI parser. handles the "INSTR_ES_mf_<NOTE>.wav" naming
// convention used by both harp and celesta sample libraries. supports
// any file whose stem's last underscore-separated token matches
// [A-G](#|b)?[0-9] — e.g. C4, A#3, Bb5.
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

// sample folder. same naming convention as cotf_harp1 (INSTR_ES_mf_NOTE.wav):
//   harp   → ~/Music/cotf_samples/harp           (HA_ES_mf_*)
//   celesta → ~/Music/cotf_samples/Celesta_ES_mf (CE_ES_mf_*)
// only difference is the prefix and the pitch range (celesta is C3–C8 vs.
// harp's B0–C7). the .split($_).last extraction works unchanged because
// both libraries put the note token at the end of the underscore chain.
var folder = PathName("~/Music/cotf_samples/Celesta_ES_mf");
var samplesLib;

//------------------------------------------------------------

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\stereoSampler, {|bufnum=0, out=0, amp=1, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=1.2, gate=1,cutoff=20000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum);
    var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
    Out.ar(out, sig);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	var result;

	// linear scan through samplesLib for the buffer whose midiNote matches
	// the request. returns nil if not found (which the caller must handle —
	// see the celesta pitch-range note above).
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
	// the target MIDI note. same shape as cotf_harp1 — the naming convention
	// is identical between the two libraries, so no per-library special-casing.
	samplesLib = folder.entries.collect({ |path|
		var note = path.fileNameWithoutExtension.split($_).last;
		var buffer = Buffer.read(s, path.fullPath, action:{ |buf|
			postf("buffer alloc [%] \n", buf);
		});
		postf("loading sample : % \n", path.fileNameWithoutExtension);
		(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: noteToMidi.(note))
	});

	// custom event handler — inherited from cotf_harp1 with no changes.
	// odd-MIDI requests get resampled up a semitone from the nearest lower
	// even-MIDI sample (rate = 1.midiratio). works here because celesta,
	// like harp, is provided as a mostly-even-MIDI sample set.
	Event.addEventType(\customEvent, {|e|
		~note = ~note + ~root + (12 * ~octave);
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
		Pdef(m.ptn,
			Pbind(
				\instrument, \stereoSampler,
				\out, ob,
				\type, \customEvent,
				// half time vs. cotf_harp1 (which uses \dur, 1). one note per
				// 8th note instead of per 16th — feels more spacious, gives the
				// celesta's bell tail room to bloom before the next strike.
				\dur, 2,
				\note, 0,
				\root, Pfunc { ~scoreVoicePool.choose.wrap(0,11).asInteger},
			);
		);
		~scoreAnchorBeat = 3;

		Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);

	};
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	// hack a delay to ensure the Pdef is removed before the samples are freed
	fork{
		1.0.yield;
		samplesLib.do({|sample|
			postf("buffer dealloc [%] \n", sample.buffer);
			sample.buffer.free;
			s.sync;
		});
	};


};


//------------------------------------------------------------
~next = {|d|

	// octave range 5–7 — celesta is characteristically high-register, and
	// its sample set fully covers octaves 3–7 (octave 8 is missing sharps
	// so avoid the top). cap at 7 so findSampleBuffer never returns nil.
	var amp = m.accelMassFiltered.lincurve(0,1.4,-50,-12,-1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1,1,5,7,1).asInteger;
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, oct);

};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[(d.sensors.gyroEvent.y / pi.half)];//up down
};
