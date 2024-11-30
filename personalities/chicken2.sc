var m = ~model;
var bi = 0;
~buffers;

m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay = 0.2;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.9;

//------------------------------------------------------------
SynthDef(\grobt, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=200, rq=0.3, subFreq=145|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	var cd = BufDur.kr(bufnum);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: cd * 2, doneAction: 2);
		  var kick = SinOsc.ar(XLine.kr(subFreq*2, subFreq*1, 0.01),0,0.4) * EnvGen.ar(Env.perc(0.01, 3.3), gate) * 0.3;

	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.0], startPos: start * BufFrames.kr(bufnum), loop: 0) * 1.6;
    sig = RHPF.ar(sig, cutoff, rq);
		sig = Compander.ar(sig, sig,
        thresh: -33.dbamp,
        slopeBelow: 1,
        slopeAbove: 0.5,
        clampTime:  0.01,
        relaxTime:  0.01
		);
		sig = Mix.ar([sig,kick]);
    sig = Balance2.ar(sig[0],sig[1], pan);
    Out.ar(out, sig * amp * env);
}).add;


//------------------------------------------------------------
~init = ~init <> {

	var folder  = PathName("~/Downloads/yourDNASamples/robt");
	postf("loading samples : % \n", folder);

	~buffers = folder.entries.collect({ |path,i|
		Buffer.read(s, path.fullPath, action:{|buf|
			postf("buffer alloc [%] \n", buf);
			if(folder.entries.size - 1 == i,{
				"samples loaded".postln;
			});
		});
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \grobt,
			\bufnum, Pfunc{
				bi = bi + 1;
				if(bi >= (~buffers.size-1),{bi=0});
				~buffers[bi];
			},
			\octave, Pseq([3,4].stutter(24), inf),
			\rate, Pseq([0,-3,-5,4,7,9,12,0].midiratio, inf),
			\amp, Pseq([1, 2, 2, 0.9,0.7,0.6 ,0.5 ,1] * 1.8, inf),
			\subFreq,Pxrand([65,255,440,180,245,100] , inf),
			\start, 0,
			\legato, Prand([0.1,0.5], inf),
      		\root, Pseq([0,7].stutter(12), inf),
			\note, Pseq([33], inf),
		 	// \dur, 0.4,//Pseq([0.4,0.4,0.4,0.4,0.4,0.4,0.4,0.4/3,0.4/3,0.4/3],inf),
		 	\pan,Pseq([-0.8],inf),
			\attack, 0.02,
			\release,0.2,
			\args, #[],
		)
	);

	Pdef(m.ptn).play(quant:0.2);
};

~deinit = ~deinit <> {
	Pdef(m.ptn).remove;

	~buffers.do({|buf|
		buf.free;
		s.sync;
		postf("buffer dealloc [%] \n", buf);
	});
};


//------------------------------------------------------------
~next = {|d|

	var sub = 2.pow(m.rrateMassFiltered.lincurve(0,0.2,0,1,-2).floor).reciprocal;
	Pdef(m.ptn).set(\dur, 0.4 * sub);

	if(m.rrateMassFiltered > 0.022,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.2);
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
	[m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
