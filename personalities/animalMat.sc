var m = ~model;
m.midiChannel = 1;


SynthDef(\stereoSampler, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=20000, rq=1|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: 1, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.003], startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = RLPF.ar(sig, cutoff, rq);
    sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
    Out.ar(out, sig);
}).add;

//------------------------------------------------------------
// intial state
//------------------------------------------------------------
~init = ~init <> {

	~sampleFolderB = PathName("/Users/soh_la/Downloads/Voice recordings Music in Motion 2July2025/converted");
	~buffersB = ~sampleFolderB.entries.collect({ |path|
		("loading : "+ path.fileName).postln;
	    Buffer.read(s, path.fullPath);
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \stereoSampler,
			\bufnum, ~buffersB[15],
			\octave, Pxrand([3], inf),
			\rate, Pseq([0,2,-2].midiratio, inf),
			\start, Pxrand([0.04,0.1,0.28,0.525,0.7,0.75,0.86,0.9], inf),
			\note, Pseq([32], inf),
			\attack, 0.07,
			\release,0.2,
			\args, #[],
		)
	);

	Pdef(m.ptn).play(quant:0.25);
};


~deinit = {
	Pdef.removeAll;
	s.freeAllBuffers;

};
~stop = {
	"stop".postln;
	Pdef(~model.ptn).stop();
};

//------------------------------------------------------------
// triggers
//------------------------------------------------------------

// example feeding the community
~onEvent = {|e|
	// m.com.root = e.root;
	// m.com.dur = e.dur;

	// m.com.root.postln;
	// Pdef(m.ptn).set(\root, m.com.root);
};

~onHit = {|state|
};

//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var dur = m.accelMassFiltered.linlin(0,1,0.5,0.02);
	var start = m.accelMass.linlin(0,0.5,0.5,0.8);
	var amp = m.accelMass.linlin(0,1,0,4);

	Pdef(m.ptn).set(\amp, amp);
	Pdef(m.ptn).set(\dur, dur);
	// Pdef(m.ptn).set(\start, start);

	// Pdef(m.ptn).set(\filtFreq, m.accelMassFiltered.linexp(0,4,180,14000));
	//
	// if(m.accelMass > 0.1,{
	// 	if( Pdef(~model.ptn).isPlaying.not,{
	// 		Pdef(~model.ptn).resume(quant:0.25);
	// 	});
	// 	},{
	// 		if( Pdef(~model.ptn).isPlaying,{
	// 			Pdef(~model.ptn).pause();
	// 		});
	// });
};

~nextMidiOut = {|d|
};

//------------------------------------------------------------
// plot with min and max
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	[m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

// (
// var a = 1.0.linrand;
// var b = Array.linrand(1,0.0,1.0-a);
// var c = 1.0 - b - a;
// [a,b,c].flat
// )
//
