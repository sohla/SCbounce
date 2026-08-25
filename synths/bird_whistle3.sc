// =========================================================================
// bird_whistle3.sc — whipBird's synth, with breath, and patterns to play with.
//
// This one is NOT the v1/v2 whistle engine. It is whipBird.sc's SynthDef
// almost verbatim — same two envelopes, same crack, same reverb and early
// reflections — plus a breath layer and a set of experimental patterns.
// bird_whistle.sc and bird_whistle2.sc are untouched; everything here is
// named 3 so all three can be loaded at once.
//
// WHAT IS UNCHANGED FROM whipBird
//   the whip crack        WhiteNoise * BPF(Pink, XLine(12000, 2000, 0.03), 0.05)
//                         + Resonz(Pink, XLine(9000, 1500, 0.03), 0.1, 0.2)
//                         on Env.perc(0.001, 0.03, curve: -8)
//   the whistle env       Env([0, 0, 1, 0], [gliss, 0.15, 0.2], [0, 2, -4])
//   the swoop contour     Env([.., .., .., ..], [swoopDelay, 0.15, 0.15], [\sine, \sine, -3])
//   mainEnv, FreeVerb2, the early reflections, DetectSilence
//
// THE ONE STRUCTURAL CHANGE — pitch
//   whipBird's swoop is absolute Hz: [200, 200, 400, 380] * pitchRand, so
//   you can only play it by abusing pitchRand as a ratio. Here the SAME
//   contour is expressed as ratios of a `freq` arg, taking the landing note
//   (380) as 1.0:
//                    200/380 = 0.526     400/380 = 1.053     380/380 = 1.0
//                    swoopFrom           swoopPeak           lands on freq
//   So the gesture is identical — up an octave, overshoot ~0.9 st, settle —
//   but `freq` now means the note, and patterns can use \midinote or \freq.
//   Defaults are whipBird's numbers, and `pitchRand` is still there on top
//   if you want to drive it the old way.
//
// WHAT WAS ADDED
//   breath      A swept band of pink noise falling from `breathTop` x the
//               note down onto `breathLand`, on a soft swell rather than a
//               spike. Randomised per note by `breathJit` (0 = identical
//               every time, 0.4 = default, 1 = wild): its attack, length,
//               level and sweep start all move, and the band itself wanders.
//   whip, tone  whipBird's two hard-coded 0.2 balance factors, given knobs,
//               so you can push the crack back when the breath comes up.
//   stretch     Scales every time constant at once — the whole gesture from
//               a chirp (0.2) to a long sigh (6). This is the fun one.
//
//   whipBird's `reverbTime` arg is dropped — FreeVerb2 has no time
//   parameter, so it never did anything. `damp` is exposed instead.
//
// Core UGens only, no buffers. One-shot voice, self-freeing via
// DetectSilence — so it plays from Pbind, not Pmono. Bench file, not COTF.
// =========================================================================

