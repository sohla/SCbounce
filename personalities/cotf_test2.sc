/*
gestures:    [beat, shake, tilt]
description: Two engines, one shared \simple SynthDef, running in parallel — a long-lived Synth instance (simple2 style, accelMassFiltered → amp) alongside a Pdef pattern (simple3 style, rrateMassFiltered → amp + dur). Both track the score via ~onHalf freq updates. Every gesture mapping lives inline in state ticks per §22.
sound:       Sustained filtered saw+sine drone with a rhythmic pattern of the same voice punctuating it. Motion (accel) opens the drone; rotation (rrate) opens the pattern.
pitch:       ~onHalf sets \freq for BOTH engines to ctx.voicePool.first wrapped to a pitch class near baseMidi (60 = C4).
rhythm:      Drone is continuous; pattern fires per-note on ~beatClock with \dur driven by rrate per state.
instruments: [Template]
*/

var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init bodies run under topEnvironment.use
var synth;            // long-lived (simple2 style)
var group;            // dedicated Group for the Pdef's per-note synths (§5)
var baseMidi = 60;    // C4 — anchor pitch for voicePool wrap

m.accelMassFilteredAttack = 0.98;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay = 0.1;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
// ONE SynthDef, used two ways:
//   (a) as a long-lived drone — Synth.new with gate held at 1, released
//       by ~deinit's synth.set(\gate, 0). ADSR sustains at its level
//       until then, then releases and self-frees via Done.freeSelf.
//   (b) as a per-note Pdef voice — Pbind's default \note event sends
//       gate=1 at start and gate=0 after \sustain seconds, so the ADSR
//       releases per event and each synth self-frees.
// ADSR sustain LEVEL is hardcoded (0.7) to avoid the collision with
// Pbind's ~sustain (a TIME value); Pbind would otherwise override the
// synth's \sustain arg with time-in-seconds and produce wrong ADSR.
SynthDef(\simple, {|out=0, amp=0.0, freq=440, gate=1,
    attack=0.01, decay=0.1, release=0.4, ffreq=1200,
    lagAttack=0.02, lagRelease=0.9|
	var env = EnvGen.kr(Env.adsr(attack, decay, 0.7, release), gate, doneAction: Done.freeSelf);
	var sig = Saw.ar(freq, 0.2, 0.1) + SinOsc.ar(freq / 2, 0, 1);
	var filter = RLPF.ar(sig, ffreq, 0.3) * 0.4;
	Out.ar(out, filter!2 * env * amp.lagud(lagAttack, lagRelease));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	topEnvironment.use{
		// (a) Long-lived drone — one synth for the whole personality life.
		synth = Synth(\simple, [
			\out,        ob,
			\freq,       ((~scoreVoicePool.first.asInteger % 12) + baseMidi).midicps,
			\amp,        0,
			\attack,     0.5,
			\decay,      0.1,
			\release,    1.0,
			\ffreq,      800,
			\lagAttack,  0.3,
			\lagRelease, 0.8,
		]);

		// (b) Pdef pattern — per-note synths spawn into the dedicated group.
		group = Group.new;
		Pdef(m.ptn,
			Pbind(
				\instrument, \simple,
				\out,        ob,
				\group,      group,
				\args,       #[],
			);
		);
		// Silent defaults so pre-first-tick events don't scream. State ticks
		// overwrite immediately. Short attack/release fits per-note character.
		Pdef(m.ptn).set(\amp,     0);
		Pdef(m.ptn).set(\dur,     1);
		Pdef(m.ptn).set(\freq,    baseMidi.midicps);
		Pdef(m.ptn).set(\ffreq,   1000);
		Pdef(m.ptn).set(\attack,  0.01);
		Pdef(m.ptn).set(\release, 0.4);

		Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
	};

	// ~onResync in d.env (per-device dispatch). Body wraps topEnvironment.use.
	// Long-lived synth is unaffected by seek — nothing to freeAll for the drone.
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
	// Release the drone (ADSR release → Done.freeSelf).
	synth.set(\gate, 0);
	// Free the Pdef's group + any in-flight per-note synths.
	fork {
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		};
	};
};

//------------------------------------------------------------
// Pattern + drone always play across all states — per-state amp/dur/ffreq
// is set by the state ticks. \silent mutes BOTH engines one-shot.
~onRoomState = { |ctx|
	switch(ctx.state,
		\idle,    { },
		\tuning,  { },
		\piece,   { },
		\curtain, { },
		\silent,  {
			synth.set(\amp, 0);
			Pdef(m.ptn).set(\amp, 0);
		}
	);
};

