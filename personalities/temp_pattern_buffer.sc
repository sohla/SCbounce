var m = ~model;
var buffer;
var dur = 0.15;

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
    Out.ar(out, sig!2 * amp * env);
}).add;
//------------------------------------------------------------
~init = ~init <> {
	var path = PathName("~/Downloads/yourDNASamples/violin/Violin_02.wav");
	// var path = PathName("~/Downloads/melSamples/hello/mel_hello3.wav");
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
				\note, Pseq([2].stutter(6) + 30, inf),
				// \attack,0.1,
				\decay, 0.1,
				\sustain,0.1,
				\release,0.04,
				\dur, dur,

				\type, \customVisualEvent,
				\cnt, Pseries(0,1, inf),
				\sx, Pfunc({ |e| cos(e.cnt / 6) * w * 0.5}),
				\sy, Pfunc({ |e| sin(e.cnt / 6) * w}),
				\ex, Pfunc({ |e| cos(e.cnt / 6) * w * 1}),
				\ey, Pfunc({ |e| sin(e.cnt / 6) * w * 2}),
				\shape, \line,
				\rotation, Pseg([0, 2pi], 2.5, 'lin', inf),
	      \duration, 2.7,
				\endSize, 200,
				\fill, true,
				\args, #[],
			)
		);
		Pdef(m.ptn).play(quant:0.125);
	});
};

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
	// var shapes = [\circle, \square, \line, \triangle, \star, \hexagon, \cross, \wave, \leaf, \spiral, \blobby];
	var poss = [0.1,0.19,0.4,0.59];
	// var poss = [0.1,0.2,0.3,0.4,0.5,0.6,0.7];
	var pos = m.gyroYFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0,poss.size-1,0).round;
	// var shape = m.gyroYFiltered.fold(-1,1).lincurve(-1,1,0.0,shapes.size-1,0).asInteger;
	// var rate = m.gyroXFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,1.9,2.0,0);
	var amp = m.rrateMassFiltered.lincurve(0.0,0.5,0.0,4.0,-1);
	var size = m.accelMassFiltered.lincurve(0.0,1.5,100,250,4);
	var attack = m.accelMassFiltered.lincurve(0.0,1.5,0.1,0.01,2);

	Pdef(m.ptn).set(\start, poss[pos]);
	Pdef(m.ptn).set(\attack, attack);
	// Pdef(m.ptn).set(\rate, rate);

	if(amp < 0.2, { amp = 0; });
	Pdef(m.ptn).set(\amp, amp);

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\startColor, Color.yellow.alpha_(amp.min(0.5)));
	Pdef(m.ptn).set(\endColor, Color.red.alpha_(0));
	Pdef(m.ptn).set(\startSize, size);
	// Pdef(m.ptn).set(\shape, shapes[shape]);

	// if(m.accelMassFiltered > 0.11,{
	// 	if( Pdef(m.ptn).isPlaying.not,{
	// 		Pdef(m.ptn).resume(quant:dur);
	// 	});
	// },{
	// 	if( Pdef(m.ptn).isPlaying,{
	// 		Pdef(m.ptn).pause();
	// 	});
	// });

	Pdef(m.ptn).set(\viewID, d.port);


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