(
SynthDef(\birdWhistle3, { |
	out = 0, pan = 0, amp = 0.3, gate = 1,

	// ---- pitch --------------------------------------------------------
	freq = 380,               // the note it lands on
	pitchRand = 1,            // whipBird's extra multiplier, still here
	swoopFrom = 0.5263,       // whipBird 200/380 — an octave below
	swoopPeak = 1.0526,       // whipBird 400/380 — overshoot ~0.9 st

	// ---- the swoop, whipBird's timings --------------------------------
	swoopDelay = 0.03,        // flat hold before the slide starts
	riseTime = 0.15,
	settleTime = 0.15,
	settleCurve = -3,

	// ---- the whistle envelope, whipBird's timings ---------------------
	gliss = 0.01,             // amp entry delay, independent of the pitch
	ampRise = 0.15,
	ampFall = 0.2,

	stretch = 1,              // scales EVERY time constant at once

	// ---- the whip crack ------------------------------------------------
	whip = 0.2,               // whipBird's hard-coded 0.2, now a knob
	whipTop = 12000, whipEnd = 2000, whipTime = 0.03,

	// ---- breath (the addition) -----------------------------------------
	breath = 0.15,
	breathAtk = 0.03,
	breathDur = 0.18,
	breathTop = 3.5,          // sweep starts this many x above the note...
	breathLand = 1.0,         // ...and lands here (1 = on the note)
	breathRq = 0.9,           // wide = airy, narrow = whistly
	breathJit = 0.4,          // 0 = identical every note, 1 = wild

	// ---- balance / space -----------------------------------------------
	tone = 0.2,               // whipBird's other hard-coded 0.2
	reverbMix = 0.3, reverbSize = 0.8, damp = 0.3
|
	var whipEnv, whistleEnv, whipOsc, whistleOsc, sig, mainEnv;
	var rAtk, rDur, rLvl, rTop, bCf, puffEnv, puff;
	var room = reverbSize.clip(0.1, 0.9);
	var st = stretch.max(0.01);

	// Main envelope for the whole sound — whipBird's
	mainEnv = EnvGen.kr(Env.asr(0.01, 1, 0.5), gate);

	// Whip crack envelope — whipBird's
	whipEnv = EnvGen.ar(Env.perc(0.001, 0.03 * st, curve: -8), gate);

	// Rising whistle envelope with adjustable delay — whipBird's
	whistleEnv = EnvGen.ar(
		Env([0, 0, 1, 0], [gliss, ampRise, ampFall] * st, curve: [0, 2, -4]),
		gate
	);

	// Whip crack sound — whipBird's
	whipOsc = WhiteNoise.ar *
		BPF.ar(
			PinkNoise.ar,
			freq: XLine.kr(whipTop, whipEnd, (whipTime * st).max(0.002)),
			rq: 0.05
		) +
		Resonz.ar(
			PinkNoise.ar,
			XLine.kr(whipTop * 0.75, whipEnd * 0.75, (whipTime * st).max(0.002)),
			0.1,
			0.2
		);

	// Rising whistle — whipBird's contour, as ratios of `freq`
	whistleOsc = SinOsc.ar(
		freq: EnvGen.kr(
			Env(
				[freq * swoopFrom, freq * swoopFrom, freq * swoopPeak, freq] * pitchRand,
				[swoopDelay, riseTime, settleTime] * st,
				[\sine, \sine, settleCurve]
			),
			gate
		)
	) * whistleEnv;

	// ---- breath (added) -------------------------------------------------
	// Four Rands fixed per synth, all scaled by breathJit, so one knob takes
	// it from dead repeatable to wild.
	rAtk = 1 + (Rand(-1, 1) * breathJit);
	rDur = 1 + (Rand(-1, 1) * breathJit);
	rLvl = 1 + (Rand(-1, 1) * breathJit * 0.8);
	rTop = 1 + (Rand(-1, 1) * breathJit * 0.5);

	// Air falling onto the pitch, rather than a static hiss burst.
	bCf = XLine.kr(
		(freq * breathTop * rTop).clip(20, 18000),
		(freq * breathLand).clip(20, 18000),
		(breathDur * rDur * st).max(0.01)
	);
	bCf = (bCf * LFNoise2.kr(14).range(0.85, 1.18)).clip(20, 18000);

	puffEnv = EnvGen.kr(
		Env([0, 1, 0.4, 0],
			[(breathAtk * rAtk * st).max(0.004),
				(breathDur * rDur * st * 0.5).max(0.01),
				(breathDur * rDur * st).max(0.01)],
			curve: [\sin, -1, -2]),
		gate
	);

	puff = BPF.ar(PinkNoise.ar, bCf, breathRq)
		* LFNoise2.kr(26).range(0.5, 1.0)   // internal flutter — reads as air
		* puffEnv * breath * rLvl * 6;

	// Combine — whipBird's balance, with its two 0.2s now on knobs
	sig = (whipOsc * whipEnv * whip) + (whistleOsc * tone) + puff;
	sig = Pan2.ar(sig * 0.5, pan);

	sig = FreeVerb2.ar(sig[0], sig[1], mix: reverbMix, room: room, damp: damp);

	// Additional early reflections for forest feel — whipBird's
	sig = sig + DelayN.ar(
		sig,
		0.1,
		[0.033, 0.039, 0.045].collect({ |t|
			sig * LFNoise2.kr(0.1).range(0.01, 0.02) * DelayC.ar(sig, 0.1, t)
		}).sum
	);

	DetectSilence.ar(sig, time: 0.3, doneAction: 2);
	Out.ar(out, sig * amp * mainEnv);
}).add;
)


