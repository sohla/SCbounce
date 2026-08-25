// =========================================================================
// bird_whistle.sc — a human whistle playground.
//
// Target: the Mary Poppins whistle. Worth knowing before you tune anything —
// that whistle is JULIE ANDREWS IMITATING A ROBIN. A professional whistler was
// hired for "A Spoonful of Sugar", didn't catch the mood, and Andrews did it
// herself. So the sound sits between a human whistle and a birdcall, which is
// why personalities/Birdsong.sc was a fair start — but that patch is a
// blackbird firing short percussive chirps, and a whistled *melody* needs
// sustained, legato, scooped notes. Hence this: one gated voice, played
// monophonically, with the five things you asked to hold in your hands —
// attack, slide, vibrato/warble, air, ambience — on their own knobs.
//
// What the research says, and where each finding landed:
//
//   MECHANISM  The mouth is a Helmholtz resonator, not vocal folds. Pitch is
//              changed by embouchure/cavity volume, so there is no formant
//              that tracks pitch and no source-filter model to build.
//   SPECTRUM   "A single, dominant frequency and its overtone harmonics" —
//              harmonics are present but WEAK, and unlike the singing voice
//              they do not group into formants. -> sine + 2nd/3rd at roughly
//              -24 and -34 dB, both on one `bright` knob.
//   REGISTER   Every whistled language in the world works in 1–4 kHz;
//              untrained whistlers span 500 Hz–5 kHz. -> play it MIDI ~84–98.
//   BANDWIDTH  The fundamental emerging from the noise floor is under 500 Hz
//              wide. -> `airRq` around 0.2, i.e. a tight band around f.
//   DYNAMICS   A whistle's amplitude range is REDUCED compared with speech.
//              -> a flat-ish amp envelope with only a slight sag. Resist the
//              urge to make it swell.
//   JITTER     The best realism-per-line in the whole file. A whistle that
//              sounds rock steady actually measures 1130 Hz +/- 10 Hz — about
//              +/- 15 cents — wobbling over ~40 ms. That is `jitter`. Set it
//              to 0 once and listen to how instantly synthetic it goes.
//   VIBRATO    5–6 Hz. Below ~27 cents peak-to-peak listeners stop hearing it
//              as vibrato at all. It is irregular, it RAMPS IN rather than
//              starting with the note, and it shows up as amplitude as well
//              as pitch movement. Core SC's `Vibrato` UGen already has delay,
//              onset, rateVariation and depthVariation, so all of that is
//              free — `trem` couples the amplitude side.
//   WARBLE     Not the same thing as vibrato. Whistler Yuki Takeda defines
//              warbling as a pitch JUMP with a smooth transition between two
//              pitches. Separate knobs, and off by default.
//   ATTACK     There is a puff of air before the tone, and legato onsets
//              scoop up into pitch from below. -> `breath` + `slideFrom`.
//   RELEASE    The "labial stop": narrowing the lips ends a note SOFTLY.
//              -> a gentle `rel`, plus a small downward `slideTo` droop.
//   AMBIENCE   A whistle lives in the band the ear is most sensitive to, so
//              it wants a small BRIGHT space, not a dark hall. -> low `damp`
//              and a short `preDelay`, kept per-note inside the SynthDef.
//
// Core UGens only, no buffers, no samples — evaluate it on a bare booted
// server. This is a bench, not a p-file; nothing here is wired to COTF.
// =========================================================================

