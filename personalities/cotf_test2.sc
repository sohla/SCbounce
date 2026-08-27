/*
gestures:    [beat, shake, tilt]
description: Two engines, one shared \simple SynthDef, running in parallel — a long-lived Synth instance (accelMassFiltered → amp) alongside a Pdef pattern (rrateMassFiltered → amp + dur). Both track a local voice pool via a half-bar freq update.
sound:       Sustained filtered saw+sine drone with a rhythmic pattern of the same voice punctuating it. Motion (accel) opens the drone; rotation (rrate) opens the pattern.
pitch:       \freq for BOTH engines from voicePool.first wrapped to a pitch class near baseMidi (60 = C4).
rhythm:      Drone is continuous; pattern fires per-note with \dur driven by rrate.
instruments: [Template]
*/

var m = ~model;
var synth;            // long-lived
var group;            // dedicated Group for the Pdef's per-note synths
var baseMidi = 60;    // C4 — anchor pitch for voicePool wrap
var halfTime = 0;
var step = 0;

// the conductor used to hand pitch material down as ctx.voicePool.
// standalone, the pool is local.
var voicePool = [0, 3, 5, 7, 10];

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
// visual : one mark per pattern note. two engines share one pitch, so
// the drone is the ground and the pattern is what you can actually see —
// each note is a mark whose height is the shared pitch and whose weight
// is the pattern's filter. Atlas grammar G8 (event-triggered).
//
//   freq  -> vertical position   (\sy -> \ey)
//   ffreq -> stroke weight
//   amp   -> mark size
~init = ~init <> {
	// (a) Long-lived drone — one synth for the whole personality life.
	synth = Synth(\simple, [
		\freq,       ((voicePool.first.asInteger % 12) + baseMidi).midicps,
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
			\group,      group,

			\type, \customVisualEvent,
			\shape, \line,
			\sy, Pfunc{|e| (e[\freq] ? 440).explin(80, 2000, 0.7, -0.7) },
			\ey, Pkey(\sy),
			\startSize, Pfunc{|e| (e[\amp] ? 0.1).linlin(0, 0.7, 60, 260) },
			\endSize, 20,
			\startWidth, Pfunc{|e| (e[\ffreq] ? 1000).explin(200, 8000, 1, 9) },
			\endWidth, 0.4,
			\startColor, Color.new(0.7, 0.9, 1.0),
			\endColor, Color.new(0.2, 0.3, 0.6).alpha_(0.0),
			\duration, 1.2,

			\args,       #[],
		);
	);
	// Silent defaults so pre-first-tick events don't scream. ~next
	// overwrites immediately. Short attack/release fits per-note character.
	Pdef(m.ptn).set(\amp,     0);
	Pdef(m.ptn).set(\dur,     1);
	Pdef(m.ptn).set(\freq,    baseMidi.midicps);
	Pdef(m.ptn).set(\ffreq,   1000);
	Pdef(m.ptn).set(\attack,  0.01);
	Pdef(m.ptn).set(\release, 0.4);

	Pdef(m.ptn).play(quant: 4);
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
// Two engines, so every tick re-declares mappings for BOTH:
//   synth (drone) : m.accelMassFiltered drives amp
//   Pdef (pattern): `under` (rrate isolated from accel) drives amp + dur
// Drone (accel) opens with impact-driven amp + x-tilt filter. Pattern
// driven by `under` — smooth rotation opens amp + tightens dur, while
// strikes go straight to the drone instead.
//
// Pitch for BOTH engines updates each half-bar, same pitch so drone and
// pattern harmonize (unison octave). s.bind so both /n_set land on the
// same s.latency timeline.
~next = { |d|
	var under = m.accelMassFiltered.lincurve(0, 1.0, m.rrateMassFiltered.neg, 0, -1).neg.lincurve(0, 0.4, 0, 1, -1);

	Pdef(m.ptn).set(\viewID, d.port);

	synth.set(\amp,        m.accelMassFiltered.lincurve(0, 2.0, -60, -3, -1).dbamp);
	synth.set(\ffreq,      (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).lincurve(-0.5, 0.5, 500, 8000, 3));
	synth.set(\lagAttack,  0.02);
	synth.set(\lagRelease, 0.6);
	Pdef(m.ptn).set(\amp,   under.lincurve(0, 1.0, -60, -3, -4).dbamp);
	Pdef(m.ptn).set(\dur,   under.lincurve(0, 1.0, 2, 0.25, -2));
	Pdef(m.ptn).set(\ffreq, (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 800, 6000, 3));

	if (TempoClock.beats > (halfTime + 8), {
		var pitch;
		halfTime = TempoClock.beats;
		step = step + 1;
		pitch = ((voicePool.wrapAt(step).asInteger % 12) + baseMidi).midicps;
		s.bind {
			synth.set(\freq, pitch);
			Pdef(m.ptn).set(\freq, pitch);
		};
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d, p|
	var under = m.accelMassFiltered.lincurve(0, 1.0, m.rrateMassFiltered.neg, 0, -1).neg.lincurve(0, 0.4, 0, 1, -1	);
	[m.accelMassFiltered, m.rrateMassFiltered, under]
};
