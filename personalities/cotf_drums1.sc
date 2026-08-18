/*
gestures:    [strike, tilt]
description: Piece: rotating drum-kit Pdef on ~beatClock with variable dur (Pseq [1,1,2,Rest(1),1,Rest(2)]); idle/tuning/curtain: single drum hit on strike, buffer chosen from drumSet by y-tilt.
sound:       Compressed drum-kit samples with FreeVerb tail; RLPF applied. Piece = rolling groove; other states = discrete gestural hits.
pitch:       None — sample kit, fixed pitch per buffer. Rate driven by y-tilt during piece; no pitch change in tuning.
rhythm:      Piece: variable-dur cycle; other states: gestural single triggers throttled ~150 ms.
instruments: [Gravitone]
*/

var m = ~model;
var ob = ~outBus ? 0;
var lastTime = 0;
var bi = 0;
var group;
var loading = false;   // true while ~init is waiting on the sample reads;
                       // ~deinit clears it so a load in flight bails out
                       // instead of building an unreachable Pdef. See ~init.

// piece-time cycle order
var drums = [3,0,3,5,8,9,10,11];

// buffer indices for stateless single-hit triggers (y-tilt picks one):
// kick, closed-hat, snare, open-hat, tom-mid, tom-low, floor
var drumSet = [0, 2, 5, 4, 9, 11, 13];

~buffers;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.98;

//------------------------------------------------------------
SynthDef(\drumkitt, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
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

	loading = true;

	topEnvironment.use{
		postf("loading samples : % \n", folder);
		~buffers = folder.entries.collect({|path,i|
			Buffer.read(s, path.fullPath, action:{|buf|
				postf("buffer alloc [%] \n", buf);
				if(folder.entries.size - 1 == i, { "samples loaded".postln });
			});
		});
	};

	s.sync;
	postf("[drums1] all buffers loaded (%) \n", (topEnvironment[\buffers] ? []).size);

	if(loading.not,{
		postf("[drums1] load cancelled — unloaded while samples were loading \n");
	},{
		// Name any file that didn't come back rather than failing silently.
		// We still build: one bad sample costs its own notes, not the seat.
		(topEnvironment[\buffers] ? []).do({ |buf, i|
			if(buf.numFrames.isNil or: { buf.numFrames == 0 },{
				postf("[drums1] sample failed to load : index % \n", i);
			});
		});

		topEnvironment.use{
			group = Group.new;

			Pdef(m.ptn,
				Pbind(
					\instrument, \drumkitt,
					\out, ob,
					\group, group,
					\bufnum, Pfunc{|e|
						bi = bi + 1;
						if(bi >= (drums.size), { bi = 0 });
						~buffers[drums[bi]]
					},
					\octave, Pseq([5].stutter(24), inf),
					\start, 0,
					\note, Pseq([40], inf),
					\dur, Pseq([1,1,2,Rest(1),1,Rest(2)], inf),
					\pan, Pwhite(-0.1, 0.1),
					\attack, 0.02,
					\decay, 1,
					\args, #[],
				)
			);

			// start playing then immediately pause — ~onRoomState \piece resumes.
			// quant means no events fire before the pause lands.
			Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
			// cotf: seed envir so a stickless seat is silent — SC's Event default
			// amp is 0.1, and the ~*Next tick hooks (the only writers of \amp) run
			// only while the seat's device is enabled. Envir .set, not a Pbind key:
			// Pbind keys override the envir and would defeat the hooks' .set.
			Pdef(m.ptn).set(\amp, 0);
			Pdef(m.ptn).set(\bufnum, ~buffers[0]);
			Pdef(m.ptn).pause;

		};

		// ~onResync in d.env (per-device dispatch — no cross-device clobber).
		// Body in topEnvironment.use so ~beatClock / ~roomState resolve.
		// Seek: stop + freeAll; restart only if we're in \piece.
		~onResync = { |idx|
			topEnvironment.use {
				Pdef(m.ptn).stop;
				s.bind { group.freeAll };
				if (~roomState == \piece) {
					Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
				};
			};
		};
	});
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	loading = false;

	Pdef(m.ptn).remove;
	// idempotent: notNil guards let ~deinit fire twice safely (unload+load path).
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
// Room-state routing. Pattern only plays in \piece; other states pause it and
// let the state-gated ticks drive single hits instead. topEnvironment.use so
// ~beatClock / ~scoreBeatsPerBar / ~scoreEventsPerBeat resolve.
~onRoomState = {|ctx|
	topEnvironment.use {
		switch(ctx.state,
			\idle,    { Pdef(m.ptn).pause; s.bind { group.freeAll } },
			\tuning,  { Pdef(m.ptn).pause; s.bind { group.freeAll } },
			\piece,   { Pdef(m.ptn).resume(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat) },
			\curtain, { Pdef(m.ptn).pause; s.bind { group.freeAll } },
			\silent,  { Pdef(m.ptn).set(\amp, 0) }
		);
	};
};

//------------------------------------------------------------
// Idle / tuning / curtain share the same behaviour: fire one drum on strike,
// buffer selected from drumSet by y-tilt, throttled to ~150 ms.
~idleNext = {|d, ctx|
	if (m.accelMassFiltered > 0.5, {
		if (TempoClock.beats > (lastTime + 0.15), {
			var idx = m.gyroYFiltered.clip(-1, 1).linlin(-1, 1, 0, drumSet.size - 0.001).asInteger;
			var buf = topEnvironment[\buffers][drumSet[idx]];
			var amp = m.accelMassFiltered.lincurve(0, 2.5, 0.4, 1, 1);
			(
				instrument: \drumkitt,
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
	var amp = m.accelMassFiltered.lincurve(0, 2.5, 0.2, 1, 1);
	Pdef(m.ptn).set(\amp, amp);
	Pdef(m.ptn).set(\rate, rate);
	topEnvironment.use {
		if (m.accelMassFiltered > 0.02, {
			if (Pdef(m.ptn).isPlaying.not, {
				Pdef(m.ptn).resume(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
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
