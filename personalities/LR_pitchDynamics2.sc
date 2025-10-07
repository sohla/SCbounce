
var m = ~model;

m.accelMassFilteredAttack = 0.6;
m.accelMassFilteredDecay = 0.4;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;

//------------------------------------------------------------
SynthDef(\stereoSampler, {|bufnum=0, out=0, amp=1, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=1.2, gate=1,cutoff=20000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum);// * (freq/440.0);
    var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
	// sig = RLPF.ar(sig, cutoff, rq);
    sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
    Out.ar(out, sig);
}).add;


//------------------------------------------------------------
~init = ~init <> {


	var result;
	var folder = PathName("~/Downloads/yourDNASamples/harp");

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
	~note = ~note + ~root + (12 * ~octave);
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
	~bufnum.postln;
});

	Pdef(m.ptn,
		Pbind(
			\type, \customEvent,
			\instrument, \stereoSampler,
			\root, Pseq([0,2,4,6].stutter(40), inf),
			\dur, Pseq([0.2,0.2,0.4,0.2,0.2]*0.5, inf),
			\octave, Pseq([4].stutter(2), inf),
		);
	);
	Pdef(m.ptn).play(quant:0.1);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	s.freeAllBuffers;

};

//------------------------------------------------------------
//------------------------------------------------------------
~onEvent = {|e|
	m.com.root = e.root;
};


//------------------------------------------------------------
~next = {|d|

  var cs = [0,4,8];
  var notes = cs ++ (cs + 12) ++ (cs + 24) ++ (cs + 36);
	var index = (d.sensors.gyroEvent.z / pi).linlin(-1.0,1.0,notes.size,0); //left right
	var amp = m.accelMassFiltered.lincurve(0,2.5,-30,-12,-1);

	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\note, notes[index.floor]);

	if(m.accelMassFiltered > 0.15,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.1);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
		});
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [yellow, cyan , magenta]??

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;
	
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	// [m.accelMass, m.accelMassFiltered] * 0.2;

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].abs;
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	[m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];


};




