var m = ~model;
var synth;
var buffers;
var bl=false;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.8;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\samplerMT, {|bufnum=0, out=0, amp=1, rate=1, start=0, pan=0, freq=440, attack=0.01, decay=0.1, sustain=0.9, release=0.2, gate=1,cutoff=20000, rq=1, loop=1|
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
	sig = sig!2 * amp * env;
    Out.ar(out, (0)!0 ++ sig); // multi output
}).add;


//------------------------------------------------------------
~init = ~init <> {

	var folder  = PathName("~/Downloads/yourDNASamples/toots/Mtt");
	// var folder  = PathName("~/Downloads/yourDNASamples/drums");
	postf("loading samples : % \n", folder);

	buffers = folder.entries.collect({ |path,i|
		Buffer.read(s, path.fullPath, action:{|buf|
			postf("buffer alloc [%] \n", buf);
			if(folder.entries.size - 1 == i,{
				"samples loaded".postln;
        		
			});
		});
	});
};

//------------------------------------------------------------
~deinit = ~deinit <> {
//check if synth	
	
	if(synth.notNil, {
		synth.onFree({
			buffers.do({|buf|
			postf("buffer dealloc [%] \n", buf);
			buf.free;
			// s.sync;
			});
		});	
	});
	synth.set(\gate, 0);
};

//------------------------------------------------------------
~next = {|d|

	if(d.sensors.digiInEvent[0] == 1, {
    if(bl == false, {
    var ndx = (buffers.size -1).rand;
      bl = true;
      synth = Synth(\samplerMT, [\bufnum, buffers[ndx], \gate, 1, \rate, ([0]).choose.midiratio, \amp, 0.3]);
    });
 },{
    if(bl == true,{
        bl = false;
        synth.set(\gate, 0);
        synth = nil;

    });
 });
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

  // [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
	[d.sensors.digiInEvent[0],d.sensors.rrateEvent.x];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
