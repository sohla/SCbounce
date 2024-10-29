var m = ~model;
var synth;
var buffer;

m.accelMassFilteredAttack = 0.3;
m.accelMassFilteredDecay = 0.07;

//------------------------------------------------------------
SynthDef(\monoSampler, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=20000, rq=1|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.003], startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = RLPF.ar(sig, cutoff, rq);
    sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
    Out.ar(out, sig);
}).add;

SynthDef(\pullstretchMono, {|out, amp = 0.8, buffer = 0, envbuf = -1, pch = 1.0, div=1, speed = 0.01, splay = 0.5|
	var pos;
	// var mx,my;
	var sp;
	var mas;
	var len = BufDur.kr(buffer) / div;
	var lfo = LFSaw.kr( (1.0/len) * speed ,1,0.5,0.5);
	// my = MouseY.kr(0.01,1,1.0);//splay


	sp = Splay.arFill(12,
		{ |i| Warp1.ar(1, buffer, lfo, pch.lag(2),splay, envbuf, 8, 0.1, 2)  },
			1,
			1,
			0
	) * amp.lag(1);

	mas = HPF.ar(sp,245);

	Out.ar(out,mas);
}).add;
//------------------------------------------------------------
~init = ~init <> {
	var path = PathName("~/Downloads/yourDNASamples/HK laughing2-glued.wav");

	// var path = PathName("~/Downloads/yourDNASamples/HK lots of teddies.wav");
	postf("loading sample : % \n", path.fileName);

	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\pullstretchMono,[\buffer,buf,\pch,0.midiratio, \amp,0.4, \div, 4]);
	});
};

~deinit = ~deinit <> {
	synth.free;
	buffer.free;
};


//------------------------------------------------------------
~next = {|d|
	var amp = m.accelMass.linlin(0,1,0.00001,0.8);
	var speed= m.accelMassFiltered.linlin(0,1,0.25,0.35);
	var rate = m.accelMassFiltered.linlin(0,1,0.9,1.6);

	if(amp < 0.1, {amp = 0});

	synth.set(\pch, rate);
	synth.set(\speed, speed);
	synth.set(\amp, amp*0.2);
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
