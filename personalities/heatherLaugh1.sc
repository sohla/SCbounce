var m = ~model;
var buffer;

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.9;

//------------------------------------------------------------
SynthDef(\hl, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=20000, rq=1|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.003], startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = RLPF.ar(sig, cutoff, rq);
    sig = Balance2.ar(sig[0], sig[1], pan,  env);
 		sig = Compander.ar(sig, sig,
						thresh: -32.dbamp,
						slopeBelow: 1,
						slopeAbove: 0.5,
						clampTime:  0.02,
						relaxTime:  0.01
				);
	   Out.ar(out, sig * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	var path = PathName("~/Downloads/yourDNASamples/HK laughing2-glued.wav");
	// var path = PathName("~/Downloads/yourDNASamples/STE-1002.wav");
	postf("loading sample : % \n", path.fileName);

	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);

		Pdef(m.ptn,
			Pbind(
				\instrument, \hl,
				\bufnum, buf,
				\octave, Pxrand([3,3,3,3,3,3,3,3], inf),
				\rate, 1,
				\legato, 2,
				\note, Pseq([33], inf),
				\attack, 0.07,
				\sustain,0.4,
				\decay, 0.01,
				\release,0.0,
				// \dur, Pseq([0.3] , inf),
				\amp,0.8,
				\args, #[],
			)
		);
		Pdef(m.ptn).play(quant:0.25);

	});
};


~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	postf("buffer dealloc [%] \n", buffer);
	buffer.free;
};


//------------------------------------------------------------
~next = {|d|

	var dur = m.accelMassFiltered.linlin(0,1,0.6,0.3);
	var start = (d.sensors.gyroEvent.y / 2pi) + 0.5;
	var amp = m.accelMass.linlin(0,1,0,6);
	var pan = d.sensors.gyroEvent.z.linlin(-1,1,-1,1);

	Pdef(m.ptn).set(\pan, pan);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\start, start.linlin(0,1,0,0.9));

	// if(amp < 0.15, {amp = 0});
	// Pdef(m.ptn).set(\amp, amp * 0.3);
	if(m.accelMassFiltered > 0.07,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.5/3);
			// Pdef(m.ptn).set(\start, start.linlin(0,1,0,0.9));
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