// =========================================================================
// EXPERIMENTAL PATTERNS
// Play one at a time. Each pushes a different axis of the mechanism.
// =========================================================================

// -------------------------------------------------------------------------
// 1. FLOCK — the aviary. Dense overlapping swoops, wide pitch scatter, hard
//    panning. Each bird gets its own breath because breathJit is per synth.
//    Push \dur down to 0.05 and it becomes a dawn chorus.
// -------------------------------------------------------------------------
(
Pbindef(\w3_flock,
	\instrument, \birdWhistle3,
	\dur,       Pexprand(0.08, 0.9),
	\freq,      Pexprand(700, 3200),
	\pan,       Pwhite(-1.0, 1.0),
	\amp,       Pexprand(0.06, 0.22),
	\stretch,   Pexprand(0.5, 1.6),
	\swoopFrom, Pwhite(0.4, 0.75),
	\whip,      Pexprand(0.05, 0.3),
	\breath,    Pwhite(0.08, 0.25),
	\breathJit, 0.6,
	\reverbMix, 0.4
).play;
)

Pbindef(\w3_flock).play;
Pbindef(\w3_flock).stop;


// -------------------------------------------------------------------------
// 2. MELODY — the swoop used as an instrument. Every note arrives by glide,
//    so the tune is legible but nothing ever starts on its pitch. Whip
//    pulled right back, breath forward, longer settle so notes hang.
// -------------------------------------------------------------------------
(
Pbindef(\w3_melody,
	\instrument, \birdWhistle3,
	\midinote,  Pseq([84, 86, 88, 91, 89, 88, 86, 84, 86, 88, 86, 84], inf),
	\dur,       Pseq([1, 0.5, 0.5, 1.5, 0.5, 0.5, 0.5, 1, 0.5, 0.5, 0.5, 2], inf),
	\pan,       Pwhite(-0.2, 0.2),
	\amp,       0.18,

	\stretch,    2.2,          // long enough to hear the note, not just the swoop
	\swoopFrom,  0.84,         // a scoop, not an octave leap
	\swoopPeak,  1.03,
	\swoopDelay, 0.04,
	\settleTime, 0.25,
	\ampFall,    0.45,

	\whip,      0.02,          // almost no crack
	\tone,      0.28,
	\breath,    0.18,
	\breathTop, 3.0,
	\reverbMix, 0.3
).play;
)

Pbindef(\w3_melody).play;
Pbindef(\w3_melody).stop;


// -------------------------------------------------------------------------
// 3. CALL AND ANSWER — swoopFrom BELOW 1 rises, ABOVE 1 falls. Alternating
//    them gives a question/answer pair. The falling one also lands quieter
//    and further off-centre, so it reads as the reply from another tree.
// -------------------------------------------------------------------------
(
Pbindef(\w3_answer,
	\instrument, \birdWhistle3,
	\dur,       Pseq([0.35, 0.35, 1.4], inf),
	\freq,      Pseq([1600, 1600, 1150], inf),
	\swoopFrom, Pseq([0.55, 0.55, 1.9], inf),    // rise, rise, FALL
	\swoopPeak, Pseq([1.06, 1.06, 0.94], inf),
	\pan,       Pseq([-0.4, -0.4, 0.55], inf),
	\amp,       Pseq([0.2, 0.2, 0.12], inf),
	\stretch,   Pseq([1, 1, 2.4], inf),

	\whip,      0.06,
	\breath,    Pseq([0.14, 0.14, 0.3], inf),
	\breathTop, Pseq([3.5, 3.5, 0.4], inf),      // the reply breathes UPWARD
	\breathJit, 0.5,
	\reverbMix, 0.45
).play;
)

