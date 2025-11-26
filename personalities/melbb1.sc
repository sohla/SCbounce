var m = ~model;
var bi = 0;
var dur = 0.14 * 1;
var limit = 8;
var step = 1;
var lastTime=0;

~buffers;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.9;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\drumkit2, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
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
	);
	sig = Mix.ar([sig]);
    sig = Balance2.ar(sig[0],sig[1], -1);
    Out.ar(out, ((0)!0 ++ sig) * amp * env);
    // Out.ar(out, ((0)!0 ++ sig ++ ((0)!0) ++ sig) * amp * env);

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
			\instrument, \drumkit2,			
			\bufnum, Pfunc{
        bi = bi + step;
				if(bi >= (7),{bi=0});
				~buffers[bi];
			},
			\octave, Pseq([0].stutter(8), inf),
			\start, 0.05,
			\note, Pseq([30], inf),
			// \dur, dur,
			\pan, Pwhite(-0.4,0.4),
			\attack, 0.02,

			\args, #[],
		)
	);


	Pdef(\shaker,
		Pbind(
			\instrument, \drumkit2,			
			\bufnum, Pfunc{
				~buffers[1];
			},
			\octave, Pseq([0].stutter(8), inf),
			\start, Pwhite(0.15,0.3),
			\note, Pseq([30], inf),
			\rate, Pxrand([0.5,1], inf),
			\dur, dur,
			\cnt, Pseries(0,1, inf),
			\clk, Pfunc({TempoClock.beats}),
			\pan, Pwhite(-0.4,0.4),
			\attack, 0.07,
			\legato, 0.2,
			\release, Pwhite(0.1,0.9),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		)
	);


	Pdef(m.ptn).play(quant:dur);
	Pdef(m.ptn).set(\bufnum, ~buffers[0]);

	Pdef(\shaker).play(quant:dur);


};

~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	Pdef(\shaker).remove;

	~buffers.do({|buf|
		postf("buffer dealloc [%] \n", buf);
		buf.free;
		// s.sync;
	});
};

//------------------------------------------------------------
~onEvent = {|e|
	
	if(e.clk > (lastTime + (0.14 * 2)),{
		lastTime = e.clk;
		if(m.accelMassFiltered > 1.5,{
				dur = 0.14/2;
		},{
				dur = 0.14
		});

	});
  true
};

//------------------------------------------------------------
~next = {|d|

	var rel = m.gyroYFiltered.clip(-0.5,0.5).lincurve(-0.5,0.5,0.3,0.01,3);
	var amp = m.accelMassFiltered.lincurve(0,1.0,0.2,1, -1);
	var roll = m.gyroXFiltered.lincurve(-0.2,0.4,1,4,-2) * 0.5;
	var sa = m.rrateMassFiltered.lincurve(0,0.3,0.1,0.35, -1);


	Pdef(m.ptn).set(\amp, amp * 0.3);
	Pdef(m.ptn).set(\release, rel);
	Pdef(m.ptn).set(\rate, roll);
	step = 2.pow(m.accelMassFiltered.lincurve(0,1.0,-1,0, -1));

	Pdef(\shaker).set(\amp, sa*0.7);	
	Pdef(m.ptn).set(\dur, dur);	


	if(m.accelMassFiltered > 0.07,{
		if( Pdef(m.ptn).isPlaying.not,{
      // bi = 8;
			Pdef(m.ptn).play(quant:dur*1);
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
	[m.accelMass, m.accelMassFiltered];

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].abs;
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];


};
// Buffer.cachedBuffersDo(s, {|b|b.postln})