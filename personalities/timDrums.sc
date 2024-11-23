var m = ~model;
var buffer;
var pa = m.ptn ++ "A";
var pb = m.ptn ++ "B";


m.accelMassFilteredAttack = 0.5;
m.accelMassFilteredDecay = 0.9;

//------------------------------------------------------------
SynthDef(\monoSampler, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=500, rq=1|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
  var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction:2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.003], startPos: start * BufFrames.kr(bufnum), loop: 0)[0];
  sig = RHPF.ar(sig, cutoff, rq);
  sig = Pan2.ar(sig, pan);
	sig = FreeVerb.ar(sig,0.4,0.3);
    Out.ar(out, sig * amp * env);
}).add;


//------------------------------------------------------------
~init = {

	var path = PathName("~/Downloads/yourDNASamples/TR laughing2.wav");
	postf("loading sample : % \n", path.fileName);

	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		Pdef(pa,
			Pbind(
				\instrument, \monoSampler,
				\bufnum, buf,
				\octave, Pxrand([3], inf),
				\rate, 1,
			 \root, Pseq([0,7].stutter(24), inf),
				\note, Pseq([0,5,4,0]+38, inf),
				\attack, 0.07,
				\decay, 1.4,
				\pan, Pseq([-1,1], inf),
				\sustain,0.04,
				// \func, Pfunc({|e| Pdef(pa).set(\root, m.com.root)}),
				\args, #[],
			)
		);
		Pdef(pb,
			Pbind(
				\instrument, \monoSampler,
				\bufnum, buf,
				\octave, Pseq([3,3], inf),
				\rate, 1,
				\root, Pseq([0,-5,7,0].stutter(24), inf),
				\note, Pseq([0,14,14,0]+26, inf),
				\pan, Pseq([1,-1], inf),
				\attack, 0.07,
				\sustain,0.03,
				// \func, Pfunc({|e| Pdef(pb).set(\root, m.com.root)}),
				\args, #[],
			)
		);

		Pdef(pa).play(quant:0.125);
		Pdef(pb).play(quant:0.125);

	});
};

~deinit = ~deinit <> {
	Pdef(pa).remove;
	Pdef(pb).remove;
	buffer.free;
};


~play = ~play <> {
	Pdef(pa).play();
	Pdef(pb).play();
};

~stop = ~stop <> {
	Pdef(pa).stop();
	Pdef(pb).stop();
};
//------------------------------------------------------------
~next = {|d|

	var dur = 0.5 * 2.pow(m.accelMassFiltered.linlin(0,3,0,3)).reciprocal / 1.5;
	//var dur = m.accelMassFiltered.linlin(0,1,0.5,0.02);
	var start = d.sensors.gyroEvent.y.linlin(0,1,0.2,0.75);
	var amp = m.accelMass.linlin(0,1,0,1);
	var div = d.sensors.gyroEvent.z.linlin(-1,1,0,3).floor;
	var rels = [1.3,0.01];

	if(amp < 0.15, {amp = 0}, { amp = 1 });

	Pdef(pa).set(\amp, amp*1);
	Pdef(pb).set(\amp, amp*1);

	Pdef(pa).set(\start, start);
	Pdef(pb).set(\start, start.linlin(0,1,0.75,0.2));

	Pdef(pa).set(\dur,dur);
	Pdef(pb).set(\dur,dur);

	Pdef(pa).set(\release, rels[div.asInteger]);
	Pdef(pb).set(\release, rels[div.asInteger]);

};


//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [m.rrateMass, m.rrateMassFiltered];
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	[d.sensors.gyroEvent.x * 0.1, d.sensors.gyroEvent.y * 0.1, d.sensors.gyroEvent.z * 0.1];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
