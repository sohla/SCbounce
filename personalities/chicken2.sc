var m = ~model;
var bi = 0;
var synth;
~buffers;

//------------------------------------------------------------
SynthDef(\grobt, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=400, rq=1, subFreq=145|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	var cd = BufDur.kr(bufnum);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: cd * 2, doneAction: 2);
		  var kick = SinOsc.ar(XLine.kr(subFreq*2, subFreq*1, 0.01),0,0.4) * EnvGen.ar(Env.perc(0.01, 0.3), gate);

	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.0], startPos: start * BufFrames.kr(bufnum), loop: 0) * 1.4;
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
SynthDef(\treeWind, { |out, frq=111, gate=0, amp = 0, pchx=0|
	var env = EnvGen.ar(Env.asr(1.3,1.0,8.0), gate, doneAction:Done.freeSelf);
	var follow = Amplitude.kr(amp, 0.3, 0.5).lag(2);
	// var sig = Saw.ar(frq.lag(2),0.3 * env * amp.lag(1));
	var trig = PinkNoise.ar(0.01) * env * follow;
	var sig =  DynKlank.ar(`[[60 + 7 - 12 + pchx.lag(4)].midicps, nil, [2, 1, 1, 1]], trig);
	var dly = DelayC.ar(sig,0.03,[0.02,0.027]);
	Out.ar(out, dly);
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
			\amp, Pseq([1, 2, 2, 0.9,0.7,0.6 ,0.5 ,1] * 1.15, inf),
			\subFreq,Pxrand([65,255,440,180,245,100], inf),
			\start, 0,
			\legato, Prand([0.3,1.0], inf) *3,
      		\root, Pseq([0,7].stutter(12), inf),
			\note, Pseq([33], inf),
		 	\dur, Pseq([0.4,0.4,0.4,0.4,0.4,0.4,0.4,0.4/3,0.4/3,0.4/3],inf),
		 	\pan,Pseq([-0.8],inf),
			\attack, 0.02,
			\release,0.2,
			\args, #[],
		)
	);

	Pdef(m.ptn).play(quant:0.2);
	// synth = Synth(\treeWind, [\frq, 40, \gate, 1, \pchx, -2+5]);
};

~deinit = ~deinit <> {
	Pdef(m.ptn).remove;

	~buffers.do({|buf|
		buf.free;
		s.sync;
		postf("buffer dealloc [%] \n", buf);
	});
	// synth.free;
};


//------------------------------------------------------------
~next = {|d|

	// var amp = m.rrateMassFiltered.linlin(0,1,0,0.3);
	// synth.set(\amp, amp);

	if(m.accelMass > 0.07,{
		if( Pdef(~model.ptn).isPlaying.not,{
			Pdef(~model.ptn).play(quant:0.2);
		});
	},{
		if( Pdef(~model.ptn).isPlaying,{
			Pdef(~model.ptn).stop();
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
Buffer.cachedBuffersDo(s, {|b|b.postln})