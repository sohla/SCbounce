var m = ~model;
var bi = 0;
var buffers;
m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.1;

SynthDef(\monoSampler, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=20000, rq=1|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	var cd = BufDur.kr(bufnum);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: cd * 2, doneAction: 2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.003], startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = RLPF.ar(sig, cutoff, rq);
    sig = Balance2.ar(sig[0], sig[0], pan);
		sig = Compander.ar(sig * 2, sig * 2,
						thresh: -35.dbamp,
						slopeBelow: 1,
						slopeAbove: 0.5,
						clampTime:  0.01,
						relaxTime:  0.01
		);
		Out.ar(out, sig * amp * env);
}).add;

//------------------------------------------------------------
// intial state
//------------------------------------------------------------
~init = ~init <> {

	var folder  = PathName("~/Downloads/yourDNASamples/blobblob");
	postf("loading samples : % \n", folder);

	buffers = folder.entries.collect({ |path,i|
		Buffer.read(s, path.fullPath, action:{|buf|
			postf("buffer alloc [%] \n", buf);
			if(folder.entries.size - 1 == i,{
				"samples loaded".postln;
			});
		});
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \monoSampler,
			\bufnum, Pfunc{
				bi = bi + 1;
				if(bi >= (buffers.size-1),{bi=0});
				buffers[bi];
			},
			\dur, 0.2,
			\octave, Pxrand([1,2], inf),
			\rate, Pseq([0,7,12].midiratio, inf),
			\legato, 0.5,
			\root, 0,
			\start, Pwhite(0, 0.1),
			\note, Pseq([33], inf),
			\amp, 2,
			\attack, 0.07,
			\release,0.07,
			\pan, -1,
			\args, #[],
		)
	);

	Pdef(m.ptn).play(quant:0.2);
};

~deinit = ~deinit <> {
	Pdef(m.ptn).remove;

	buffers.do({|buf|
		buf.free;
		s.sync;
		postf("buffer dealloc [%] \n", buf);
	});
};

//------------------------------------------------------------
~next = {|d|

	var cutoff = m.accelMassFiltered.linexp(0,2.5,100,11000);
	Pdef(m.ptn).set(\cutoff, cutoff);

	if(m.accelMassFiltered > 0.1,{
		if( Pdef(~model.ptn).isPlaying.not,{
			Pdef(~model.ptn).resume(quant:0.2);
		});
	},{
		if( Pdef(~model.ptn).isPlaying,{
			Pdef(~model.ptn).pause();
		});
	});
};

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