var m = ~model;
var bi = 0;
var dur = 0.11;

~buffers;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.9;

//------------------------------------------------------------
SynthDef(\drumkit, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.8, release=0.02, gate=1,cutoff=10, rq=1|
	var lr = rate * BufRateScale.kr(bufnum);
	var cd = BufDur.kr(bufnum);
  var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.0], startPos: start * BufFrames.kr(bufnum), loop: 0) ;
    sig = RHPF.ar(sig, cutoff, rq);
		sig = Compander.ar(sig, sig,
        thresh: -15.dbamp,
        slopeBelow: 1,
        slopeAbove: 0.5,
        clampTime:  0.01,
        relaxTime:  0.01
		) ;
		sig = Mix.ar([sig]);
    sig = Balance2.ar(sig[0],sig[1], pan);
    Out.ar(out, sig * amp * env);
}).add;
//--------------------------------------
~init = ~init <> {

	var folder  = PathName("~/Downloads/openLabSamples/kit");
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
			\instrument, \drumkit,			
      \type, \customVisualEvent,
	  \shape, \star,
			\sx, Pwhite(250,350),
			\sy, 300,
			\ex, Pkey(\sx),
			\ey, 380,
			// \startSize, 40,
			\endSize, 30,
			\rotation, pi / Pwhite(1.7,2.3),
			\fill, true,
			\startColor, Color.hsv(0.1,1,1.0,1),
			\endColor, Color.hsv(0.2,1,1.0,0.0),
      \duration, 0.4,

			\bufnum, Pfunc{
				if(bi >= (~buffers.size-1),{bi=0});
				~buffers[bi];
			},
			\octave, Pseq([5].stutter(24), inf),
			\start, 0,
			\note, Pseq([40], inf),
			\dur, Pseq([1] * dur, inf),
			\pan, Pwhite(-0.3,0.3),
			\attack, 0.01,
			\release,1.3,
			\args, #[],
		)
	);

	Pdef(m.ptn).play(quant:dur);
	Pdef(m.ptn).set(\bufnum, ~buffers[0]);

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

	var rate = m.rrateMassFiltered.linlin(0,1,1,1.4);
	var amp = m.accelMassFiltered.lincurve(0,2.5,0.02,1, 2);
	Pdef(m.ptn).set(\amp, amp * 3);
	Pdef(m.ptn).set(\rate, rate);


	Pdef(m.ptn).set(\viewID, d.port);
  Pdef(m.ptn).set(\startSize, 40 + (160 * amp));

	Pdef(m.ptn).set(\modulation, (
			type: \radial,
			freq: 3 ,
			amp: 3,
			harmonics: 2
	));


	// bi = (d.sensors.gyroEvent.y.abs / pi) * (~buffers.size-1);
	// bi = bi.asInteger;
	// bi = [0,1].choose;
  bi = ~buffers.size.rand;
	if(m.accelMassFiltered > 0.05,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:dur*2);
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
Buffer.cachedBuffersDo(s, {|b|b.postln})