//------------------------------------------------------------
// State ticks — EVERY mapping inline, per state. Two engines, so each
// state re-declares mappings for BOTH synth and Pdef.
//   synth (drone) : m.accelMassFiltered drives amp
//   Pdef (pattern): m.rrateMassFiltered drives amp + dur

// idle — both engines quiet, slow, wide filter.
~idleNext = { |d, ctx|
	var under = m.accelMassFiltered.lincurve(0, 1.0, m.rrateMassFiltered.neg, 0, -1).neg.lincurve(0, 0.4, 0, 1, -1	);
	synth.set(\amp,        m.accelMassFiltered.lincurve(0, 1.0, -80, -24, -1).dbamp);
	synth.set(\freq,      (baseMidi + 12).midicps); 
	synth.set(\ffreq,      300);
	synth.set(\lagAttack,  0.1);
	synth.set(\lagRelease, 0.1);
	Pdef(m.ptn).set(\amp,   under.lincurve(0, 1.0, -70, -2, -4).dbamp);
	Pdef(m.ptn).set(\dur,   under.lincurve(0, 1.0, 4, 0.5, -1));
	Pdef(m.ptn).set(\ffreq, 200);
};

// tuning — both engines sparse and dim.
~tuningNext = { |d, ctx|
	synth.set(\amp,        0);//m.accelMassFiltered.lincurve(0, 1.0, -70, -25, -4).dbamp);
	synth.set(\ffreq,      400);
	synth.set(\lagAttack,  0.4);
	synth.set(\lagRelease, 1.0);
	Pdef(m.ptn).set(\amp,   m.rrateMassFiltered.lincurve(0, 1.0, -70, -25, -4).dbamp);
	Pdef(m.ptn).set(\dur,   2);
	Pdef(m.ptn).set(\ffreq, 800);
};

// piece — full mapping: accel opens drone (amp + x-tilt filter),
// rotation opens pattern (amp + dur), y-tilt drives pattern filter.
~pieceNext = { |d, ctx|
	synth.set(\amp,        m.accelMassFiltered.lincurve(0, 2.0, -60, -3, -1).dbamp);
	synth.set(\ffreq,      (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).lincurve(-0.5, 0.5, 500, 8000, 3));
	synth.set(\lagAttack,  0.02);
	synth.set(\lagRelease, 0.6);
	Pdef(m.ptn).set(\amp,   m.rrateMassFiltered.lincurve(0, 1.5, -60, -6, -1).dbamp);
	Pdef(m.ptn).set(\dur,   m.rrateMassFiltered.lincurve(0, 1.5, 2, 0.25, -2));
	Pdef(m.ptn).set(\ffreq, (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 800, 6000, 3));
};

// curtain — both engines fading, low filter.
~curtainNext = { |d, ctx|
	synth.set(\amp,        m.accelMassFiltered.lincurve(0, 1.0, -80, -30, -4).dbamp);
	synth.set(\ffreq,      400);
	synth.set(\lagAttack,  0.5);
	synth.set(\lagRelease, 1.4);
	Pdef(m.ptn).set(\amp,   m.rrateMassFiltered.lincurve(0, 1.0, -80, -35, -4).dbamp);
	Pdef(m.ptn).set(\dur,   3);
	Pdef(m.ptn).set(\ffreq, 600);
};

//------------------------------------------------------------
// Beat-locked pitch — freq for BOTH engines updates each half-bar,
// same pitch so drone and pattern harmonize (unison octave). s.bind so
// both /n_set land on the same s.latency timeline.
// `pitch` captured once (reuse across two .set calls).
~onHalf = { |ctx|
	var pitch = ((ctx.voicePool.first.asInteger % 12) + baseMidi).midicps;
	s.bind {
		synth.set(\freq, pitch);
		Pdef(m.ptn).set(\freq, pitch);
	};
};

~onTick    = { |ctx| };
~onBeat    = { |ctx| };
~onBar     = { |ctx| };
~onPhrase  = { |ctx| };
~onSection = { |ctx| };
~onChord   = { |ctx| };
~onKey     = { |ctx| };
~onScale   = { |ctx| };

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d, p| 
	var under = m.accelMassFiltered.lincurve(0, 1.0, m.rrateMassFiltered.neg, 0, -1).neg.lincurve(0, 0.4, 0, 1, -1	);
	[m.accelMassFiltered, m.rrateMassFiltered, under] 
};
