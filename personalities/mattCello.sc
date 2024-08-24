var m = ~model;
var buffer;

m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay = 0.6;
m.accelMassFilteredAttack = 0.8;
m.accelMassFilteredDecay = 0.9;

//------------------------------------------------------------
SynthDef(\stereoSampler, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=20000, rq=1|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: 2,doneAction: 2);
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
				\octave, Pxrand([3], inf),
				\note, Pwhite(33,36, inf).floor,
				\attack, 0.07,
				\decay, 0.2,
				\sustain,0.1,
				\release,0.2,
				\dur, Pseq([0.125 * 0.5] , inf),
				\args, #[],
			)
		);
		Pdef(m.ptn).play(quant:0.125);
	});
};
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	postf("buffer dealloc [%] \n", buffer);
	buffer.free;
};

//------------------------------------------------------------
~next = {|d|

	var dur = m.accelMassFiltered.linlin(0,1,0.5,0.02);
	var start = (d.sensors.gyroEvent.y / 2pi) + 0.5;
	var amp = m.accelMassFiltered.linlin(0,1,0,1);
	var rate= m.accelMass.linlin(0,1,0,2);

	if(amp < 0.03, {amp = 0}, {amp = 0.5 + (amp * 0.5)});


	Pdef(m.ptn).set(\amp, amp);
	Pdef(m.ptn).set(\start, start.linlin(0,1,0,1));
	Pdef(m.ptn).set(\rate, rate.linlin(0,2,0.25,1));

};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	// [m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	[m.accelMass * 0.2, m.rrateMass, (m.rrateMass + m.accelMass) * 0.2];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMass, m.rrateMassFiltered, d.sensors.rrateEvent.x];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
