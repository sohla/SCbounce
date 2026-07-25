/*
gestures:    [beat, shake, tilt]
description: Pdef pattern firing per-note Saw+Sine synths through an RLPF on ~beatClock; state sets pitch and envelope, gesture drives amp and filter cutoff (roll → ffreq)
sound:       pulsing filtered saw+sine; state-driven pitch and envelope shape; tuning slides down from ~88 to 84 over ~15 s
pitch:       per-state — idle holds A4 (69); tuning slides ~88 → 84; piece picks a pitch class from score voice pool, gesture-tilted octave 7–10; curtain inherits last freq
rhythm:      note per \dur ~beatClock tick; state sets dur (idle 1–2 from accel, tuning 0.75, piece 1 or 2 from accel, curtain 1)
instruments: [Template]
*/

var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init bodies run under topEnvironment.use
var lastTime = 0;
var idleNotes = [0,2,4,5,7,5,4,2];
var tuneTime = 0;
var group;   // dedicated Group for this personality's synths — lets
             // ~onResync fan gate=0 to all in-flight notes so nothing
             // gets stuck at sustain after a seek.
//------------------------------------------------------------

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.999;
m.rrateMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
// Simple sine synth with ADSR + detune. Per-note lifetime (self-frees
// via Done.freeSelf), driven by the Pdef in ~init. `\detune` is a freq
// multiplier — analogous to `\ptch` on the harp's stereoSampler.
SynthDef(\simple, {|out=0, amp=0.0, freq=440, attack=0.001, decay=0.03, sustain=0.8, release=0.59, gate=1, ffreq = 1440|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var sig = Saw.ar(freq, LFCub.ar(freq/2,0,pi),0.3) + SinOsc.ar(freq/2,0,1);
	var filter = RLPF.ar(sig, ffreq, 0.3);
    Out.ar(out, filter!2 * env * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	topEnvironment.use{
		group = Group.new;

		Pdef(m.ptn,
			Pbind(
				\instrument, \simple,
				\out, ob,
				\group, group,   // route every event's synth into our group
				\args, #[],

			);
		);

		Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		// cotf: seed envir so a stickless seat is silent — SC's Event default
		// amp is 0.1, and the ~*Next tick hooks (the only writers of \amp) run
		// only while the seat's device is enabled. Envir .set, not a Pbind key:
		// Pbind keys override the envir and would defeat the hooks' .set.
		Pdef(m.ptn).set(\amp, 0);
		Pdef(m.ptn).set(\dur, 1);
		Pdef(m.ptn).set(\freq, 60.midicps);

	};

	// ~onResync in d.env (per-device dispatch — no cross-device clobber).
	// Body in topEnvironment.use so ~beatClock etc. resolve.
	// freeAll wrapped in s.bind so it inherits s.latency and lands after
	// any /s_new bundle still in flight (see §6 for the timing analysis).
	~onResync = { |idx|
		topEnvironment.use {
			Pdef(m.ptn).stop;
			s.bind { group.freeAll };
			Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		};
	};
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	// Nuclear stop on unload. freeAll wrapped in s.bind so it lands
	// after any in-flight /s_new bundle (Pbind sends at s.latency). The
	// group.free itself doesn't need latency — it targets the group
	// node which our freeAll didn't touch, so nothing races on it.
	if (group.notNil) {
		s.bind { group.freeAll };
		group.free;
		group = nil;
	};
};

//------------------------------------------------------------
// ~next = {|d| };

//------------------------------------------------------------
// Room-state routing — Pdef stays playing across all states; per-state
// amp is set by the state-gated ticks (~idleNext etc.) so we only hear
// the synth during \piece. \silent handled one-shot here (no ~silentNext).
~onRoomState = {|ctx|
	switch(ctx.state,
		\idle,    { 
			Pdef(m.ptn).set(\attack, 0.03);
			Pdef(m.ptn).set(\release, 1.4);
		},
		\tuning,  { 
			tuneTime = TempoClock.beats;
			Pdef(m.ptn).set(\attack, 0.07);
			Pdef(m.ptn).set(\release, 0.4);
		},
		\piece,   { 
		},
		\curtain, { 
			Pdef(m.ptn).set(\attack, 0.03);
			Pdef(m.ptn).set(\release, 1.4);
		},
		\silent,  { 
			Pdef(m.ptn).set(\amp, 0); 
		}
	);
};

//------------------------------------------------------------
// State-gated ticks — same shape as cotf_harp1, minus the sample-only
// bits. Each fires at ~30 Hz while its state is current.
~idleNext = {|d, ctx|
	var amp = ((m.rrateMassFiltered) * 2.0).lincurve(0, 1.0, -60, -20, -4);
	var ffreq = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1.0, 1.0, 4000, 8000, 3);
	var dur = m.accelMassFiltered.lincurve(0, 2.5, 2, 1, -1);
	var pchi = m.gyroZFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0,idleNotes.size,-1).asInteger;

	Pdef(m.ptn).set(\ffreq, ffreq);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\dur, dur );
	Pdef(m.ptn).set(\freq, (81 +  idleNotes[pchi]).midicps);
};

