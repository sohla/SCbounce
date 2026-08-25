// =========================================================================
// bird_whistle2.sc — bird_whistle.sc + whipBird.sc's swoop/gliss mechanism.
//
// v1 (bird_whistle.sc) is left alone; everything here is named with a 2
// (\birdWhistle2, \whistleTune2, ...) so both files can be loaded at once
// and A/B'd. Read v1's header for the whistle-acoustics research — all of
// it still applies and is carried over unchanged.
//
// WHAT CAME FROM whipBird.sc
//
//   swoopDelay   whipBird's freq env is Env([200, 200, 400, 380],
//                [swoopDelay, 0.15, 0.15]) — the first segment HOLDS at the
//                start pitch before the sweep begins. v1 had no such hold;
//                it started gliding immediately. That flat moment before the
//                slide is most of what makes the gesture read as a swoop
//                rather than a portamento.
//
//   overshoot    Those levels go 200 -> 400 -> 380: the sweep passes THROUGH
//                the target and eases back down to it. v1 climbed straight
//                to pitch and stopped. The overshoot is why whipBird's
//                gliss sounds like a living thing landing on a note.
//                `overshoot` + `settleTime` here.
//
//   gliss        whipBird delays the AMPLITUDE env separately —
//                Env([0, 0, 1, 0], [gliss, 0.15, 0.2]). The pitch is already
//                moving while the note is still silent, so you hear it enter
//                partway up the swoop. gliss and swoopDelay together decide
//                how much of the slide you actually hear:
//                  gliss < swoopDelay  -> you hear the flat start, then the slide
//                  gliss > swoopDelay  -> you drop in mid-slide, already moving
//
//   the breath   whipBird's crack is a downward-swept resonant band on pink
//                noise: BPF(PinkNoise, XLine.kr(12000, 2000, 0.03), rq 0.05).
//                v1's breath was a static HPF'd white-noise perc env, which
//                is why it clicked. Slowed right down, widened, and aimed at
//                the note instead of at nothing, that same sweep becomes air
//                LANDING on the pitch. `breathTop` -> `breathLand`.
//
// THE BREATH, and its randomness
//
//   Every part of the breath is jittered per note by `breathJit` (0 = off,
//   perfectly repeatable; 0.4 = default): its attack, its length, its level,
//   and where the sweep starts. On top of that the band wanders (LFNoise2)
//   and the level flutters, so it is never the same shape twice and never a
//   clean envelope. Under PmonoArtic a new synth is born at every phrase
//   start, so each phrase gets its own breath. That is where you hear it.
//
//   `breath` is a plain level knob here — it is NOT the same scale as v1's
//   (v1 multiplied it by 0.05 internally). Start around 0.2.
//
// Carried over from v1 unchanged: Lag portamento, the Vibrato UGen with its
// delay/onset/rateVariation, warble, the +/-15 cent jitter, the transient
// air band, tremolo coupling, and the small bright per-note room.
// `air` keeps the percussive Env.perc(atk, airDur) shaping from your edit —
// airDur is just that hard-coded 0.1 given a knob.
//
// Core UGens only, no buffers. Bench file — nothing here is wired to COTF.
// =========================================================================

