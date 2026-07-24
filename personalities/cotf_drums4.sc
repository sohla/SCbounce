/*
gestures:    [strike, tilt]
description: Piece: 4-bar Manchester phrase with explicit variable-dur sequences (dotted 8ths, syncopation, Rest() markers) on ~beatClock, clock-derived slot lookup. Idle/tuning/curtain: single drum hit on strike, buffer chosen from drumSet by y-tilt.
sound:       Compressed drum-kit samples with FreeVerb tail; RLPF applied.
pitch:       None — sample kit. Rate driven by y-tilt during piece; no pitch change in tuning.
rhythm:      Piece: 64-clock-beat phrase with variable durs (1,2,3,4,6 and Rest(N)); other states: gestural single triggers throttled ~150 ms.
instruments: [Gravitone]
*/

var m = ~model;
var ob = ~outBus ? 0;
var lastTime = 0;
var bi = -1;
// music-downbeat alignment in clock beats
var phase = 4;
var group;

// dur vocab (clock beats = 16ths): 1=16th, 2=8th, 3=dotted 8th, 4=quarter, 6=dotted quarter, Rest(N)=silent event
// buffer indices: K=kick(0)  S=snare(5)  h=closed hat(2)  O=open hat(4)  M=tom mid(9)  t=tom hi(7)  L=tom low(11)  F=floor(13)
var samples1 = [ 0,      0,     5,    0,    0,      2,     5,      4];
var durs1    = [ 3, Rest(1),    2,    2,    3,      1,     3,      1];
var samples2 = [ 0,      0,     5,    0,    0,      0,     5,      4];
var durs2    = [ 3, Rest(1),    2,    2,    3,      1,     3,      1];
var samples3 = [ 0,    2,    5,     2,    5,   4];
var durs3    = [ 6,    2,    3,     1,    2,   2];
var samples4 = [ 0,      0,    7,   9,   11,  13,     4,       4];
var durs4    = [ 2, Rest(2),   2,   2,    2,   2,     3,       1];

var samples = samples1 ++ samples2 ++ samples3 ++ samples4;
var durs    = durs1    ++ durs2    ++ durs3    ++ durs4;

// numeric-dur mirror for clock lookup (Rest.value → its inner dur)
var numericDurs = durs.collect(_.value);
var patternLen  = numericDurs.sum;
var eventStarts = [0] ++ numericDurs.integrate.drop(-1);

// buffer indices for stateless single-hit triggers (y-tilt picks one):
// kick, closed-hat, snare, open-hat, tom-mid, tom-low, floor
var drumSet = [0, 2, 5, 4, 9, 11, 13];

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

//------------------------------------------------------------
~init = ~init <> {
	var folder = PathName("~/Music/cotf_samples/drums");

	topEnvironment.use{
		group = Group.new;
		postf("loading samples : % \n", folder);
		~buffers = folder.entries.collect({|path,i|
			Buffer.read(s, path.fullPath, action:{|buf|
				postf("buffer alloc [%] \n", buf);
				if(folder.entries.size - 1 == i, { "samples loaded".postln });
			});
		});

		Pdef(m.ptn,
			Pbind(
				\instrument, \drumkitt4,
				\out, ob,
				\group, group,
				// clock-derived slot lookup: pos → slot in eventStarts; Rest.wrapAt marks skip
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

		Pdef(m.ptn).play(~beatClock, quant: [~scoreBeatsPerBar * ~scoreEventsPerBeat, phase]);
		Pdef(m.ptn).set(\bufnum, ~buffers[0]);
		Pdef(m.ptn).pause;

	};

	// ~onResync in d.env (per-device dispatch — no cross-device clobber).
	~onResync = { |idx|
		topEnvironment.use {
			Pdef(m.ptn).stop;
			s.bind { group.freeAll };
			if (~roomState == \piece) {
				Pdef(m.ptn).play(~beatClock, quant: [~scoreBeatsPerBar * ~scoreEventsPerBeat, phase]);
			};
		};
	};
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	fork {
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		};
		if (~buffers.notNil) {
			~buffers.do({|buf|
				buf.free;
				s.sync;
				postf("buffer dealloc [%] \n", buf);
			});
			~buffers = nil;
		};
	};
};

//------------------------------------------------------------
~onRoomState = {|ctx|
	topEnvironment.use {
		switch(ctx.state,
			\idle,    { Pdef(m.ptn).pause; s.bind { group.freeAll } },
			\tuning,  { Pdef(m.ptn).pause; s.bind { group.freeAll } },
			\piece,   { Pdef(m.ptn).resume(~beatClock, quant: [~scoreBeatsPerBar * ~scoreEventsPerBeat, phase]) },
			\curtain, { Pdef(m.ptn).pause; s.bind { group.freeAll } },
			\silent,  { Pdef(m.ptn).set(\amp, 0) }
		);
	};
};

//------------------------------------------------------------
~idleNext = {|d, ctx|
	if (m.accelMassFiltered > 0.5, {
		if (TempoClock.beats > (lastTime + 0.15), {
			var idx = m.gyroYFiltered.clip(-1, 1).linlin(-1, 1, 0, drumSet.size - 0.001).asInteger;
			var buf = topEnvironment[\buffers][drumSet[idx]];
			var amp = m.accelMassFiltered.lincurve(0, 2.5, 0.4, 1, 1);
			(
				instrument: \drumkitt4,
				out: ob,
				group: group,
				bufnum: buf,
				amp: amp,
			).play;
			lastTime = TempoClock.beats;
		});
	});
};

~tuningNext  = ~idleNext;
~curtainNext = ~idleNext;

~pieceNext = {|d, ctx|
	var rate = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 0.5, 4, 1);
	var amp = m.accelMassFiltered.lincurve(0, 2.5, 0.6, 1, 1);
	Pdef(m.ptn).set(\amp, amp * 1.4);
	Pdef(m.ptn).set(\rate, rate);
	topEnvironment.use {
		if (m.accelMassFiltered > 0.02, {
			if (Pdef(m.ptn).isPlaying.not, {
				Pdef(m.ptn).resume(~beatClock, quant: [~scoreBeatsPerBar * ~scoreEventsPerBeat, phase]);
			});
		}, {
			if (Pdef(m.ptn).isPlaying, {
				Pdef(m.ptn).pause;
			});
		});
	};
};

//------------------------------------------------------------
~onTick    = {|ctx| };
~onHalf    = {|ctx| };
~onBeat    = {|ctx| };
~onBar     = {|ctx| };
~onPhrase  = {|ctx| };
~onSection = {|ctx| };
~onChord   = {|ctx| };
~onKey     = {|ctx| };
~onScale   = {|ctx| };

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = {|d,p| [m.accelMass, m.accelMassFiltered] };
