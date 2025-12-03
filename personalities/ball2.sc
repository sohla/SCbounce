var m = ~model;
var buffer;
var dur = 0.1;

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.4;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.4;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------

SynthDef(\sampler, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440, attack=0.01, decay=0.1, sustain=0.9, release=0.2, gate=1,cutoff=20000, rq=1, loop=1|
	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
     var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: lr, startPos: start * BufFrames.kr(bufnum), loop: loop);
    sig = RLPF.ar(sig, cutoff, rq);
    // sig = Balance2.ar(sig[0], sig[1], pan);
		sig = Compander.ar(sig, sig,
						thresh: -32.dbamp,
						slopeBelow: 1,
						slopeAbove: 0.5,
						clampTime:  0.02,
						relaxTime:  0.01
				);

			sig = sig * amp * env;
			Out.ar(out, ((0)!0 ++ sig ++ ((0)!6) ++ sig));

}).add;
//------------------------------------------------------------
~init = ~init <> {
	// var path = PathName("~/Downloads/yourDNASamples/violin/Violin_02.wav");
	var path = PathName("~/Downloads/melSamples/hello/mel_hello1.wav");
	// var path = PathName("~/Downloads/yourDNASamples/bath/MrHeyHeyscrubadubdub-001.wav");
	// var path = PathName("~/Downloads/yourDNASamples/bath/Heatherbathtimerubberducky.wav");
	// var path = PathName("~/Downloads/yourDNASamples/TR laughing2.wav");
	// var path = PathName("~/Downloads/yourDNASamples/DC power of love.wav");

    var w = 0.1;
	postf("loading sample : % \n", path.fileName);

	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		Pdef(m.ptn,
			Pbind(
				\instrument, \sampler,
				\bufnum, buf,
				\octave, 3,
				\note, Pseq([11].stutter(6) + 38, inf),
				// \attack,0.1,
				\decay, 0.1,
				\sustain,0.01,
				\release,0.1,
				\dur, Pseq([dur * 1,dur * 1], inf),

				\args, #[],
			)
		);
		Pdef(m.ptn).play(quant:dur);
	});};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	fork{
		1.0.yield;
		postf("buffer dealloc [%] \n", buffer);
		buffer.free;
		s.sync;
	};
};

//------------------------------------------------------------
~next = {|d|
  var amp = m.rrateMassFiltered.lincurve(0,0.7,0.002,2, -1);
	var start = m.gyroYFiltered.fold(-1,1).lincurve(-1,1,0.0,1.0,0);

	Pdef(m.ptn).set(\start, start);

	if(amp < 0.2, { amp = 0; });
	Pdef(m.ptn).set(\amp, amp);



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
	// [m.accelMass, m.accelMassFiltered];

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].abs;
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	[m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

//   [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
// [d.sensors.digiInEvent[0]]

};
