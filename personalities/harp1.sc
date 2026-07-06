
var m = ~model;
var lastTime = 0;
var frame = 0;

var synth, bassSynth;
var dur = 0.11;
var notes = [0,2,5,7,9,11,12,14,12,11] + 1;
var bass = [2,9,5,12,5,9,2].stutter(2) + 1;
var root = [0];
var offset = 0;
var bassCount = 0;
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

	~playNote = {|note,root,octave, amp=0.1|
			var n = note + root + (12 * octave);
			var bufnum,rate;

			if(n.odd,{
				bufnum = findSampleBuffer.(n-1);
				rate = 1.midiratio;
			},{
				bufnum = findSampleBuffer.(n);
				rate = 1;
			});
			synth = Synth(\stereoSampler, [
				\bufnum, bufnum,
				\rate,rate,
				\freq, n.midicps,
				\amp,amp*1
			]);
			synth.server.sendBundle(0.3,[\n_set, synth.nodeID, \gate, 0]);

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
			// ~instrument = \stereoSampler;
			~type = \customVisualEvent;
			// ~type = \note;
			currentEnvironment.play;
		 	// ~bufnum.postln;
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \stereoSampler,
			\dur, Pslide([dur,dur,dur,dur,dur,dur,dur,dur,dur,dur], inf, Pkey(\range), 0, 0),
			\note, Pslide(notes, inf, Pkey(\range), 0, offset),
			\octave, 5,//Pwhite(5,7),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[]

		);
	);
	Pdef(m.ptn).play(quant:0.1);

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
~onEvent = {|e|
	// m.com.root = bass[0];
	frame = frame + 1;
};


//------------------------------------------------------------
~next = {|d|

	var move = m.accelMassFiltered.lincurve(0,1.5,1,notes.size,1);
	var amp = m.accelMassFiltered.lincurve(0,1.4,-50,-6,-1);

	Pdef(m.ptn).set(\range, move.floor);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\root, root[0]);


	// if(m.accelMassFiltered > 0.01,{
	// 	if( Pdef(m.ptn).isPlaying.not,{
	// 		Pdef(m.ptn).resume(quant:dur);
	// 	});
	// },{
	// 	if( Pdef(m.ptn).isPlaying,{
	// 		Pdef(m.ptn).pause();
	// 	});
	// });

	
	// topEnvironment.use{
	// 	~beatClock.beats.postln;
	// };


};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// ACCEL
	// [m.accelMass * 0.1, m.accelMassFiltered.linlin(0,3,0,1)];

	// ROTATE
	[m.rrateMass/2, m.rrateMassFiltered.linlin(0,2,0,1)];

	// X axis
	// [d.sensors.gyroEvent.x/pi]; // norm

	// Y axis
	// [d.sensors.gyroEvent.y/pi]; // norm

	// Z axis
	// [d.sensors.gyroEvent.z/(pi/2)]; // norm

	// device [I• ]
	// [(d.sensors.gyroEvent.x/pi).linlin(-0.8,0.8,0.9,-0.9)]  //up down
	// [(d.sensors.gyroEvent.y/pi).linlin(-0.4,0.4,0.9,-0.9)]  //left right
	// [(d.sensors.gyroEvent.z/(pi/2)).linlin(-0.3,1.0,-0.9,0.9)]  //wrist rotate


};




