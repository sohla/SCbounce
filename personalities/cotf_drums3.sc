var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init bodies run under topEnvironment.use
var bi = -1;
// music-downbeat alignment inside a music bar, in ~beatClock.beats units.
var phase = 4;

// drum sound buffer index list : kick1(0), kick2(1), hihat close(2), hihat close soft(3), hihat open(4), snare(5), snare soft(6), tom hi(7), tom hi soft(8), tom mid(9), tom mid soft(10), tom low(11), tom low soft(12), floor(13), floor soft(14)
//
// Manchester / Madchester beat — 4-bar phrase inspired by Reni's "Fools Gold".
//
// Encoding: each slot below is one 16th-note. an integer is a buffer index
// (a hit); `nil` is a rest slot (silence at that 16th). uniform \dur = 1, so
// the array reads left-to-right like a drum tab. rests earn their keep here —
// they're the *breath* between hits that gives Manchester its swagger, and
// they're what makes bar-by-bar variation legible without recomputing durs.
//
// legend used in the position/hit rows: K=kick(0)  S=snare(5)  h=closed hat(2)
//                                       M=tom mid(9)  t=tom hi(7)  F=floor(13)  O=open hat(4)
//   position: 1 e + a 2 e + a 3 e + a 4 e + a
//
// bar 1 — baseline Fools Gold groove with breath:
//     K . h . S h K . K . h . S h . h
//   kicks: 1, "+"of 2, 3   —  snare: 2, 4   —  closed hat on "+" of every beat
//   rests: 1e, 1a, 2a, 3e, 3a, 4+
var bar1 = [0, nil,  2,  nil,  5,  2,   0,  nil,  0,  nil,  2,  nil,  5,  2,  nil,  2];

// bar 2 — repeat. Manchester grooves settle into repetition; this is the
// pocket that bar 3/4 will push against.
var bar2 = bar1;

// bar 3 — building tension: ghost kick on "a" of 2, extra push kick on "+" of 4:
//     K . h . S h K K K . h . S h K h
//   more density in the second half, sets up the bar 4 fill.
var bar3 = [0, nil,  2,  nil,  5,  2,   0,  0,    0,  nil,  2,  nil,  5,  2,  0,    2];

// bar 4 — fill: first half is the groove, second half is a descending tom
// figure into a floor tom hit and an open-hat crash that spills into the
// next bar's downbeat.
//     K . h . S h K . M M t t F F O O
//   toms cascade: mid → mid → hi → hi → floor → floor, then open hats
var bar4 = [0, nil,  2,  nil,  5,  2,   0,  nil,  9,  9,    7,  7,    13, 13, 4,    4];

// concatenate — 64 slots = 4 music 4/4 bars = 64 clock beats.
// swap in your own bar arrays to compose different phrases; anything summing
// to a multiple of the barLen quant works.
var samples = bar2  ++ bar4 ++ bar3 ++ bar1;
var patternLen = samples.size;  // 64 clock beats

~buffers;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.98;

//------------------------------------------------------------
SynthDef(\drumkitt3, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
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
			\instrument, \drumkitt3,
			\out, ob,
			// uniform 16th grid — every event advances one 16th. rest slots
			// are encoded as `nil` in samples[]; the Pfunc returns Rest()
			// for those, which sets ~isRest on the event and Event.play
			// skips the synth dispatch (Rest.sc:12, Event.sc:427). the
			// event still consumes its 1-tick dur, so the pattern advances
			// uniformly through all 64 slots per phrase.
			\dur, 1,
			\bufnum, Pfunc{|e|
				var pos = (~beatClock.beats - phase).mod(patternLen);
				var slot = pos.floor.asInteger.mod(samples.size);
				var s = samples[slot];
				bi = slot;
				if(s.isNil, { Rest() }, { ~buffers[s] })
			},
			\octave, Pseq([5].stutter(24), inf),
			\start, 0,
			\note, Pseq([40], inf),
			\pan, Pwhite(-0.05, 0.05),
			\attack, 0.02,
			\decay, 1,
			\args, #[],
		)
	);

	// quant to the score bar — Pfunc's clock-derived slot lookup handles
	// alignment across the full 64-slot phrase regardless of which bar we
	// enter on. phase pushes fire time to the music downbeat.
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
	var amp = m.accelMassFiltered.lincurve(0,2.0,0.5,1,1);
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
