var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init bodies run under topEnvironment.use
var bi = 0;

// drum sound buffer index list : kick1, kick2, hihat close, hihat close soft, hit hat open, snare , snare soft, tom hi, tom hi soft, tom mid, tom mid soft, tom low, tom low soft, floor, floor soft
var drums = [3,0,3,5,8,9,10,11];

~buffers;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.98;

//------------------------------------------------------------
SynthDef(\drumkitt, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.01, sustain=0.3, release=0.4, gate=1,cutoff=14000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum);
	var cd = BufDur.kr(bufnum);
  var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.0], startPos: start * BufFrames.kr(bufnum), loop: 0) * env ;
    sig = RLPF.ar(sig, cutoff, rq);
		sig = Compander.ar(sig, sig,
        thresh: -15.dbamp,
        slopeBelow: 1,
        slopeAbove: 0.5,
        clampTime:  0.01,
        relaxTime:  0.01
		) ;
		// sig = Mix.ar([sig]);
    // sig = Balance2.ar(sig[0],sig[1], pan);
	sig = FreeVerb.ar(sig,0.1,1.1,0.4) ;
    Out.ar(out, sig * amp);
}).add;
//--------------------------------------
~init = ~init <> {
	var folder  = PathName("~/Music/cotf_samples/drums");

	topEnvironment.use{
	~scoreAnchorBeat = 3;

	postf("loading samples : % \n", folder);

	~buffers = folder.entries.collect({ |path,i|
		Buffer.read(s, path.fullPath, action:{|buf|
			postf("buffer alloc [%] \n", buf);
			if(folder.entries.size - 1 == i,{
				"samples loaded".postln;
			});
		});
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \drumkitt,
			\out, ob,
			\bufnum, Pfunc{|e|
				bi = bi + 1;
				if(bi >= (drums.size),{bi=0});
				~buffers[drums[bi]]
			},
			\octave, Pseq([5].stutter(24), inf),
			\start, 0,
			\note, Pseq([40], inf),
			\dur, Pseq([1,1,2,Rest(1),1,Rest(2)], inf),
			\pan, Pwhite(-0.1,0.1),
			\attack, 0.02,
			\decay,1,
			\args, #[],
		)
	);

	Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
	Pdef(m.ptn).set(\bufnum, ~buffers[0]);
	};
};

~deinit = ~deinit <> {
	Pdef(m.ptn).remove;

	~buffers.do({|buf|
		buf.free;
		s.sync;
		postf("buffer dealloc [%] \n", buf);
	});
};


//------------------------------------------------------------
~next = {|d|

	var rate = (d.sensors.gyroEvent.y / pi.half).lincurve(-1,1,0.5,4,1);
	var amp = m.accelMassFiltered.lincurve(0,2.5,0.2,1,1);
	Pdef(m.ptn).set(\amp, amp);
	Pdef(m.ptn).set(\rate, rate);
	topEnvironment.use{
		if(m.accelMassFiltered > 0.02,{
			if( Pdef(m.ptn).isPlaying.not,{
				Pdef(m.ptn).resume(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
				~onResync = { |idx|
					Pdef(m.ptn).stop;
					Pdef(m.ptn).play(~beatClock,
						quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
				};
			});
		},{
			if( Pdef(m.ptn).isPlaying,{
				Pdef(m.ptn).pause();
			});
		});
	};
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;
	
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	[m.accelMass, m.accelMassFiltered];

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
	// [m.gyroXFiltered.fold(-0.5,0.5).linlin(-0.5,0.5,-10,10).lcurve];
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];

};
// Buffer.cachedBuffersDo(s, {|b|b.postln})