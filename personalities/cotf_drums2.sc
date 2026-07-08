var m = ~model;
var bi = -1;
var dur = 2;
// where the music's downbeat sits inside a bar, in ~beatClock.beats units.
// this is the general replacement for `drums.rotate(N)`: it shifts alignment
// in TIME rather than in array positions, so it survives complex/variable-dur
// rhythms. rotate(2) with dur=2 was equivalent to phase=4 clock beats.
// tune by ear until drums[0] falls on the music's downbeat.
var phase = 4;

// drum sound buffer index list : kick1, kick2, hihat close, hihat close soft, hit hat open, snare, snare soft, tom hi, tom hi soft, tom mid, tom mid soft, tom low, tom low soft, floor, floor soft
// basic rock beat at 8th-note grid over one bar (sum(dur) == scoreBeatsPerBar * scoreEventsPerBeat == 8):
//   pos:  1   1+   2   2+   3   3+   4   4+
//   hit:  K   H    S   H    K   H    S   H
var drums = [0, 2, 5, 2, 0, 2, 5, 2];

~buffers;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.98;

//------------------------------------------------------------
SynthDef(\drumkitt2, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.01, sustain=0.3, release=0.4, gate=1, cutoff=14000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.0], startPos: start * BufFrames.kr(bufnum), loop: 0) * env;
	sig = RLPF.ar(sig, cutoff, rq);
	sig = Compander.ar(sig, sig,
		thresh: -15.dbamp,
		slopeBelow: 1,
		slopeAbove: 0.5,
		clampTime:  0.01,
		relaxTime:  0.01
	);
	sig = FreeVerb.ar(sig, 0.1, 1.1, 0.4);
	Out.ar(out, sig * amp);
}).add;
//--------------------------------------
~init = ~init <> {
	var folder = PathName("~/Music/cotf_samples/drums");

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
			\instrument, \drumkitt2,
			// derive index from ~beatClock so every bar restarts at drums[0]:
			//   barLen (in clock beats) = ~scoreBeatsPerBar * ~scoreEventsPerBeat
			//   position-in-bar         = (~beatClock.beats - phase) mod barLen
			//   event index             = round(position-in-bar / dur)
			// phase shifts alignment in clock beats, so it survives any dur
			// pattern (unlike drums.rotate, which only works for uniform dur).
			\bufnum, Pfunc{|e|
				var barLen = ~scoreBeatsPerBar * ~scoreEventsPerBeat;
				var pos = (~beatClock.beats - phase).mod(barLen);
				var idx = (pos / dur).round.asInteger;
				bi = idx.mod(drums.size);
				~buffers[drums[bi]]
			},
			\octave, Pseq([5].stutter(24), inf),
			\start, 0,
			\note, Pseq([40], inf),
			\dur, dur,
			\pan, Pwhite(-0.05, 0.05),
			\attack, 0.02,
			\decay, 1,
			\args, #[],
		)
	);

	// quant: [barLen, phase] fires the first event on the music's downbeat,
	// so the Pdef's schedule is phase-aligned from event 0 onwards. this is
	// what matters for complex/variable-dur patterns — the Pfunc's beatClock
	// derivation only re-aligns for uniform dur.
	Pdef(m.ptn).play(~beatClock, quant: [~scoreBeatsPerBar * ~scoreEventsPerBeat, phase]);
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
				Pdef(m.ptn).resume(~beatClock, quant: [~scoreBeatsPerBar * ~scoreEventsPerBeat, phase]);
				~onResync = { |idx|
					Pdef(m.ptn).stop;
					Pdef(m.ptn).play(~beatClock,
						quant: [~scoreBeatsPerBar * ~scoreEventsPerBeat, phase]);
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
	[m.accelMass, m.accelMassFiltered];
};