(
SynthDef(\birdWhistle, { |
	// ---- note ---------------------------------------------------------
	out = 0, freq = 1500, amp = 0.2, pan = 0, gate = 1,
	portTime = 0.08,          // legato glide between notes under Pmono

	// ---- attack -------------------------------------------------------
	atk = 0.035,              // 25–60 ms is the human range
	rel = 0.14,               // the labial stop: soft, not a cut
	sag = 0.88,               // slight settle after the onset
	breath = 0.3,             // the puff of air before the tone
	breathDur = 0.08,

	// ---- slide (mostly up) --------------------------------------------
	slideFrom = -1.5,         // semitones BELOW the note — the scoop in
	slideTime = 0.06,         // seconds to arrive at pitch
	slideCurve = -3,          // negative = fast then settling, like a whistler
	slideTo = -0.3,           // semitones of droop on release
	slideOut = 0.2,

	// ---- vibrato ------------------------------------------------------
	vibRate = 5.5,            // 5–6 Hz
	vibDepth = 0.018,         // proportion of freq; 0.018 ~= 31 cents
	vibDelay = 0.25,          // vibrato does not start with the note
	vibOnset = 0.35,          // ...it ramps in over this long
	vibRateVar = 0.06,        // irregularity — humans are not LFOs
	vibDepthVar = 0.12,
	trem = 2.5,               // couples amplitude to the vibrato movement

	// ---- warble (pitch jump, NOT vibrato) ------------------------------
	warbleRate = 6,
	warbleDepth = 0,          // semitones of jump; 0 = off
	warbleSlew = 0.03,        // how smooth the jump is

	// ---- air / tone ---------------------------------------------------
	air = 0.05,               // band noise riding with the tone
	airRq = 0.2,
	jitter = 0.15,            // semitones of human pitch instability
	jitterRate = 25,          // ~40 ms wobble
	bright = 0.6,             // weak 2nd/3rd harmonic

	// ---- ambience -----------------------------------------------------
	verb = 0.28, room = 0.62, damp = 0.35, preDelay = 0.022
|
	var scoop, base, vf, dev, wob, jit, f;
	var tone, airSig, puff, aenv, tremolo, sig, st, wet, ae;

	// Scoop in, hold, droop out. Gate-triggered, so under Pmono this fires
	// on the FIRST note only — later notes portamento via portTime instead,
	// which is exactly how a whistled legato line behaves.
	scoop = EnvGen.kr(
		Env([slideFrom, 0, slideTo], [slideTime, slideOut],
			curve: [slideCurve, 2], releaseNode: 1),
		gate
	);

	base = Lag.kr(freq, portTime) * scoop.midiratio;

	// Delayed, ramped, slightly irregular vibrato — all of it from the UGen.
	vf = Vibrato.kr(base, vibRate, vibDepth, vibDelay, vibOnset,
		vibRateVar, vibDepthVar);

	// The vibrato's own deviation, reused to drive the amplitude side.
	// Falls to zero on its own when vibDepth is 0, so `trem` self-disables.
	dev = (vf / base) - 1;

	// Warble: a jump between two pitches, smoothed. Nothing like vibrato.
	wob = Lag.kr(LFPulse.kr(warbleRate, 0, 0.5), warbleSlew) * warbleDepth;

	// The +/- 15 cent human wobble.
	jit = LFNoise2.kr(jitterRate) * jitter;

	f = (vf * (wob + jit).midiratio).clip(20, 18000);

	tone = SinOsc.ar(f)
		+ (SinOsc.ar(f * 2, pi/2) * bright * 0.06)
		+ (SinOsc.ar(f * 3) * bright * 0.02);


	ae = EnvGen.kr(Env.perc(atk,0.1), gate);
	// Air sits in a narrow band around the whistle, not broadband hiss.
	airSig = BPF.ar(WhiteNoise.ar, f, airRq) * air * 3 * ae;

	// The onset puff. Peaks in 4 ms — ahead of the tone's 35 ms attack — so
	// it reads as breath arriving before pitch. Lives outside the amp
	// envelope for that reason.
	puff = HPF.ar(WhiteNoise.ar, (f * 0.6).clip(20, 18000))
		* EnvGen.kr(Env.perc(0.03, breathDur, curve: -4), gate)
		* breath * 0.05;

	// Flat-ish: a whistle has less amplitude range than a voice.
	aenv = EnvGen.kr(
		Env([0, 1, sag, 0], [atk, 0.3, rel],
			curve: [\sin, -2, \sin], releaseNode: 2),
		gate
	);

	tremolo = 1 + (dev * trem);

	sig = ((tone + airSig) * aenv * tremolo) + puff;
	sig = sig * amp;

	// Small bright room, wet path pre-delayed.
	st  = Pan2.ar(sig, pan);
	wet = FreeVerb2.ar(st[0], st[1], 1.0, room, damp);
	wet = DelayN.ar(wet, 0.2, preDelay);
	sig = st + (wet * verb);

	DetectSilence.ar(sig.sum, 0.0001, 0.2, doneAction: 2);
	Out.ar(out, sig);
}).add;
)


