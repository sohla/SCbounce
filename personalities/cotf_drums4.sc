var m = ~model;
var bi = -1;
// music-downbeat alignment inside a music bar, in ~beatClock.beats units.
var phase = 4;

// drum sound buffer index list : kick1(0), kick2(1), hihat close(2), hihat close soft(3), hihat open(4), snare(5), snare soft(6), tom hi(7), tom hi soft(8), tom mid(9), tom mid soft(10), tom low(11), tom low soft(12), floor(13), floor soft(14)
//
// Manchester / Madchester beat — 4-bar phrase using EXPLICIT variable-duration
// sequences with dotted rhythms and Rest() markers.
//
// two parallel arrays per bar:
//   samples : buffer index per event (any value at rest-events is ignored)
//   durs    : clock-beat duration per event. Rest(N) marks a silent event
//             of length N. numeric N marks a hit event of length N.
//
// duration vocabulary (in clock beats = 16ths):
//   1        16th note
//   2        8th note
//   3        dotted 8th (1.5 × 8th)
//   4        quarter note
//   6        dotted quarter (1.5 × quarter)
//   Rest(N)  silent event of N clock beats
//
// this is a proper variable-dur transcription — no uniform 16th grid, no
// "kick swap in at position 8" hack. hits are placed at their actual musical
// positions (some on the grid, some off it via dotted values), and rests
// mark deliberate silences within the flow rather than gaps absorbed into
// the previous event's dur.

// bar 1 — dotted 8th kick sets up the swagger, syncopated 8th kick push,
// dotted 8th snare on 4 that stretches into the open hat pickup.
//   K(dot8)  R(16)  S(8)  K(8)  K(dot8)  H(16)  S(dot8)  O(16)
//   positions: 0    3     4     6     8       11     12      15
var samples1 = [ 0,      0,     5,    0,    0,      2,     5,      4];
var durs1    = [ 3, Rest(1),    2,    2,    3,      1,     3,      1];

// bar 2 — same rhythm, ghost kick replaces the hihat pickup for density
var samples2 = [ 0,      0,     5,    0,    0,      0,     5,      4];
var durs2    = [ 3, Rest(1),    2,    2,    3,      1,     3,      1];

// bar 3 — dotted-quarter kick opens the bar (long held one), then dotted 8th
// snare and an 8th open hat at the end.
//   K(dot4)  H(8)  S(dot8)  H(16)  S(8)  O(8)
//   positions: 0   6        8      11    12   14
var samples3 = [ 0,    2,    5,     2,    5,   4];
var durs3    = [ 6,    2,    3,     1,    2,   2];

// bar 4 — fill: kick, explicit 8th rest, descending tom line at 8ths, then
// dotted 8th + 16th open hats spilling into the next bar's downbeat.
//   K(8)  R(8)  t(8)  T(8)  L(8)  F(8)  O(dot8)  O(16)
//   positions: 0   2  4     6     8     10    12       15
var samples4 = [ 0,      0,    7,   9,   11,  13,     4,       4];
var durs4    = [ 2, Rest(2),   2,   2,    2,   2,     3,       1];

// concatenate — 4 music 4/4 bars = 64 clock beats total
var samples = samples1 ++ samples2 ++ samples3 ++ samples4;
var durs    = durs1    ++ durs2    ++ durs3    ++ durs4;

// numeric-dur mirror for clock lookup. Rest.value returns .dur (see Rest.sc:36);
// SimpleNumber.value returns self. so .collect(_.value) works for both.
var numericDurs = durs.collect(_.value);
var patternLen  = numericDurs.sum;                          // 64
var eventStarts = [0] ++ numericDurs.integrate.drop(-1);   // slot start times

~buffers;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.98;

//------------------------------------------------------------
SynthDef(\drumkitt4, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
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
			\instrument, \drumkitt4,
			// both \dur and \bufnum are driven by ~beatClock, indexing into the
			// durs and samples arrays via the precomputed eventStarts. this is
			// a clock-derived form of Pseq(durs, inf) / Pseq(samples, inf) —
			// the arrays ARE the sequences, but access is time-driven rather
			// than natural iteration so pause/resume/anchor jumps stay aligned.
			//
			// when durs.wrapAt(bi) is a Rest, Pfunc's built-in .processRest
			// step (Patterns.sc:185) sets ~isRest=true on the event and yields
			// the Rest's inner dur value. Event.play then skips the synth
			// dispatch (Event.sc:427) but the pattern still advances by that dur.
			\dur, Pfunc{|e|
				var pos = (~beatClock.beats - phase).mod(patternLen);
				var slot = (eventStarts.indexOfGreaterThan(pos) ? samples.size) - 1;
				bi = slot.max(0);
				durs.wrapAt(bi)
			},
			\bufnum, Pfunc{|e|
				var pos = (~beatClock.beats - phase).mod(patternLen);
				var slot = (eventStarts.indexOfGreaterThan(pos) ? samples.size) - 1;
				~buffers[samples.wrapAt(slot.max(0))]
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

	// quant to the score bar. because the Pfunc lookups are clock-derived,
	// the pattern plays the right (sample, dur) pair for whatever slot the
	// clock lands on — no need to quant to the full patternLen (which would
	// be a ~32-sec startup delay at tempo 2).
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
	var amp = m.accelMassFiltered.lincurve(0,2.5,0.6,1,1);
	Pdef(m.ptn).set(\amp, amp*1.4);
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