(
SynthDef(\birdWhistle2, { |
	// ---- note ---------------------------------------------------------
	out = 0, freq = 1500, amp = 0.2, pan = 0, gate = 1,
	portTime = 0.08,          // legato glide between notes under Pmono

	// ---- attack -------------------------------------------------------
	atk = 0.035,
	rel = 0.14,               // the labial stop: soft, not a cut
	sag = 0.88,
	gliss = 0.02,             // whipBird: amp entry delay, INDEPENDENT of pitch

	// ---- swoop (whipBird's mechanism) ---------------------------------
	slideFrom = -3,           // semitones BELOW the note to start from
	swoopDelay = 0.035,       // whipBird: hold flat down there before sliding
	slideTime = 0.09,         // then sweep up over this long (\sine, as whipBird)
	overshoot = 0.8,          // whipBird: sail PAST the note by this many st
	settleTime = 0.07,        // and ease back down onto it over this long
	settleCurve = -3,         // numeric: SynthDef args cannot default to a Symbol
	slideTo = -0.3,           // droop on release
	slideOut = 0.2,

	// ---- vibrato ------------------------------------------------------
	vibRate = 5.5,
	vibDepth = 0.018,         // proportion of freq; 0.018 ~= 31 cents
	vibDelay = 0.25,
	vibOnset = 0.35,
	vibRateVar = 0.06,
	vibDepthVar = 0.12,
	trem = 2.5,

	// ---- warble (pitch jump, NOT vibrato) ------------------------------
	warbleRate = 6,
	warbleDepth = 0,
	warbleSlew = 0.03,

	// ---- breath (swept, soft, randomised) ------------------------------
	breath = 0.2,             // level. NOT v1's scale — see header
	breathAtk = 0.03,         // soft swell, not a click
	breathDur = 0.16,
	breathTop = 3.5,          // sweep starts this many x above the note...
	breathLand = 1.0,         // ...and lands here (1 = on the note)
	breathRq = 0.9,           // wide = airy; narrow = whistly
	breathJit = 0.4,          // 0 = identical every note, 1 = wild

	// ---- air / tone ---------------------------------------------------
	air = 0.05,
	airRq = 0.2,
	airDur = 0.1,             // your Env.perc(atk, 0.1) on the air, as a knob
	jitter = 0.15,            // semitones of human pitch instability
	jitterRate = 25,
	bright = 0.6,

	// ---- ambience -----------------------------------------------------
	verb = 0.28, room = 0.62, damp = 0.35, preDelay = 0.022
|
	var swoop, base, vf, dev, wob, jit, f;
	var rAtk, rDur, rLvl, rTop, bCf, puffEnv, puff;
	var tone, airSig, ae, aenv, tremolo, sig, st, wet;

	// ---- the swoop, straight off whipBird -----------------------------
	// hold flat at slideFrom -> sweep up PAST the note -> settle onto it.
	// Gate-triggered, so under Pmono it fires on the first note only, and
	// under PmonoArtic it fires again at the head of every phrase.
	swoop = EnvGen.kr(
		Env(
			[slideFrom, slideFrom, overshoot, 0, slideTo],
			[swoopDelay, slideTime, settleTime, slideOut],
			curve: [\step, \sine, settleCurve, 2],
			releaseNode: 3
		),
		gate
	);

	base = Lag.kr(freq, portTime) * swoop.midiratio;

	vf = Vibrato.kr(base, vibRate, vibDepth, vibDelay, vibOnset,
		vibRateVar, vibDepthVar);

	// Vibrato's own deviation, reused for the amplitude side. Falls to zero
	// when vibDepth is 0, so `trem` self-disables.
	dev = (vf / base) - 1;

	wob = Lag.kr(LFPulse.kr(warbleRate, 0, 0.5), warbleSlew) * warbleDepth;
	jit = LFNoise2.kr(jitterRate) * jitter;

	f = (vf * (wob + jit).midiratio).clip(20, 18000);

	tone = SinOsc.ar(f)
		+ (SinOsc.ar(f * 2, pi/2) * bright * 0.06)
		+ (SinOsc.ar(f * 3) * bright * 0.02);

	// ---- the breath ----------------------------------------------------
	// Four Rands, fixed per synth, all scaled by breathJit so a single knob
	// takes it from dead repeatable to wild.
	rAtk = 1 + (Rand(-1, 1) * breathJit);
	rDur = 1 + (Rand(-1, 1) * breathJit);
	rLvl = 1 + (Rand(-1, 1) * breathJit * 0.8);
	rTop = 1 + (Rand(-1, 1) * breathJit * 0.5);

	// whipBird's swept band, slowed down and aimed at the note: air rushing
	// down onto the pitch rather than a static hiss burst. Pink, not white —
	// white is what made v1 read as a "tsh".
	bCf = XLine.kr(
		(freq * breathTop * rTop).clip(20, 18000),
		(freq * breathLand).clip(20, 18000),
		(breathDur * rDur).max(0.01)
	);
	// ...and the band itself wanders, so it is never a clean sweep.
	bCf = (bCf * LFNoise2.kr(14).range(0.85, 1.18)).clip(20, 18000);

	// Soft swell and a long tail instead of Env.perc's spike.
	puffEnv = EnvGen.kr(
		Env([0, 1, 0.4, 0],
			[(breathAtk * rAtk).max(0.004),
				(breathDur * rDur * 0.5).max(0.01),
				(breathDur * rDur).max(0.01)],
			curve: [\sin, -1, -2]),
		gate
	);

	puff = BPF.ar(PinkNoise.ar, bCf, breathRq)
		* LFNoise2.kr(26).range(0.5, 1.0)   // internal flutter — reads as air
		* puffEnv * breath * rLvl * 6;

	// ---- air (your transient shaping, with airDur as a knob) -----------
	ae = EnvGen.kr(Env.perc(atk, airDur), gate);
	airSig = BPF.ar(WhiteNoise.ar, f, airRq) * air * 3 * ae;

	// ---- amp, with whipBird's independent gliss delay ------------------
	aenv = EnvGen.kr(
		Env([0, 0, 1, sag, 0], [gliss, atk, 0.3, rel],
			curve: [\lin, \sin, -2, \sin], releaseNode: 3),
		gate
	);

	tremolo = 1 + (dev * trem);

	sig = ((tone + airSig) * aenv * tremolo) + puff;
	sig = sig * amp;

	st  = Pan2.ar(sig, pan);
	wet = FreeVerb2.ar(st[0], st[1], 1.0, room, damp);
	wet = DelayN.ar(wet, 0.2, preDelay);
	sig = st + (wet * verb);

	DetectSilence.ar(sig.sum, 0.0001, 0.2, doneAction: 2);
	Out.ar(out, sig);
}).add;
)


