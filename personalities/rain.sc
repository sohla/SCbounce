var m = ~model;
var buffers;
var synths=[];
var lastTime=0;

m.accelMassFilteredAttack = 0.01;
m.accelMassFilteredDecay = 0.03;

SynthDef(\rainSampler, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=20000, rq=1|

	  var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: 1, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: rate, startPos: start * BufFrames.kr(bufnum), loop: 1);
    sig = RLPF.ar(sig, cutoff, rq);
    sig = Pan2.ar(sig, pan, amp * env);
    Out.ar(out, sig);
}).add;

//------------------------------------------------------------
// intial state
//------------------------------------------------------------
~init = ~init <> {

	var folder  = PathName("~/Downloads/yourDNASamples/rain");
	postf("loading samples : % \n", folder);

	buffers = folder.entries.collect({ |path,i|
		Buffer.read(s, path.fullPath, action:{|buf|
			postf("buffer alloc [%] \n", buf);
			// postf("buffer alloc [%] \n", buf.path.basename.splitext[0]);
			synths = synths.add(Synth(\rainSampler, [
				\rate, 1,
				\gate, 1,
				\amp, 0,
				\release, 10,
        \bufnum, buf
			]);
			);
			if(folder.entries.size - 1 == i,{
				"samples loaded".postln;
			});
		});
	});

};

~deinit = ~deinit <> {

	synths.do({|synth, i|
		synth.onFree({
			if(i >= 3,{
				"all synths on free".postln;
				buffers.do({|buf|
					{
						postf("buffer dealloc [%] \n", buf);
						buf.free;
						s.sync;
					}.fork;
				});
			});
		});
		synth.set(\gate, 0);	
	});

	// buffers.do({|buf|
	// 	buf.free;
	// 	s.sync;
	// 	postf("buffer dealloc [%] \n", buf);
	// });
};

//------------------------------------------------------------
~next = {|d|

	var levels = [	
		m.accelMassFiltered.clip2(0.5).linlin(0,0.5,0,1),
		m.accelMassFiltered.clip2(1.0).linlin(0.5,1.0,0,1),
		m.accelMassFiltered.clip2(2.0).linlin(1.0,2.0,0,1),
		m.accelMassFiltered.clip2(3.0).linlin(2.0,3.0,0,1)];

	var mix = [1,1,1,1.5];
	synths.do({|synth, i|
		synth.set(\amp, levels[i] * mix[i]);
	});
	
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




