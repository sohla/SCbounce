
var m = ~model;
var synth;

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;
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

SynthDef(\warmRichSynth, {
    arg out=0, freq=440, amp=0.5, gate=1,
        attackTime=1.1, decayTime=0.3, sustainLevel=0.5, releaseTime=1.0,
        cutoff=1000, resonance=0.5,
        detune=0.1, stereoWidth=0.5,
        oscMix=0.5, subOscLevel=0.3,
        filterEnvAmount=0.1, filterAttack=0.03, filterDecay=0.1, filterSustain=0.5, filterRelease=0.5;

    var sig, env, filterEnv, subOsc, stereoSig;

    // ADSR envelope
    env = EnvGen.kr(
        Env.adsr(attackTime, decayTime, sustainLevel, releaseTime),
        gate,
        doneAction: 2
    );

    // Main oscillator (slightly detuned saw waves for richness)
    sig = Mix.ar([
        Saw.ar(freq * (1 - detune)),
        Saw.ar(freq),
        Saw.ar(freq * (1 + detune))
    ]) * (1 - oscMix) ;

    // Add a sine wave oscillator for warmth
    sig = sig + (SinOsc.ar(freq) * oscMix);

    // Sub oscillator for extra depth
    subOsc = SinOsc.ar(freq * 0.5) * subOscLevel;
    sig = sig + subOsc;

    // Stereo widening
    stereoSig = [sig, sig];
    stereoSig = stereoSig + LocalIn.ar(2);
    stereoSig = DelayC.ar(stereoSig, 0.01, SinOsc.kr(0.1, [0, pi]).range(0, 0.01) * stereoWidth);
    LocalOut.ar(stereoSig * 0.5);

    // Filter envelope
    filterEnv = EnvGen.kr(
        Env.adsr(filterAttack, filterDecay, filterSustain, filterRelease),
        gate
    );

    // Apply resonant filter
    sig = RLPF.ar(
        stereoSig,
        cutoff.lag(0.9) * (1 + (filterEnv * filterEnvAmount)),
        resonance.linexp(0, 1, 1, 0.05)
    );

    // Apply main envelope and output
    sig = sig * env * amp * 0.33;
	Out.ar(out, DelayN.ar(sig,0.01,[0.007,0.009]));
}).add;
//------------------------------------------------------------
~init = ~init <> {


	var result;
	var folder = PathName("~/Music/yourDNASamples/harp");

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
			\root, Pseq([0,3,8,4,-2].stutter(32), inf),
			\dur, Pseq([0.2,0.2,0.4,0.2,0.2] * 0.5, inf),
			\octave, Pseq([4].stutter(2), inf),
			\func, Pfunc({|e| ~onEvent.(e)})
		);
	);
	Pdef(m.ptn).play(quant:0.1);

	synth = Synth.new(\warmRichSynth,[
		\freq, 36.midicps,
		\detune,0.003,
		\cutoff, 1111,
		\subOscLevel,2,
		\attackTime, 0.3,
		\decayTime, 0.5,
		\sustainLevel,0.7,
		\amp, 0.0

	]);

};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	synth.set(\gate,0);
	s.freeAllBuffers;

};

//------------------------------------------------------------
//------------------------------------------------------------
~onEvent = {|e|
	m.com.root = e.root;
	synth.set(\freq, (36+m.com.root).midicps);
};


//------------------------------------------------------------
~next = {|d|

	var dur = 0.2;
    var cs = [0,4,7];
    var notes = cs ++ (cs + 12) ++ (cs + 24) ++ (cs + 36);
	var index = (d.sensors.gyroEvent.x/pi).linlin(-0.5,0.5,notes.size-1,0.0); //up down
	var amp = m.accelMassFiltered.lincurve(0,1.5,-58,-17,-1);
	var sa = m.accelMassFiltered.lincurve(0,2.5,-70,-2,-8);
	var sf = m.accelMassFiltered.linexp(0,2.5, 500,5000);

	synth.set(\amp, sa.dbamp);
	synth.set(\cutoff, sf);

	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\note, notes[index.floor]);

	if(m.accelMassFiltered > 0.04,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:dur);
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

	// ACCEL
	// [m.accelMass * 0.1, m.accelMassFiltered.linlin(0,3,0,1)];
	
	// ROTATE
	// [m.rrateMass, m.rrateMassFiltered.linlin(0,1,0,1)];

	// X axis
	// [d.sensors.gyroEvent.x/pi]; // norm
	
	// Y axis
	// [d.sensors.gyroEvent.y/pi]; // norm

	// Z axis
	// [d.sensors.gyroEvent.z/(pi/2)]; // norm

	// device [I• ]
	[(d.sensors.gyroEvent.x/pi).linlin(-0.8,0.8,0.9,-0.9)]  //up down
	// [(d.sensors.gyroEvent.y/pi).linlin(-0.4,0.4,0.9,-0.9)]  //left right
	// [(d.sensors.gyroEvent.z/(pi/2)).linlin(-0.3,1.0,-0.9,0.9)]  //wrist rotate


};