// -------------------------------------------------------------------------
// 1. The tune, with the swoop on the front. Pmono, so the swoop fires once
//    and everything after is portamento — compare with 2.
// -------------------------------------------------------------------------
(
Pdef(\whistleTune2,
	Pmono(\birdWhistle2,
		\midinote, Pseq([84, 86, 88, 91, 89, 88, 86, 84, 86, 88, 86, 84], inf),
		\dur,      Pseq([1, 0.5, 0.5, 1.5, 0.5, 0.5, 0.5, 1, 0.5, 0.5, 0.5, 2], inf),
		\amp,      0.2,
		\pan,      Pwhite(-0.15, 0.15),

		\portTime,   Pwhite(0.05, 0.1),
		\slideFrom,  -3,
		\swoopDelay, 0.035,
		\slideTime,  0.09,
		\overshoot,  0.8,
		\settleTime, 0.07,
		\gliss,      0.02,

		\breath,    0.2,
		\breathJit, 0.4,

		\vibDepth, 0.018,
		\vibRate,  Pwhite(5.2, 5.9),
		\air,      0.05,
		\jitter,   0.15,
		\verb,     0.28
	)
).play;
)

Pdef(\whistleTune2).play;
Pdef(\whistleTune2).stop;


// -------------------------------------------------------------------------
// 2. Phrased. PmonoArtic ties while \legato is 1 and starts a FRESH synth
//    when it drops below 1 — so the swoop, the overshoot and a newly
//    randomised breath all fire again at the head of every phrase. This is
//    the one where the whipBird mechanism actually earns its keep.
//
//    all \legato 1     -> one endless slide (i.e. pattern 1)
//    all \legato < 1   -> swoop + breath on every note, no ties
// -------------------------------------------------------------------------
(
Pdef(\whistlePhrases2,
	PmonoArtic(\birdWhistle2,
		//              <--- phrase 1 --->  <-- phrase 2 -->  <-- phrase 3 -->
		\midinote, Pseq([ 84, 86,  88,  91,   89,  88,  86,     84,  86,  84  ] - 20, inf),
		\legato,   Pwhite(0.5,1),// Pseq([  1,  0.5,   1, 0.55,   1,  0.5,    1,  1,   0.5, 1  ], inf),
		\dur,      Pseq([  1, 0.5, 0.5, 1.5,  0.5, 0.5, 1,      0.5, 0.5, 1.5 ] * 0.5, inf),
		//                               ^breath        ^breath           ^breath
		\amp,      0.2,
		\pan,      Pwhite(-0.15, 0.15),

		\portTime,   Pwhite(0.05, 0.1),
		\slideFrom,  Pwhite(-4.0, -2.0),   // each phrase starts from elsewhere
		\slideTime,  Pwhite(0.07, 0.3),
		\overshoot,  Pwhite(0.5, 1.2),
		\settleTime, 0.07,
		// \swoopDelay, Pwhite(0.02, 0.06),
		// \gliss,      Pwhite(0.01, 0.05),

		\swoopDelay, 0.01,//Pwhite(0.01, 0.1),// Varied swoop timing
		\gliss, 0.001,//Pwhite(0.07, 0.1),

		\breath,     Pwhite(0.15, 0.3),
		\breathTop,  Pwhite(2.5, 4.5),
		\breathJit,  0.4,

		\vibDepth, 0.018,
		\vibRate,  Pwhite(5.2, 5.9),
		\air,      0.05,
		\jitter,   0.15,
		\verb,     0.08
	)
).play;
)

Pdef(\whistlePhrases2).play;
Pdef(\whistlePhrases2).stop;


