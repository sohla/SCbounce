
var m = ~model;

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
m.accelMassFilteredDecay = 0.5;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

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


SynthDef(\funBass, {
    |out=0, freq = 440, gate = 1, amp = 0.8, filtFreq = 200, filtRes = 0.2, envAtk = 0.31, envDec = 0.1, envSus = 0.7, envRel = 3.2, rm = 0.5|
    var osc1, osc2, osc3, env, filter, output;
		var osc4, osc5, osc6;
    // env = EnvGen.ar(Env.adsr(envAtk, envDec, envSus, envRel), gate, doneAction: Done.freeSelf);
    env = EnvGen.ar(Env.perc(envAtk,envRel), gate, doneAction: Done.freeSelf);
    osc1 = Saw.ar(freq, 1);
    osc2 = Pulse.ar(freq * 0.99, 0.3, 1);
    osc3 = SinOsc.ar(freq * 1.01, 0,1);
    osc4 = Saw.ar(freq, 2.002);
    osc5 = Pulse.ar(freq * 2.004, 0.3, 1);
    osc6 = SinOsc.ar(freq * 2.97, 0, 1);
    output = [Mix([osc1, osc2, osc3]), Mix([osc4, osc5, osc6])] * env * amp;
    filter = RLPF.ar(output, filtFreq, filtRes);
		// filter = [filter.distort, filter.tanh];
    Out.ar(out, LeakDC.ar(filter.softclip));
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
				\type, \customEvent,
				\dur, 0.5,
				\note, 0,
				\root, Pfunc { ~scoreVoicePool.choose.wrap(0,11).asInteger},
				// \octave, 6,
				// \args, #[]
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

	var amp = m.accelMassFiltered.lincurve(0,2.4,-50,-10,-1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1,1,4,7,1).asInteger;
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, oct);

};

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


	// [m.gyroXFiltered.fold(-0.5,0.5)];
	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	[(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [m.gyroYFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0,1,-1)];
	// [m.gyroXFiltered.fold(-0.5,0.5).linlin(-0.5,0.5,-10,10).lcurve];
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];



};




