var m = ~model;
var synth;
var buffer;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.08;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\pullstretchStereo, {|out, amp = 1, bufnum = 0, envbuf = -1, pch = 1, div=1, speed = 0.5, splay = 0.1 ,pan=0, gate=1, ws=1|
	var len = BufDur.kr(bufnum) / div;
	var lfo = LFSaw.kr( (1.0/len) * speed,1);
	var sp = Splay.arFill(2,
		{ |i| Warp1.ar(2, bufnum, lfo.linlin(-1,1,0.0,0.65), pch * 2.pow(i) * 0.5,splay, envbuf, ws, 0.0 , 2)  },
			1,
			1,
			0
	) ;
	var env = EnvGen.ar(Env.adsr(0.4,0.1,0.9,1.0), gate, doneAction:2);
	var mas = HPF.ar(sp,45);
	var sig = Greyhole.ar(mas[0]) * 0.2;
	Out.ar(out,Pan2.ar(sig + mas,pan)* amp.lag(0.3) * env);
}).add;



//------------------------------------------------------------
~init = ~init <> {

	var path = PathName("~/Downloads/melSamples/sing/mel_sing_wet-001.wav");
	postf("loading sample : % \n", path.fileName);

	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
        var winenv = Env([0, 1, 0], [0.002, 1.5], [8, 1]);
        var z = Buffer.sendCollection(s, winenv.discretize, 1, action:{|eb|
					postf("buffer alloc [%] \n", buf);
					synth = Synth(\pullstretchStereo, [\bufnum, buf,  \envbuf, eb, \pch, 0.5]);
				});
	});
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	synth.onFree({
		postf("buffer dealloc [%] \n", buffer);
		buffer.free;
    });
	synth.set(\gate, 0);
};

//------------------------------------------------------------
~next = {|d|
	var amp = m.accelMassFiltered.lincurve(0.0,2.0,-50,0,-7);
  	var speed = m.gyroXFiltered.lincurve(-1,1,0.1,1.0,0);
  	var ws = m.gyroYFiltered.lincurve(-1,1,0.1,1.5,-2);
  	synth.set(\ws, ws);
  	synth.set(\speed, speed);
  	synth.set(\amp, amp.dbamp);

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
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right

  [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
