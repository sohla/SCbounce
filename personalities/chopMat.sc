var m = ~model;
var buffer;
var synth;

//------------------------------------------------------------
m.rrateMassFilteredAttack = 0.8;
m.rrateMassFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\stereoSamplerAM, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1, ts=1, cutoff=20000, rq=0.4|
	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: ts, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.003], startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = RLPF.ar(sig, cutoff, rq);
    sig = Balance2.ar(sig[0], sig[1], pan.lag(2), amp * env);
    Out.ar(out, sig[0]);
}).add;

SynthDef(\pullstretchMono, {|out, amp = 0.8, buffer = 0, envbuf = -1, pch = 1.0, div=1, speed = 1, splay = 0.5|
	var pos;
	// var mx,my;
	var sp;
	var mas;
	var len = BufDur.kr(buffer) / div * 0.5;
	var lfo = LFSaw.kr( (1.0/len) * speed ,1,0.5,0.5);
	// my = MouseY.kr(0.01,1,1.0);//splay
	sp = Splay.arFill(12,
		{ |i| Warp1.ar(1, buffer, lfo, pch,splay, envbuf, 2, 0.3, 2)  },
			1,
			1,
			0
	) * amp.lag(0.3);

	mas = HPF.ar(sp,245);

	Out.ar(out,mas);
}).add;
//------------------------------------------------------------
~init = ~init <> {

	var path = PathName("~/Downloads/yourDNASamples/matt2.wav");
	postf("loading sample : % \n", path.fileName);

	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);

		synth = Synth(\pullstretchMono,[\buffer,buf,\pch,0.midiratio, \amp,0.4, \div, 10]);

		Pdef(m.ptn,
			Pbind(
				\instrument, \stereoSamplerAM,
				\bufnum, buf,
				\octave, Pxrand([3], inf),
				\rate, Pwhite(1),
				\root, 0,
				\note, Pseq([0,5,-7].stutter(8) + 33, inf),
				\attack, 0.07,
				\release, 0.2,
				\legato, 1,
				\args, #[],
			)
		);
		Pdef(m.ptn).play(quant:0.04);
	});

};

~deinit = ~deinit <> {
	Pdef(m.ptn).stop;
	buffer.free;
	synth.free;

	s.sync;
	Pdef(m.ptn).remove;
	postf("buffer dealloc [%] \n", buffer);
};

//------------------------------------------------------------
~next = {|d|

	var dur = m.accelMassFiltered.lincurve(0,3,0.2,0.04,-1);
	var leg= m.accelMassFiltered.linlin(0,1,0.6,1.2);
	var start = d.sensors.gyroEvent.z.linlin(-1,1,0.1,0.5);
	var amp = m.accelMass.lincurve(0,2.5,0,1.5,-5);
	var co = (d.sensors.gyroEvent.y / pi).linexp(-1,1,540,14000);
	var pan = d.sensors.gyroEvent.z.linlin(-1,1,-1,1);

	var samp = m.accelMass.linlin(0,1,0.001,0.8);
	var speed= m.accelMassFiltered.linlin(0,1,0.04,0.005);
	var rate = m.accelMassFiltered.linlin(0,2.5,3,1).floor.reciprocal;

	if(amp < 0.15, {amp = 0}, { amp = 1.1});
	Pdef(m.ptn).set(\amp, amp);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\cutoff, co);
	// Pdef(m.ptn).set(\ts, leg);
	Pdef(m.ptn).set(\start, start);
	Pdef(m.ptn).set(\pan, pan);


	//  synth.set(\pch, rate);
	synth.set(\speed, speed * 0.1);
	synth.set(\amp, amp * 0.2);

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