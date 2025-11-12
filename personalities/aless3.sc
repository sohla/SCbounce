var m = ~model;
var bi = 0;
var dur = 0.3 ;
~buffers;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;

//------------------------------------------------------------
SynthDef(\drumkitAAA, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.8, release=0.3, gate=1,cutoff=10, rq=1|
	var lr = rate * BufRateScale.kr(bufnum);
	var cd = BufDur.kr(bufnum);
  var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr,lr/4], startPos: start * BufFrames.kr(bufnum), loop: 0) ;
    sig = RHPF.ar(sig, cutoff, rq);
		sig = Compander.ar(sig, sig,
        thresh: -15.dbamp,
        slopeBelow: 1,
        slopeAbove: 0.5,
        clampTime:  0.01,
        relaxTime:  0.01
		) ;
	sig = Mix.ar([sig * 1, sig.tanh * 4]);
    sig = Pan2.ar(sig * amp * env, pan);
    Out.ar(out, ((0)!0++ sig) );
}).add;
//--------------------------------------
~init = ~init <> {
	var folder = PathName("~/Downloads/alessioSamples/vv");
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
			\instrument, \drumkitAAA,			
			\bufnum, Pfunc{
				bi = bi + 1;
				// bi = ~buffers.size.rand;
				if(bi >= (~buffers.size-1),{bi=0});
				~buffers[bi];
			},
			\start, 0.03,
			\legato,0.4,
			// \dur, dur,//Pseq([1,Rest(1),2,2,1,Rest(1),1] * dur, inf),
			\pan, Pwhite(-0.1,0.1),
			// \attack, 0.002,
			\release,0.01,
			\args, #[],
		)
	);

	Pdef(m.ptn).play(quant:dur);
	Pdef(m.ptn).set(\bufnum, ~buffers[0]);

};

~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	{
	~buffers.do({|buf|
		buf.free;
		s.sync;
		postf("buffer dealloc [%] \n", buf);
	});
	}.defer(0.3);
};


//------------------------------------------------------------
~next = {|d|

	var rate = m.rrateMassFiltered.linlin(0,1,0.6,3);
	var amp = m.accelMassFiltered.lincurve(0,2.5,0.4,1, -2);
	// var notes = [0.3,0.4,0.8,0.0,0.7] + 0.4;
	var notes = [0.3,1,3,5,7,11];
	var amps = [3,1,0.7,0.4,0.2] * 1;
	var index = m.gyroYFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0,notes.size-1,-1).asInteger;
	var attack = m.accelMassFiltered.lincurve(0.0,1.5,0.8,0.002,-1);
	dur= m.accelMassFiltered.lincurve(0,1.5,0.3,0.04, -1);
	Pdef(m.ptn).set(\amp, amp * amps[index]);
	Pdef(m.ptn).set(\rate, notes[index]);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\attack, attack);
	// bi = (d.sensors.gyroEvent.y.abs / pi) * (~buffers.size-1);
	// bi = bi.asInteger;
	// bi = [0,1,10].choose;
	if(m.accelMassFiltered > 0.1,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0);
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