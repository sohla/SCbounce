var m = ~model;
var buffer;

//------------------------------------------------------------
m.rrateMassFilteredAttack = 0.8;
m.rrateMassFilteredDecay = 0.2;

//------------------------------------------------------------
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
~init = ~init <> {

	var path = PathName("~/Downloads/yourDNASamples/STE-1004.wav");
	postf("loading sample : % \n", path.fileName);

	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);

		Pdef(m.ptn,
			Pbind(
				\instrument, \stereoSampler,
				\bufnum, buf,
				// \octave, Pxrand([3], inf),
				\rate, Pseq([0,12,-5,7].stutter(8).midiratio, inf),
				\start, Pseq([0.04,0.1,0.28,0.525,0.7,0.75,0.86,0.9], inf),
				\note, Pseq([33], inf),
				\attack, 0.07,
				\release,0.2,
				\args, #[],
			)
		);
		Pdef(m.ptn).play(quant:0.04);
	});

};

~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	postf("buffer dealloc [%] \n", buffer);
	buffer.free;
};

//------------------------------------------------------------
~next = {|d|

	var dur = m.accelMassFiltered.linlin(0,1,0.8,0.04).lag(2);
	var start = m.accelMass.linlin(0,0.5,0.5,0.8);
	var amp = m.accelMass.linlin(0,1,0,3);
	var oct = (d.sensors.gyroEvent.y / pi).linlin(-1,1,2,6).floor;
	if(amp < 0.06, {amp = 0}, { amp = 3 });
	Pdef(m.ptn).set(\amp, amp);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\octave, oct);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	// [m.rrateMass, m.rrateMassFiltered];
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	[d.sensors.gyroEvent.x / pi, d.sensors.gyroEvent.y / pi, d.sensors.gyroEvent.z / pi];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
};