// -------------------------------------------------------------------------
// 1. A whistled tune. Pmono is the point — one held voice, so the notes
//    portamento into each other instead of restarting, and the scoop only
//    happens on the way in. That is the "slide" the whole file is about.
//    Sits MIDI 84–91 (~1–1.6 kHz), the comfortable whistling register.
// -------------------------------------------------------------------------
(
Pdef(\whistleTune,
	Pmono(\birdWhistle,
		\midinote, Pseq([84, 86, 88, 91, 89, 88, 86, 84, 86, 88, 86, 84], inf),
		\dur,      Pseq([1, 0.5, 0.5, 1.5, 0.5, 0.5, 0.5, 1, 0.5, 0.5, 0.5, 2], inf),
		\amp,      0.2,
		\pan,      Pwhite(-0.15, 0.15),

		\portTime,  Pwhite(0.05, 0.1),   // slide between notes
		\slideFrom, -1.5,                // scoop into the first note
		\slideTime, 0.07,

		\vibDepth,  0.018,               // held notes bloom into vibrato
		\vibRate,   Pwhite(5.2, 5.9),
		\vibDelay,  0.25,
		\vibOnset,  0.35,

		\air,       0.05,
		\jitter,    0.15,
		\bright,    0.6,
		\verb,      0.28
	)
).play;
)

Pdef(\whistleTune).play;
Pdef(\whistleTune).stop;


// -------------------------------------------------------------------------
// 2. The same line, but breathing. Pmono above holds ONE synth with gate 1
//    forever, so the scoop and the breath puff fire once, on the very first
//    note, and never again — which is why it slides endlessly and never
//    phrases. PmonoArtic is the built-in fix: it keeps the voice tied while
//    \legato is 1, and RELEASES it and starts a fresh synth as soon as
//    \legato drops below 1. Fresh synth, fresh gate — so the attack comes
//    back, and `slideFrom` now scoops only where a phrase begins.
//
//    So \legato is really a per-note "is this the end of a phrase?" flag:
//      all 1s      -> one endless Pmono slide again, as before
//      all below 1 -> re-attacked every note, no slides at all
//    The phrase-final note also takes a long \dur — that gap is the breath.
// -------------------------------------------------------------------------
(
Pdef(\whistlePhrases,
	PmonoArtic(\birdWhistle,
		//              <--- phrase 1 --->  <-- phrase 2 -->  <-- phrase 3 -->
		\midinote, Pseq([ 84, 86,  88,  91,   89,  88,  86,     84,  86,  84  ], inf),
		\legato,   Pseq([  1,  1,   1, 0.55,   1,   1, 0.5,      1,   1, 0.4  ], inf),
		\dur,      Pseq([  1, 0.5, 0.5, 1.5,  0.5, 0.5, 2,      0.5, 0.5, 2.5 ] * 0.25, inf),
		//                               ^breath        ^breath           ^breath
		\amp,      0.2,
		\pan,      Pwhite(-0.15, 0.15),

		\atk, 0.2,

		\portTime,  Pwhite(0.05, 0.1),   // slides inside a phrase
		\slideFrom, -2,                  // scoop — now only at phrase starts
		\slideTime, 0.07,
		\breath,    0.4,                 // ditto the puff of air

		\vibDepth,  0.018,
		\vibRate,   Pwhite(5.2, 5.9),
		\vibDelay,  0.25,
		\vibOnset,  0.35,

		\air,       0.05,
		\jitter,    0.15,
		\bright,    0.6,
		\verb,      0.28
	)
).play;
)

