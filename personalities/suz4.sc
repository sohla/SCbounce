var m = ~model;
var buffers;
var bi = 0;
var dur = 0.15;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.8;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\stereoSampler1, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
	attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=20000, rq=0.9|
	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: 2,doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.003], startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = RLPF.ar(sig, cutoff, rq);// + osc;
	sig = Balance2.ar(sig[0], sig[1], pan, amp);
	sig = LeakDC.ar(sig * env);
	Out.ar(out, sig);
}).add;


//------------------------------------------------------------
~init = ~init <> {

	// var folder  = PathName("~/Downloads/openLabSamples/kit");
	// var folder  = PathName("~/Downloads/yourDNASamples/drums");
	var folder  = PathName("~/Downloads/melSamples/sing");

	postf("loading samples : % \n", folder);

	buffers = folder.entries.collect({ |path,i|
		Buffer.read(s, path.fullPath, action:{|buf|
			postf("buffer alloc [%] \n", buf);
			if(folder.entries.size - 1 == i,{   
				"samples loaded".postln;
                Pdef(m.ptn,
                    Pbind(
                        \instrument, \stereoSampler1,
                        \bufnum, buf,
                        \note, 28,
                        \attack,0.01,
                        \decay, 0.1,
                        \sustain,0.1,
                        \release,0.8,
						\octave, Pseq([2,3].stutter(2), inf),
                        \dur, dur,
                        \pan, Pwhite(-0.5,0.5),
                        \bufnum, Pfunc{
                            // bi = bi + 1;
                            if(bi >= (buffers.size-1),{bi=0});
                            buffers[bi];
                        },                        
                        \args, #[],
                    )
                );
                Pdef(m.ptn).play(quant:0.125);
			});
		});
	});
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;

	// s.bind{
    //     ~buffers.do({|buf|
    //         postf("buffer dealloc [%] \n", buf);
    //         buf.free;
    //     });
	// };
	fork{
		1.0.yield;
        buffers.do({|buf|
            postf("buffer dealloc [%] \n", buf);
            buf.free;
            s.sync;
        });
		s.sync;
	};
};

//------------------------------------------------------------
~next = {|d|

    var start = m.gyroZFiltered.lincurve(-1,1,0.01,0.74,0);
    var index = m.gyroYFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0,(buffers.size-1),0).asInteger;
    var res = m.gyroXFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,500,15000,-3);
    var amp = m.accelMassFiltered.lincurve(0,1.5,0.002,1, 2);
    var octave = 2;//m.gyroZFiltered.fold(-1,1).lincurve(-1,1,2,5,0).round;
    var spd = m.accelMassFiltered.lincurve(0,2.5,0,3, -2).floor;

    bi = index;
    
    if(amp < 0.02, { amp = 0; });
	Pdef(m.ptn).set(\amp, amp * 4.0);
    Pdef(m.ptn).set(\start, start);
    Pdef(m.ptn).set(\cutoff, res);
    // Pdef(m.ptn).set(\octave, octave);
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