// -------------------------------------------------------------------------
// 3. Warble / trill bench. Past about 2 semitones and 9 Hz it stops being a
//    whistler and becomes a bird.
// -------------------------------------------------------------------------
(
Pbindef(\whistleWarble2,
	\instrument, \birdWhistle2,
	\dur,      3,
	\legato,   0.9,
	\midinote, 88,
	\amp,      0.2,

	\warbleRate,  Pseq([4, 6, 9, 13], inf),
	\warbleDepth, Pseq([0.5, 1, 2, 1], inf),
	\warbleSlew,  Pseq([0.05, 0.03, 0.015, 0.03], inf),

	\vibDepth,  0.01,
	\slideFrom, -2,
	\breath,    0.2,
	\air,       0.05,
	\verb,      0.3
).play;
)

Pbindef(\whistleWarble2).play;
Pbindef(\whistleWarble2).stop;


// -------------------------------------------------------------------------
// Tuning by ear. One knob at a time against a held note.
// -------------------------------------------------------------------------
~w = Synth(\birdWhistle2, [\freq, 1500, \amp, 0.2]);

// --- the swoop (whipBird) — retrigger by re-evaluating the Synth line ---
~w.set(\slideFrom, 0);      ~w.set(\slideFrom, -3);     ~w.set(\slideFrom, -8);
~w.set(\swoopDelay, 0);     ~w.set(\swoopDelay, 0.035); ~w.set(\swoopDelay, 0.15);
~w.set(\slideTime, 0.03);   ~w.set(\slideTime, 0.09);   ~w.set(\slideTime, 0.3);
~w.set(\overshoot, 0);      ~w.set(\overshoot, 0.8);    ~w.set(\overshoot, 3);
~w.set(\settleTime, 0.02);  ~w.set(\settleTime, 0.07);  ~w.set(\settleTime, 0.25);
~w.set(\gliss, 0);          ~w.set(\gliss, 0.02);       ~w.set(\gliss, 0.12);

// --- the breath — breathJit 0 to hear the shape, then wind it back up ---
~w.set(\breath, 0);         ~w.set(\breath, 0.2);       ~w.set(\breath, 0.6);
~w.set(\breathJit, 0);      ~w.set(\breathJit, 0.4);    ~w.set(\breathJit, 0.9);
~w.set(\breathAtk, 0.004);  ~w.set(\breathAtk, 0.03);   ~w.set(\breathAtk, 0.1);
~w.set(\breathDur, 0.06);   ~w.set(\breathDur, 0.16);   ~w.set(\breathDur, 0.5);
~w.set(\breathTop, 1);      ~w.set(\breathTop, 3.5);    ~w.set(\breathTop, 8);
~w.set(\breathLand, 0.5);   ~w.set(\breathLand, 1.0);   ~w.set(\breathLand, 2.0);
~w.set(\breathRq, 0.2);     ~w.set(\breathRq, 0.9);     ~w.set(\breathRq, 2.0);

// --- vibrato ---
~w.set(\vibDepth, 0);       ~w.set(\vibDepth, 0.018);   ~w.set(\vibDepth, 0.04);
~w.set(\vibRate, 4);        ~w.set(\vibRate, 5.5);      ~w.set(\vibRate, 8);
~w.set(\trem, 0);           ~w.set(\trem, 2.5);         ~w.set(\trem, 6);

// --- warble ---
~w.set(\warbleDepth, 0);    ~w.set(\warbleDepth, 1);    ~w.set(\warbleDepth, 3);
~w.set(\warbleSlew, 0.005); ~w.set(\warbleSlew, 0.03);  ~w.set(\warbleSlew, 0.1);

// --- air / tone ---
~w.set(\air, 0);            ~w.set(\air, 0.05);         ~w.set(\air, 0.2);
~w.set(\airDur, 0.02);      ~w.set(\airDur, 0.1);       ~w.set(\airDur, 0.6);
~w.set(\jitter, 0);         ~w.set(\jitter, 0.15);      ~w.set(\jitter, 0.5);
~w.set(\bright, 0);         ~w.set(\bright, 0.6);       ~w.set(\bright, 2);
~w.set(\freq, 900);         ~w.set(\freq, 1500);        ~w.set(\freq, 2600);

// --- ambience ---
~w.set(\verb, 0);           ~w.set(\verb, 0.28);        ~w.set(\verb, 0.7);
~w.set(\room, 0.3);         ~w.set(\room, 0.62);        ~w.set(\room, 0.9);
~w.set(\damp, 0.1);         ~w.set(\damp, 0.35);        ~w.set(\damp, 0.8);
~w.set(\preDelay, 0.005);   ~w.set(\preDelay, 0.022);   ~w.set(\preDelay, 0.08);

s.meter;

~w.set(\gate, 0);