~tuningNext = {|d, ctx|
	var amp = ((m.rrateMassFiltered) * 2.0).lincurve(0, 1.0, -60, -28, -4);
	var tt = 15.0;

	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\dur, 1.0.rrand(1.7));

	if( (TempoClock.beats-tuneTime) < tt, {
		var val = (TempoClock.beats-tuneTime) / tt;
		Pdef(m.ptn).set(\freq, (81 + (val.linexp(0, 1, 1, 0.0001) * 4)).midicps);
	},{
		Pdef(m.ptn).set(\freq, (81).midicps);

	});
};

~pieceNext = {|d, ctx|
	var amp = (m.accelMassFiltered + m.rrateMassFiltered).half.lincurve(0, 1.5, -70, -20, -1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 7, 10, 1).asInteger;
	var pitch = ctx.voicePool.choose.asInteger.wrap(0, 11);
	var durs = [2,1];
	var dur = m.accelMassFiltered.lincurve(0, 2.0, 0, durs.size-1, 1).asInteger;
	var ffreq = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1.0, 1.0, 400, 8000, 3);

	Pdef(m.ptn).set(\ffreq, ffreq);
	Pdef(m.ptn).set(\dur, durs[dur]);
	Pdef(m.ptn).set(\freq, (pitch + (12 * oct)).midicps);	
	Pdef(m.ptn).set(\amp, amp.dbamp * ctx.loudness.linlin(0, 1, 0.3, 1.3));
	Pdef(m.ptn).set(\detune, 1);
	Pdef(m.ptn).set(\attack, 0.01);
	Pdef(m.ptn).set(\release, 0.8);

};

~curtainNext = {|d, ctx|
	var amp = ((m.rrateMassFiltered) * 2.0).lincurve(0, 1.0, -60, -40, -4);
	var ffreq = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1.0, 1.0, 4000, 8000, 3);
	var dur = m.accelMassFiltered.lincurve(0, 2.5, 1, 0.2, -1);
	var pchi = m.gyroZFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0,idleNotes.size,-1).asInteger;

	Pdef(m.ptn).set(\ffreq, ffreq);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\dur, 1);
};

//------------------------------------------------------------
// Beat-aligned hooks. Empty stubs — fill in as ideas arise.
~onTick    = {|ctx| 
};

~onHalf    = {|ctx|
	// var pitch = ctx.voicePool.choose.asInteger.wrap(0, 11);
	// Pdef(m.ptn).set(\freq, (pitch + (12 * 7)).midicps);	
};

~onBeat    = {|ctx| 
 };

~onBar     = {|ctx|
	// "simple6 onBar %  (sec % / phr %)".format(ctx.barIdx, ctx.sectionId, ctx.phraseId).postln;
};

~onPhrase  = {|ctx| /* on phrase change */ };
~onSection = {|ctx| /* on section change */ };
~onChord   = {|ctx| /* on chord change */ };
~onKey     = {|ctx| /* on key change */ };
~onScale   = {|ctx| /* on active_scale change */ };

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [(m.accelMassFiltered + m.rrateMassFiltered).half, m.accelMassFiltered];
		// [(d.sensors.gyroEvent.z / pi)];//left right
	[m.gyroZFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0,1,-1)];

};
