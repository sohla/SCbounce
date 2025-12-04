var m = ~model;
var synth;
var buffer;
var notes = [0,-7];
var lastTime = 0;

var trig = false;

m.accelMassFilteredAttack = 0.98;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.9;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\pullstretchMonoQN, {|out, amp = 1, buffer = 0, envbuf = -1, pch = 1, div=1, speed = 0.01, splay = 0.3,pan=0, gate=1, delta=0.01, ff=45|
	var len = BufDur.kr(buffer) / div;
	var lfo = LFSaw.kr( (1.0/len) * speed ,1).range(0.0,0.99);
	var sp = Splay.arFill(2,
		{ |i| Warp1.ar(1, buffer, lfo.linlin(0,1,0.01,0.89), pch.lag(3) * (1 / ((i*delta)+1)) ,splay, envbuf, 8, 0.1 * (i+1), 4)  },
			1,
			1,
			0
	) ;
	var env = EnvGen.ar(Env.adsr(0.4,0.1,0.9,2.0), gate, doneAction:2);
	var mas = HPF.ar(sp,ff) * amp.lag(1.2);
	var sig = Compander.ar(mas, mas,
			thresh: -32.dbamp,
			slopeBelow: 1,
			slopeAbove: 0.5,
			clampTime:  0.02,
			relaxTime:  0.01
	);

	sig = Pan2.ar(sig,pan) * env;
	Out.ar(out, (0)!2 ++ sig);
	// Out.ar(out, ((0)!2 ++ sig ++ ((0)!4) ++ sig));

}).add;

//------------------------------------------------------------
~init = ~init <> {
	var path = PathName("~/Downloads/nicSamples/4_Dreams/2.wav");
	postf("loading sample : % \n", path.fileName);
	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\pullstretchMonoQN,[\buffer,buf, \amp,0.0, \div, 10]);
	});
};

~deinit = ~deinit <> {
	synth.onFree({
		postf("buffer dealloc [%] \n", buffer);
		buffer.free;
	});	
	synth.set(\gate, 0);
};


//------------------------------------------------------------
~next = {|d|

	var delta = m.gyroYFiltered.linlin(-1,1,-10,10).lcurve;
	var amp = m.accelMassFiltered.lincurve(0,1.5,0.0,1,1);
	var speed = m.gyroXFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0.1,0.001,-1);
	var ff = m.gyroYFiltered.lincurve(-1.0,1.0,45,1450,-1);

	if(amp < 0.01, {
		amp = 0;
	});

	if(TempoClock.beats > (lastTime + 14),{
			notes = notes.rotate(-1);
		lastTime = TempoClock.beats;
	});

	synth.set(\pch, notes[1].midiratio);

	synth.set(\speed, speed);
	synth.set(\amp, amp * 4);
	synth.set(\delta, delta * 0.05);
	synth.set(\ff, ff);
};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;
	
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	// [m.accelMass, m.accelMassFiltered];

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].abs;
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];


	// [m.gyroXFiltered.fold(-0.5,0.5)];
	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [m.gyroYFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0,1,-1)];
	[m.gyroXFiltered.fold(-0.5,0.5).linlin(-0.5,0.5,-10,10).lcurve];
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
