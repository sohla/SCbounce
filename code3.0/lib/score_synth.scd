// Audible-sync test SynthDefs. The purpose is *not* composition — it's giving
// the ear something to lock onto so the user can tune ~scoreAnchorBeat against
// the recording by hand. Bass changes on harmonic motion; a chord stab marks
// each downbeat. If the synth lags or leads the recording, nudge anchor by ±1.
//
// Defines:
//   \scoreBass            short tri-wave bass note (perc env).
//   \scoreChord           4-voice saw chord through RLPF (perc env).
//   \scoreVoice           short sine pluck — the running per-subdivision voice
//                         that picks a random pitch from fitted_notes, mirroring
//                         the PyTrackMusic visualizer's audio scheduler.
//   ~playScoreBass.(p, half, amp)
//                         takes a per-bar params dict from scorefile.scd, an
//                         integer half-bar index (0 or 1), and an amp scalar.
//                         Picks the lowest fitted_notes pitch, transposes into
//                         an audible bass register, fires \scoreBass.
//   ~playScoreChord.(p, half, amp, cutoff)
//                         same dict; top fitted_notes pitches as a stab.
//   ~playScoreVoice.(p, half, amp)
//                         one random pitch from fitted_notes[half] as a short
//                         sine pluck. Intended to fire on every subdivision.
//
// SynthDef.add queues client-side and is sent to the server on next boot, so
// these are safe to evaluate before s.waitForBoot.

SynthDef(\scoreBass, { |freq = 110, amp = 0.2|
    var env = EnvGen.kr(Env.perc(0.005, 0.95), doneAction: 2);
	var sig = LFTri.ar(freq) + Saw.ar(freq,0.2) * env * amp;
    Out.ar(0, sig ! 2);
}).add;

SynthDef(\scoreChord, { |amp = 0.2, cutoff = 2000|
    // Pass up to 4 pitches as frequencies. The .kr array control takes the size
    // from the default; pad short chords up to 4 voices at call time.
    var freqs = \freqs.kr([220, 277, 330, 415]);
    var env   = EnvGen.kr(Env.perc(0.005, 0.80), doneAction: 2);
    var sig   = Mix(Saw.ar(freqs)) * (1 / 4);
    sig = RLPF.ar(sig, cutoff.clip(80, 8000), 0.5);
    Out.ar(0, (sig * env * amp) ! 2);
}).add;

SynthDef(\scoreVoice, { |freq = 440, amp = 0.15, pan = 0|
    var env = EnvGen.kr(Env.perc(0.003, 0.15), doneAction: 2);
    var sig = SinOsc.ar(freq) * env * amp;
    Out.ar(0, Pan2.ar(sig, pan));
}).add;

// Pull the lowest pitch from fitted_notes for the given half-bar, transpose
// into a clear bass register, return as a MIDI note number. fitted_notes from
// PyTrackMusic is [[half1_pitches], [half2_pitches]] for 2/4; pitches are
// sorted lowest-first; PyTrackMusic falls back to A4 (= 69) when no notes
// sound in a bar, which is too high for a bass register, so we transpose any
// midi > 60 down two octaves.
~scoreBassMidi = { |p, half|
    var halves = p["fitted_notes"];
    var idx = half.clip(0, halves.size - 1);
    var pitches = halves[idx];
    var midi;
    if (pitches.isNil or: { pitches.size == 0 }) {
        // Defensive fallback — shouldn't trigger because PyTrackMusic always
        // emits at least the chord root, but cheap insurance.
        midi = 45;  // A2
    } {
        midi = pitches[0].asInteger;
        if (midi > 60) { midi = midi - 24 + 24};  // pull into A2-ish range
        midi = midi.clip(24, 60);
    };
    midi;
};

// Build a 4-voice chord from fitted_notes. Naive top-N slicing fails because
// fitted_notes is the *full* orchestral pitch list and is heavily doubled at
// the octave: for V/E in bar 2 it carries multiple Es and Bs but only one G#
// (the third), so any contiguous slice tends to miss the third entirely.
// Instead, dedupe by pitch class to surface every unique chord tone exactly
// once, then octave-fold into a usable register and pad.
//
// `half` semantics:
//   nil        — union of both halves. Use this for bar-level chord voicing so
//                multiple chord hits within a bar voice identically (the second
//                half of a bar is often sparse — e.g. bar 2 V has E B G# in h0
//                but only E in h1 — and would otherwise yield a thin voicing).
//   0 / 1 / ... — that specific half. Use for half-resolution detail.
~scoreChordFreqs = { |p, half|
    var halves = p["fitted_notes"];
    var raw;
    var pitches, seen, unique, voiced;
    if (half.isNil) {
        raw = halves.flatten;
    } {
        raw = halves[half.clip(0, halves.size - 1)];
    };
    if (raw.isNil or: { raw.size == 0 }) {
        voiced = [69, 69, 69, 69];
    } {
        pitches = raw.collect(_.asInteger);
        // Dedupe by pitch class; first occurrence (= lowest octave for ascending
        // input) wins, so we get each chord tone once at its score-lowest position.
        seen = Set.new;
        unique = List.new;
        pitches.do { |m|
            var pc = m % 12;
            if (seen.includes(pc).not) {
                seen.add(pc);
                unique.add(m);
            };
        };
        voiced = unique.asArray.collect({ |m|
            var v = m;
            while { v < 48 } { v = v + 12 };
            while { v > 84 } { v = v - 12 };
            v;
        });
        // Pad to 4 voices by repeating the lowest pitch in higher octaves
        // (still confined to the C3-C6 voicing range above by mod 12).
        while { voiced.size < 4 } {
            voiced = voiced.add(voiced[voiced.size % unique.size]);
        };
        voiced = voiced.copyRange(0, 3);
    };
	voiced.collect({ |m| (m + 12).midicps });
};

~playScoreBass = { |p, half, amp = 0.2|
    Synth(\scoreBass, [
        \freq, ~scoreBassMidi.(p, half).midicps,
        \amp,  amp,
    ]);
};

~playScoreChord = { |p, half, amp = 0.2, cutoff = 2000|
    Synth(\scoreChord, [
        \freqs,  ~scoreChordFreqs.(p, half),
        \amp,    amp,
        \cutoff, cutoff,
    ]);
};

// One random pitch from fitted_notes[half]. The visualizer's audio scheduler
// does the same thing per subdivision (with an optional chord-PC filter we
// skip — the raw fitted_notes already approximate the chord). Pitches are
// played at their score-emitted register; expect a wide range (E1 to E6 in
// busy bars), which is exactly what the visualizer plays.
~playScoreVoice = { |p, half, amp = 0.15, pan = 0|
    var halves = p["fitted_notes"];
    var idx = half.clip(0, halves.size - 1);
    var pitches = halves[idx];
    var midi;
    if (pitches.isNil or: { pitches.size == 0 }) {
        midi = 69;
    } {
        midi = pitches.choose.asInteger;
    };
    Synth(\scoreVoice, [
        \freq, midi.midicps,
        \amp,  amp,
        \pan,  pan,
    ]);
};
