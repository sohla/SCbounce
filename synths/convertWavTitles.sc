(
SynthDef(\stereoSampler, {|bufnum=0, out=0, amp=1, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=20000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum);// * (freq/440.0);
    var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.003], startPos: start * BufFrames.kr(bufnum), loop: 0);
	// sig = RLPF.ar(sig, cutoff, rq);
    sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
    Out.ar(out, sig);
}).add;

)

(
	var result;
	var folder = PathName("/Library/Application\ Support/GarageBand/Instrument\ Library/Sampler/Sampler\ Files/Harp/Harp_ES_mf");
// var folder = PathName("/Library/Application\ Support/GarageBand/Instrument\ Library/Sampler/Sampler\ Files/Violas/Violas_pizz_f");

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

	var samples = folder.entries.collect({ |path|

		var note = path.fileNameWithoutExtension.split($_).last;
		var buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		});
		(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: noteToMidi.(note))
	});

	var findSampleBuffer = {|note|
		var bufnum;
		samples.do({|sample|
			if(sample.midiNote == note,{
				bufnum = sample.buffer;
			});
		});
		bufnum
	};

Event.addEventType(\customEvent, {|e|
	~note = ~note + ~root;
	if(~note.odd,{
		~bufnum = findSampleBuffer.(~note-1);
	    ~rate = 1.midiratio;
	},{
		~bufnum = findSampleBuffer.(~note);
	    ~rate = 1;
	});
    ~instrument = \stereoSampler;
    ~type = \note;
    currentEnvironment.play;
});

Pdef(\tester,
	Pbind(
		\type, \customEvent,
		\instrument, \stereoSampler,
		\dur, 0.5/3,
		\legato, 3,
		// \note, Pn(Pseries(60,2, 20), inf),
		// \root, Pseq([0,-1].stutter(20), inf),
		\octave, 5,
		\root, Pseq([0,3,-6,-3].stutter(60)-2, inf),
	    \note, Pseq([-5,0,4,-5,0,4,-5,0,4,-5,0,4,-3,2,6,-3,2,6,-3,2,6,-3,2,6,-3,2,6,-3,2,6]+72, inf),
		\release,1,
		)
	).play;
)
(
s.freeAllBuffers;
Pdef(\tester).remove;
)