Pdef(\whistlePhrases).play;
Pdef(\whistlePhrases).stop;


// -------------------------------------------------------------------------
// 3. Warble / trill bench. One pitch, held, while the warble walks. These
//    are the two easiest controls to overdo — past about 2 semitones and
//    9 Hz it stops being a whistler and becomes a bird.
//    Set warbleDepth to 0 and push vibDepth instead to hear the difference
//    between a warble (a jump) and a vibrato (a wobble).
// -------------------------------------------------------------------------
(
Pbindef(\whistleWarble,
	\instrument, \birdWhistle,
	\dur,      3,
	\legato,   0.9,
	\midinote, 88,
	\amp,      0.2,

	\warbleRate,  Pseq([4, 6, 9, 13], inf),
	\warbleDepth, Pseq([0.5, 1, 2, 1], inf),
	\warbleSlew,  Pseq([0.05, 0.03, 0.015, 0.03], inf),

	\vibDepth, 0.01,      // a little underneath the warble
	\slideFrom, -2,
	\air,      0.05,
	\verb,     0.3
).play;
)

Pbindef(\whistleWarble).play;
Pbindef(\whistleWarble).stop;


// -------------------------------------------------------------------------
// Tuning by ear. Hold one note and move one knob at a time.
// -------------------------------------------------------------------------
~w = Synth(\birdWhistle, [\freq, 1500, \amp, 0.2]);

// attack
~w.set(\breath, 0);      ~w.set(\breath, 0.3);   ~w.set(\breath, 0.8);
~w.set(\atk, 0.005);     ~w.set(\atk, 0.035);    ~w.set(\atk, 0.12);

// slide — negative slideFrom scoops UP into the note
~w.set(\slideFrom, 0);   ~w.set(\slideFrom, -1.5); ~w.set(\slideFrom, -5);
~w.set(\slideTime, 0.02); ~w.set(\slideTime, 0.07); ~w.set(\slideTime, 0.25);

// vibrato — 0.023 is about 40 cents; under 0.016 it stops reading as vibrato
~w.set(\vibDepth, 0);    ~w.set(\vibDepth, 0.018); ~w.set(\vibDepth, 0.04);
~w.set(\vibRate, 4);     ~w.set(\vibRate, 5.5);    ~w.set(\vibRate, 8);
~w.set(\trem, 0);        ~w.set(\trem, 2.5);       ~w.set(\trem, 6);

// warble
~w.set(\warbleDepth, 0); ~w.set(\warbleDepth, 1);  ~w.set(\warbleDepth, 3);
~w.set(\warbleSlew, 0.005); ~w.set(\warbleSlew, 0.03); ~w.set(\warbleSlew, 0.1);

// air — and the jitter, which is the one that makes or breaks it
~w.set(\air, 0);         ~w.set(\air, 0.05);     ~w.set(\air, 0.2);
~w.set(\airRq, 0.05);    ~w.set(\airRq, 0.2);    ~w.set(\airRq, 0.6);
~w.set(\jitter, 0);      ~w.set(\jitter, 0.15);  ~w.set(\jitter, 0.5);

// tone
~w.set(\bright, 0);      ~w.set(\bright, 0.6);   ~w.set(\bright, 2);
~w.set(\freq, 900);      ~w.set(\freq, 1500);    ~w.set(\freq, 2600);

// ambience
~w.set(\verb, 0);        ~w.set(\verb, 0.28);    ~w.set(\verb, 0.7);
~w.set(\room, 0.3);      ~w.set(\room, 0.62);    ~w.set(\room, 0.9);
~w.set(\damp, 0.1);      ~w.set(\damp, 0.35);    ~w.set(\damp, 0.8);
~w.set(\preDelay, 0.005); ~w.set(\preDelay, 0.022); ~w.set(\preDelay, 0.08);

s.meter;

// release it (soft — labial stop), or free everything
~w.set(\gate, 0);
