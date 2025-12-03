var m = ~model;
var buffer;
// var notes = [0,1,4,5,7,8,7,5,4,5,4,1,4,1,0,0];
var notes = [0,4,5,7,9,10,16,17,19];
// var notes = [19];

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;
m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.8;
m.gyroFilteredDecay = 0.4;

//------------------------------------------------------------
SynthDef(\stereoSampler1, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
	attack=0.01, decay=0.1, sustain=0.3, release=1.2, gate=1,cutoff=20000, rq=0.9|
	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: 2,doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.003], startPos: start * BufFrames.kr(bufnum), loop: 0);
	var hs = RHPF.ar(sig, [6000,7000], 0.2,13).tanh;
	sig = RLPF.ar(sig, cutoff, rq);// + osc;
	sig = Pan2.ar(hs + sig, pan, amp * env);
	// sig = FreeVerb.ar(sig,0.5,0.4);
	sig = LeakDC.ar(sig);
	// Out.ar(out, [((0)!0 ++ sig)]);
	Out.ar(out, ((0)!0 ++ sig ++ ((0)!6) ++ (sig*0.1)) * amp * env);

}).add;

//------------------------------------------------------------
~init = ~init <> {

	// var path = PathName("~/Downloads/yourDNASamples/brenton/BrentonVoice_02.wav");
	var path = PathName("~/Downloads/melSamples/mel_sing_dry-005.wav");
	// var path = PathName("~/Downloads/alessioSamples/ahAirEveryPig.wav");
	postf("loading sample : % \n", path.fileName);

	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		Pdef(m.ptn,
			Pbind(
				\instrument, \stereoSampler1,
				\bufnum, buf,
				// \octave, Pxrand([0,1,2,1,3,2], inf),
				\note, Pwhite(33,33, inf).floor,
				\start,Pwhite(0.0,0.9),
				\attack,0.01,
				\decay, 0.2,
				\sustain,0.1,
				\release,1.3,
				// \rate, Pwhite(0.3,3.0),
				\rate, Pslide(notes.midiratio, inf, Pkey(\range), 0, 0),

				// \rate, Pseq(notes.midiratio, inf),
				// \dur, Pseq([0.25], inf),
				\args, #[],
			)
		);
		Pdef(m.ptn).play(quant:0.125);
	});
};
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	fork{
		3.0.yield;
		postf("buffer dealloc [%] \n", buffer);
		buffer.free;
		s.sync;
	};
};

//------------------------------------------------------------
~next = {|d|

	var dur = m.accelMassFiltered.lincurve(0,0.5,0.4,0.07,-3);
	var start = m.gyroXFiltered.lincurve(0.0,1.0,0.1,0.9,0);
	var amp = m.accelMassFiltered.lincurve(0,2.5,0,1,-6);
	var rate= m.accelMass.linlin(0,1,0,2);
	var range = m.accelMassFiltered.lincurve(0,2.0,1,notes.size,-2).asInteger;
	var octave = m.gyroYFiltered.lincurve(-1.0,1.0,1,4,0).asInteger;
	var bal = m.gyroYFiltered.lincurve(-1.0,1.0,3,1,-2);

	if(amp < 0.02, {amp = 0});

	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\amp, amp * bal * 0.5);
	Pdef(m.ptn).set(\range, range);
	Pdef(m.ptn).set(\octave, octave);

};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	// [m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	// [m.accelMass * 0.2, m.rrateMass, (m.rrateMass + m.accelMass) * 0.2];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMass, m.rrateMassFiltered, d.sensors.rrateEvent.x];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
	//  [ ((m.gyroZFiltered.fold(-0.5,0.5) * 2)+1) + (m.gyroYFiltered + 1)] - 2 * 0.5 ;
	[m.gyroYFiltered];

};
