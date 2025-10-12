var m = ~model;
var synth;
var buffer;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.08;

//------------------------------------------------------------
SynthDef(\bufGrain, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.8, release=5.2, gate=1,cutoff=20000, rq=1, rezf=200|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction:2);
	var sub = LFTri.ar(130/2,0,0.2).tanh;
	var sig = Splay.arFill(7,{|i|
		Warp1.ar(2, bufnum, start, rate * (i+1) * 0.5, 0.3, windowRandRatio:0.3)},
	1,1,0);
    sig = RLPF.ar(sig, cutoff, rq) + sub;
		sig = Resonz.ar(sig, rezf.lag(0.4), 0.05, 5)* amp.lag(0.9);
		// sig = AllpassN.ar(sig, 0.1, [0.09, 0.08], 8);
		// sig = JPverb.ar(sig,1, modDepth: 0.1, modFreq: 4.0, low: 1.0);

    Out.ar(out, sig[0] * env);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	var path = PathName("~/Downloads/yourDNASamples/brenton/BrentonVoice_09.wav");
	postf("loading sample : % \n", path.fileName);
	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\bufGrain,[\bufnum,buf, \rate, 1.1, \gate, 1 ]);
	});
};

~deinit = ~deinit <> {
	// synth.free;
	synth.set(\gate, 0);
	buffer.free;
};
//------------------------------------------------------------
~next = {|d|
	var amp = m.accelMassFiltered.linlin(0,2,0.00001,1);
	var start = (d.sensors.gyroEvent.z / pi).lincurve(0.0,1.0,0.28,0.5333,0);
	var rezf = (d.sensors.gyroEvent.y / pi).lincurve(0.0,1.0,130,260*3,0);

	if(amp < 0.04, {amp = 0});

	synth.set(\rezf, rezf);
	synth.set(\start, start);
	synth.set(\amp, amp * 3.4);

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
