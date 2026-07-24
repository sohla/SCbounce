/*
blendOsc — 5-voice unison synth with Pulse↔VarSaw blend + Moog LPF
------------------------------------------------------------------
Per-event synth voice suitable for a pattern-driven personality
(engine C style — see code3.0/personality_authoring.md §2). Five
detuned voices, blended between Pulse and VarSaw, through MoogFF
resonant LPF with its own envelope on cutoff.

Controls (all k-rate; safe to sweep via Pdef/Pbind):
  freq        — pitch, Hz
  blend       — 0 = pure Pulse, 1 = pure VarSaw (equal-power via XFade2)
  width       — shared pulse-width + varsaw-width, 0.01..0.99
  detune      — unison spread in semitones (0..0.5 typical)
  cutoff      — filter base cutoff, Hz
  res         — MoogFF resonance, 0..4 (self-oscillates > 3.5)
  envAmt      — filter env peak add, Hz (added to cutoff)
  atk/dec/sus/rel      — amp envelope
  fatk/fdec/fsus/frel  — filter envelope
  amp, pan
*/

// ---------------------------------------------------------------- SYNTHDEF
(
SynthDef(\blendOsc, {
	|out=0, freq=220, gate=1, amp=0.3, pan=0,
	 blend=0.5, width=0.5, detune=0.08,
	 cutoff=600, res=2.2, envAmt=3000,
	 atk=0.01, dec=0.25, sus=0.7, rel=0.5,
	 fatk=0.02, fdec=0.35, fsus=0.35, frel=0.6|

	var ampEnv  = EnvGen.kr(Env.adsr(atk, dec, sus, rel), gate, doneAction: 2);
	var filtEnv = EnvGen.kr(Env.adsr(fatk, fdec, fsus, frel), gate);

	// 5-voice unison — symmetric detune offsets in semitones.
	// Fixed count (SynthDef can't take a variable voice count).
	var offsets       = [-1, -0.5, 0, 0.5, 1] * detune;
	var detunedFreqs  = freq * offsets.midiratio;

	// Both oscillators track the same width. Multichannel-expands to
	// 5 voices via detunedFreqs.
	var pulses = Pulse.ar(detunedFreqs, width, 1);
	var saws   = VarSaw.ar(detunedFreqs, 0, width, 1);

	// Equal-power crossfade between the two oscillator banks.
	// XFade2 accepts pan in [-1..1]; blend*2-1 maps [0..1] onto that.
	var blended = XFade2.ar(pulses, saws, blend * 2 - 1);

	// Sum the 5 voices, small headroom compensation.
	var summed = Mix.ar(blended) * 0.35;

	// Filter env adds to base cutoff; clip to safe MoogFF range.
	var fCut = (cutoff + (filtEnv * envAmt)).clip(20, 18000);
	var filt = MoogFF.ar(summed, fCut, res);

	Out.ar(out, Pan2.ar(filt * ampEnv * amp, pan));
}).add;
)

// ---------------------------------------------------------------- SINGLE-NOTE SMOKE TEST
// Evaluate to hear one note; .set(\gate, 0) to release.
(
x = Synth(\blendOsc, [
	\freq, 110, \amp, 0.3,
	\blend, 0.5, \width, 0.5, \detune, 0.08,
	\cutoff, 400, \res, 2.5, \envAmt, 3500,
	\atk, 0.01, \dec, 0.3, \sus, 0.7, \rel, 0.6,
	\fatk, 0.02, \fdec, 0.4, \fsus, 0.4, \frel, 0.6
]);
)
x.set(\gate, 0);

// ---------------------------------------------------------------- PATTERN 1: melody, fixed voice
// Baseline — hear the voice at typical params. Bass-lead territory.
(
Pdef(\blend_melody,
	Pbind(
		\instrument, \blendOsc,
		\midinote,   Pseq([36, 38, 43, 40, 45, 43, 48, 45], inf),
		\dur,        0.1,
		\amp,        0.8,
		\blend,      0.5,
		\width,      0.5,
		\detune,     0.08,
		\cutoff,     2900,
		\res,        2.98,
		\envAmt,     3000,
		\atk, 0.01, \dec, 0.25, \sus, 0.7, \rel, 0.4,
		\fatk, 0.02, \fdec, 0.35, \fsus, 0.35, \frel, 0.5,
		\pan, Pwhite(-0.3, 0.3)
	)
).play(quant: 0);
)

Pdef(\blend_melody).stop;

