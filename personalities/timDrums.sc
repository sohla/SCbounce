var m = ~model;
var pa = m.ptn ++ "A";
var pb = m.ptn ++ "B";

m.midiChannel = 1;


SynthDef(\monoSampler, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=20000, rq=1|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.003], startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = RLPF.ar(sig, cutoff, rq);
    sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
    Out.ar(out, sig);
}).add;

//------------------------------------------------------------
// intial state
//------------------------------------------------------------
~init = {|d|

	~sampleFolderB = PathName("/Users/soh_la/Downloads/Voice recordings Music in Motion 25June2024/converted");

	~sampleFolderB.entries.do({ |path,i|

		if(path.fileName.contains("TR laughing2.wav"),{

			postf("loading [%]: % \n", i, path.fileName);

			Buffer.read(s, path.fullPath, action:{ |buf|
				Pdef(pa,
					Pbind(
						\instrument, \monoSampler,
						\bufnum, buf,
						\octave, Pxrand([3], inf),
						\rate, 1,
						\root, Pseq([0].stutter(8), inf),
						\note, Pseq([33+7], inf),
						\attack, 0.07,
						\decay,0.1,
						\sustain,0.04,
						\args, #[],
					)
				);
				Pdef(pb,
					Pbind(
						\instrument, \monoSampler,
						\bufnum, buf,
						\octave, [2,3],
						\rate, 1,
						\root, Pseq([0].stutter(8), inf),
						\note, Pseq([33-12+7], inf),
						\attack, 0.07,
						\sustain,0.03,
						\args, #[],
					)
				);

				Pdef(pa).play(quant:0.125);
				Pdef(pb).play(quant:0.125);

			});
		});
	});


};

~deinit = {
	Pdef(pa).remove;
	Pdef(pb).remove;
	("deinit" + ~model.name).postln;

	// s.freeAllBuffers;
	Pdef.all.size.postln;
	Pdef.all.class.postln;

};
~play= {
	postf("play % \n",m.ptn);
	Pdef(pa).play();
	Pdef(pb).play();
};

~stop = {
	postf("stop % \n",m.ptn);
	Pdef(pa).stop();
	Pdef(pb).stop();
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
	var start = d.sensors.gyroEvent.y.linlin(0,1,0.2,0.75);
	var amp = m.accelMass.linlin(0,1,0,1);
	var div = d.sensors.gyroEvent.x.linlin(-1,1,1,2);
	var rels = [3,0.1];

	if(amp < 0.07, {amp = 0});

	Pdef(pa).set(\amp, amp);
	Pdef(pb).set(\amp, amp);

	Pdef(pa).set(\start, start);
	Pdef(pb).set(\start, start.linlin(0,1,0.75,0.2));

	Pdef(pa).set(\dur,div.floor.reciprocal * 0.25);
	Pdef(pb).set(\dur,div.floor.reciprocal * 0.25);

	Pdef(pa).set(\release, rels[div.asInteger]);
	Pdef(pb).set(\release, rels[div.asInteger]);

};

~nextMidiOut = {|d|
};

//------------------------------------------------------------
// plot with min and max
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	// [m.rrateMass, m.rrateMassFiltered];
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	[d.sensors.gyroEvent.x * 0.1, d.sensors.gyroEvent.y * 0.1, d.sensors.gyroEvent.z * 0.1];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