Pbindef(\w3_answer).play;
Pbindef(\w3_answer).stop;


// -------------------------------------------------------------------------
// 4. BREATH ONLY — tone and whip near zero, breath long and wide. No pitch
//    at all, just air gestures moving through the room. Drop \breathRq to
//    0.15 and it turns back into whistling without any oscillator.
// -------------------------------------------------------------------------
(
Pbindef(\w3_breathOnly,
	\instrument, \birdWhistle3,
	\dur,        Pexprand(0.4, 2.5),
	\freq,       Pexprand(600, 2400),
	\pan,        Pwhite(-0.8, 0.8),
	\amp,        0.5,

	\tone,       0.005,
	\whip,       0.0,
	\breath,     Pwhite(0.3, 0.6),
	\breathAtk,  Pwhite(0.05, 0.2),
	\breathDur,  Pwhite(0.3, 0.8),
	\breathTop,  Pexprand(0.3, 6),   // above 1 falls, below 1 rises
	\breathLand, Pwhite(0.7, 1.4),
	\breathRq,   Pwhite(0.6, 1.6),
	\breathJit,  0.7,
	\stretch,    Pexprand(1, 3),
	\reverbMix,  0.5
).play;
)

Pbindef(\w3_breathOnly).play;
Pbindef(\w3_breathOnly).stop;


// -------------------------------------------------------------------------
// 5. STRETCH — the same gesture at wildly different speeds, so you can hear
//    what `stretch` actually does to the mechanism. 0.15 is a tick, 6 is a
//    slow sigh. Everything else is held still deliberately.
// -------------------------------------------------------------------------
(
Pbindef(\w3_stretch,
	\instrument, \birdWhistle3,
	\stretch,   Pseq([0.15, 0.4, 1, 2.5, 6], inf),
	\dur,       Pseq([0.5, 0.8, 1.2, 2, 4], inf),
	\freq,      1400,
	\pan,       Pseq([-0.6, -0.3, 0, 0.3, 0.6], inf),
	\amp,       0.2,
	\swoopFrom, 0.6,
	\whip,      0.12,
	\breath,    0.2,
	\breathJit, 0.2,
	\reverbMix, 0.35
).play;
)

Pbindef(\w3_stretch).play;
Pbindef(\w3_stretch).stop;


// -------------------------------------------------------------------------
// Single hits — re-evaluate to retrigger (one-shot voice, frees itself).
// -------------------------------------------------------------------------

// whipBird's original gesture, unchanged: octave swoop, landing on 380
Synth(\birdWhistle3, [\freq, 380, \amp, 0.3]);

// the same, stretched into a sigh
Synth(\birdWhistle3, [\freq, 380, \stretch, 5, \amp, 0.3]);

// a whistle-ish scoop rather than a leap
Synth(\birdWhistle3, [\freq, 1500, \swoopFrom, 0.85, \swoopPeak, 1.03,
	\stretch, 2.5, \whip, 0.02, \breath, 0.2, \amp, 0.25]);

// breath alone
Synth(\birdWhistle3, [\freq, 1500, \tone, 0.005, \whip, 0, \breath, 0.5,
	\breathDur, 0.6, \stretch, 2, \amp, 0.5]);

// falling
Synth(\birdWhistle3, [\freq, 1100, \swoopFrom, 2.0, \swoopPeak, 0.95,
	\stretch, 2, \amp, 0.25]);

s.meter;

// stop everything
Pbindef(\w3_flock).stop; Pbindef(\w3_melody).stop; Pbindef(\w3_answer).stop;
Pbindef(\w3_breathOnly).stop; Pbindef(\w3_stretch).stop;
