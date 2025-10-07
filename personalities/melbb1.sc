var m = ~model;
var bi = 0;
var dur = 0.14 * 1;
var limit = 8;
~buffers;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.9;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.9;

//------------------------------------------------------------
SynthDef(\drumkit, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.8, release=0.3, gate=1,cutoff=40, rq=0.1, octave = 0|
	var lr = rate * BufRateScale.kr(bufnum);
	var cd = BufDur.kr(bufnum);
  var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1] * (octave * 12).midiratio, startPos: start * BufFrames.kr(bufnum), loop: 0) * 10;
    sig = RHPF.ar(sig, cutoff, rq);
		sig = Compander.ar(sig, sig,
        thresh: -5.dbamp,
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

	var folder  = PathName("~/Downloads/melSamples/melbb");
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
			\bufnum, Pfunc{
        bi = bi + 1;
				if(bi >= (limit),{bi=0});
				~buffers[bi];
			},
			\octave, Pseq([0].stutter(8), inf),
			\start, 0,
			\note, Pseq([30], inf),
			\dur, dur,//Pseq([1,Rest(1),2,2,1,Rest(1),1] * dur, inf),
      \legato, 0.1,
      \rate, 0.5,//Pseq([-12, -9, -5,-2,0].midiratio.stutter(12), inf),
			\pan, Pwhite(-0.4,0.4),
			\attack, 0.02,
			// \release,0.2,
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

	var rel = (d.sensors.gyroEvent.y / pi.half).clip(-0.5,0.5).lincurve(-0.5,0.5,0.3,0.01,3);
	var amp = m.accelMassFiltered.lincurve(0,2.5,0.2,1, -1);
	Pdef(m.ptn).set(\amp, amp);
	Pdef(m.ptn).set(\release, rel);

	// bi = (d.sensors.gyroEvent.y.abs / pi) * (~buffers.size-1);
	// bi = bi.asInteger;
	// bi = [0,1,10].choose;

	if(m.rrateMassFiltered > 0.005,{
		if( Pdef(m.ptn).isPlaying.not,{
      bi = 8;
			Pdef(m.ptn).play(quant:dur);
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
// Buffer.cachedBuffersDo(s, {|b|b.postln})