// ---------------------------------------------------------------- PATTERN 2: blend sweep
// Sweep \blend 0 → 1 across notes. Hear the timbre morph from Pulse
// (hollow / square) to VarSaw (bright / buzzy). Same pitch/env, only
// oscillator shape changes.
(
Pdef(\blend_sweep,
	Pbind(
		\instrument, \blendOsc,
		\midinote,   48,
		\dur,        0.35,
		\amp,        0.3,
		\blend,      Pseries(0, 0.08, 13),   // 0..1..0 walk
		\width,      0.5,
		\detune,     0.05,
		\cutoff,     900,
		\res,        2.0,
		\envAmt,     2500,
		\atk, 0.01, \dec, 0.2, \sus, 0.7, \rel, 0.3,
		\fatk, 0.02, \fdec, 0.3, \fsus, 0.4, \frel, 0.4
	)
).play(quant: 0);
)

Pdef(\blend_sweep).stop;

// ---------------------------------------------------------------- PATTERN 3: filter sweep
// Fixed pitch, cutoff/envAmt swept per note. Hear the moog character
// come and go. High \res makes each note ping.
(
Pdef(\blend_filter,
	Pbind(
		\instrument, \blendOsc,
		\midinote,   Pseq([40, 40, 47, 40], inf),
		\dur,        0.5,
		\amp,        0.3,
		\blend,      0.3,
		\width,      0.5,
		\detune,     0.06,
		\cutoff,     Pwhite(200, 1200),
		\res,        Pwhite(2.0, 3.4),
		\envAmt,     Pwhite(1500, 5000),
		\atk, 0.005, \dec, 0.1, \sus, 0.3, \rel, 0.4,
		\fatk, 0.005, \fdec, 0.15, \fsus, 0.2, \frel, 0.4
	)
).play(quant: 0);
)

Pdef(\blend_filter).stop;

// ---------------------------------------------------------------- PATTERN 4: detune sweep
// Fixed pitch + params, detune varies per note. Hear the voice go
// from tight (0.01 = subtle chorus) to wide (0.4 = detuned-cluster).
(
Pdef(\blend_detune,
	Pbind(
		\instrument, \blendOsc,
		\midinote,   43,
		\dur,        0.6,
		\amp,        0.3,
		\blend,      0.6,
		\width,      0.5,
		\detune,     Pseries(0.01, 0.04, 10),   // 0.01..0.4..0.01
		\cutoff,     900,
		\res,        2.0,
		\envAmt,     2000,
		\atk, 0.05, \dec, 0.3, \sus, 0.8, \rel, 0.5,
		\fatk, 0.05, \fdec, 0.4, \fsus, 0.5, \frel, 0.5
	)
).play(quant: 0);
)

Pdef(\blend_detune).stop;

// ---------------------------------------------------------------- PATTERN 5: width sweep
// Fixed pitch + blend, width varies. Both Pulse and VarSaw respond to
// width, so the timbre morphs harmonically — width near 0 or 1 = thin,
// width near 0.5 = fullest spectrum.
(
Pdef(\blend_width,
	Pbind(
		\instrument, \blendOsc,
		\midinote,   40,
		\dur,        0.5,
		\amp,        0.3,
		\blend,      0.4,
		\width,      Pseries(0.05, 0.05, 19).clip(0.05, 0.95),
		\detune,     0.05,
		\cutoff,     700,
		\res,        2.2,
		\envAmt,     2500,
		\atk, 0.02, \dec, 0.25, \sus, 0.6, \rel, 0.4,
		\fatk, 0.02, \fdec, 0.3, \fsus, 0.4, \frel, 0.4
	)
).play(quant: 0);
)

Pdef(\blend_width).stop;

// ---------------------------------------------------------------- PATTERN 6: envelope articulation
// Same notes/blend, envelope shape swept — short pluck to long pad.
// Filter env tracks amp env so tonal envelope moves with the note.
(
Pdef(\blend_env,
	Pbind(
		\instrument, \blendOsc,
		\midinote,   Pseq([43, 50, 47, 43], inf),
		\dur,        0.6,
		\amp,        0.3,
		\blend,      0.55,
		\width,      0.5,
		\detune,     0.06,
		\cutoff,     500,
		\res,        2.4,
		\envAmt,     3000,
		\atk,  Pwhite(0.005, 0.4),
		\dec,  Pwhite(0.05, 0.4),
		\sus,  Pwhite(0.2, 0.8),
		\rel,  Pwhite(0.2, 0.9),
		\fatk, Pkey(\atk),
		\fdec, Pkey(\dec),
		\fsus, Pkey(\sus) * 0.7,
		\frel, Pkey(\rel)
	)
).play(quant: 0);
)

Pdef(\blend_env).stop;

// ---------------------------------------------------------------- STOP EVERYTHING
[\blend_melody, \blend_sweep, \blend_filter, \blend_detune, \blend_width, \blend_env]
    .do({|k| Pdef(k).stop });
