// ============================================================================
// Audio + Visual BASICS
// ----------------------------------------------------------------------------
// How to combine sound and shapes. The key idea:
//
//   \instrument   = the AUDIO synth   (a SynthDef name, e.g. \default, \sine)
//   \vinstrument  = the VISUAL shape  (a VisualSynthDef, e.g. \circle, \pulse)
//
// AVbind plays BOTH from one pattern. ONE RULE: in AVbind every VISUAL key
// is "v"-prefixed; the audio side keeps the plain SC note keys. (Vbind is
// visual-only, so it uses plain keys - no prefix there.)
//
//   AUDIO (plain)                 VISUAL (v-prefixed, AVbind)
//   ------------------------      ---------------------------------------
//   \instrument  (synth)          \vinstrument  (shape)
//   \freq \amp \dur               \vx \vy \vsize \vcolor \vfill \vdur
//   \legato \sustain (note len)   \vview
//                                 \vstartSize \vendSize \vcurve   (pulse)
//                                 \vx1 \vy1 \vx2 \vy2 \vwidth     (line)
//                                 \vrotation                      (radians; Function = animated spin)
//                                 \vattack \vrelease \vlegato
//                                 \vsustain \vstretch             (envelope)
//                                 \vtimeDelay                     (a/v sync)
//
//   Smart defaults: \vsize ?? amp*100 ; \vendSize ?? amp*200 ;
//                   \vcolor ?? hue(freq) ; \vdur ?? audio \dur
//
// AUDIO/VISUAL SYNC: audio is heard at +s.latency (~0.2 s); AVbind delays
// the visual by \vtimeDelay (default = s.latency) so they line up. Add to it
// for projector lag, e.g. \vtimeDelay, (s.latency + 0.05) ; or \vtimeDelay,0
// to make the visual lead the audio again.
//
// AVbind is played with .play  (NOT .vplay) - it drives audio + visual.
// Event types auto-register at class compile: no manual load needed.
// Evaluate each ( ) block in turn. Cleanup block is at the bottom.
// ============================================================================


// ---- 0. Setup: server, view, a couple of tiny synths -----------------------
(
if (VisualServer.isNil) {
    "Install the Visual Server classes first (see loadVisualServer.scd).".postln;
} {
    s.waitForBoot {
        VisualServer.default.start;
        ~view = VisualServer.default.createView(\demo, Rect(200, 200, 800, 600), Color.black);

        // \default is SuperCollider's built-in synth (always available).
        // Two tiny custom synths so you can set your own \instrument:
        SynthDef(\sine, { |freq = 440, amp = 0.2, dur = 1|
            var env = EnvGen.kr(Env.perc(0.01, dur), doneAction: 2);
            Out.ar(0, SinOsc.ar(freq) * env * amp ! 2);
        }).add;

        SynthDef(\perc, { |freq = 440, amp = 0.3, dur = 0.3|
            var env = EnvGen.kr(Env.perc(0.005, dur), doneAction: 2);
            var sig = SinOsc.ar(freq) + (WhiteNoise.ar * 0.3);
            Out.ar(0, sig * env * amp ! 2);
        }).add;

        "✓ ready - evaluate the examples below".postln;
    };
}
)


// ---- 1. Simplest: built-in synth + a circle --------------------------------
// No \instrument given => AVbind uses \default. Only the SHAPE is chosen.
// \vsize follows \amp and \vcolor follows \freq automatically (smart defaults).
(
~ex1 = AVbind(
    \freq, Pseq([440, 550, 660, 880], inf),
    \amp,  0.2,
    \dur,  0.2,
    \vinstrument, \circle,   // <- the SHAPE (audio synth defaults to \default)
	\vdur, 2.0,
	\vsize, 100,
	\vfill, false,
	\vattack, 0.003,
	\vx, (Pkey(\freq).cpsmidi - 74) / 8,
    // \vtimeDelay defaults to s.latency so the circle hits with the sound.
    // Projector a touch slow? add to it:   \vtimeDelay, (s.latency + 0.05),
    // Want the old visual-leads-audio look?  \vtimeDelay, 0,
    \vview, \demo
).play;
)
~ex1.stop;


// ---- 2. Set synth AND shape; map pitch->x, amp->growth ---------------------
// \instrument \sine = audio, \vinstrument \pulse = visual.
// \vdur 1.2 > audio \dur 0.25, so pulses overlap.
(
~ex2 = AVbind(
    \instrument, \sine,
    \freq, Pseq([220, 330, 440, 550, 660], inf),
    \amp,  Pexprand(0.1, 0.4),
    \dur,  0.25,

    \vinstrument, \pulse,
    \vx,        Pkey(\freq).linlin(220, 660, -0.7, 0.7),     // pitch -> x
    \vy,        0,
    \vstartSize, 5,
    \vendSize,  Pkey(\amp).linlin(0.1, 0.4, 80, 320),        // loudness -> size
    \vcolor,    Pfunc({ |ev| Color.hsv(ev[\freq].linlin(220,660,0,0.9), 0.8, 1) }),
    \vdur,      1.2,
    \vview,     \demo
).play;
)
~ex2.stop;


// ---- 3. Percussion synth + line shape --------------------------------------
// Pitch -> the line's endpoints, amp -> its width.
(
~ex3 = AVbind(
    \instrument, \perc,
    \freq, Prand([220, 440, 660, 880], inf),
    \amp,  Pwhite(0.2, 0.5),
    \dur,  0.09,

    \vinstrument, \line,
    \vx1, Pkey(\freq).linlin(220, 880, -0.8, 0),
    \vy1, Pwhite(-0.5, 0.5),
    \vx2, Pkey(\freq).linlin(220, 880, 0, 0.8),
    \vy2, Pwhite(-0.5, 0.5),
    \vwidth, Pkey(\amp) * 12,
    \vcolor, Color.cyan,
    \vdur, 3.8,
    \vview, \demo
).play;
)
~ex3.stop;


// ---- 4. SHOWCASE: per-note visual envelope (vattack/vrelease/vlegato) -------
// Short staccato notes, but each circle blooms IN, holds, then fades/shrinks
// OUT - and they overlap. The visual envelope is independent of the audio.
//
//   life  = audio \dur * \vlegato         (here 0.15 * 10 = 1.5 s)
//   \vattack + \vrelease are ABSOLUTE seconds carved out of `life`;
//   the leftover (life - attack - release) is the HOLD.
//
//   So they only "show" if  attack + release < life.  (A huge \vrelease
//   just saturates to the whole tail = same as not setting it.)
//   Try \vrelease 0.1  vs  1.2  - the fade-out length visibly changes.
(
~ex4 = AVbind(
    \instrument, \perc,
    \freq, Pseq((48, 50 .. 84).midicps, inf),
    \amp, 0.22,
    \dur, 0.15,                // fast audio (spawn rate)
    \legato, 0.3,              // PLAIN \legato = the AUDIO note (stays short)

    \vinstrument, \circle,
	\vx, Pseg(Pseq([-0.8,0.8], inf), 6, \sine, inf),
    \vy, Pseg(Pseq([-0.8,0.8], inf), 9, \sine, inf),//Pwhite(-0.85, 0.85),
	\vsize, 100,
    \vcolor, Pfunc({ |ev| Color.hsv(ev[\freq].cpsmidi.linlin(48,84,0,1), 0.5, 1.0) }),
	\vlegato, 1,              // life = 0.15 * 10 = 1.5 s  -> ~10 overlap
	\vdur,2,
    \vattack, 0.0025,            // grow + fade IN  over 0.25 s
	\vrelease, 4,            // shrink + fade OUT over 0.6 s  (hold = 0.65 s)
    \vcurve, \sin,
    \vview, \demo
).play;
)
~ex4.stop;


// ---- 5. SHOWCASE: a SPINNING LINE driven by the sound ----------------------
// Rotating \line replaces the old \spinner def. \vrotation (radians) is a
// Function evaluated every frame — pass a time-driven expression to spin.
// Audio amp -> line half-length (via \vx1/\vx2), pitch -> revolutions/sec.
//
// Pkey(\amp)/Pkey(\freq) resolve once per EVENT; capture them via a Pfunc
// closing over the event so the Function passed to \vrotation can read the
// per-event rps. Cleanest is to pre-compute rps with Pkey and bake it into
// a Pfunc that returns a fresh closure each event.
(
~ex5 = AVbind(
    \instrument, \sine,
    \freq, Pexprand(200, 1200),
    \amp,  Pexprand(0.1, 0.4),
    \dur,  0.3,

    \vinstrument, \line,
    \vx1, Pkey(\amp).linlin(0.1, 0.4, -0.04, -0.10) + Pwhite(-0.6, 0.6),
    \vy1, Pwhite(-0.6, 0.6),
    \vx2, Pkey(\vx1) + Pkey(\amp).linlin(0.1, 0.4, 0.08, 0.20),   // length = amp
    \vy2, Pkey(\vy1),
    // per-event spin speed (rev/sec) baked into a fresh closure.
    // Pfunc runs with the event as currentEnvironment, so ~freq is the
    // just-resolved Pexprand value for THIS event. Each event gets a new
    // closure with its own captured rps.
    \vrotation, Pfunc({|e|
        var rps = e.freq.linlin(200, 1200, 0.5, 6);
        { thisThread.seconds * 2pi * rps }
    }),
    \vwidth,  4,
    \vcolor,  Pfunc({ Color.hsv(0.55 + 0.3.rand, 0.8, 1) }),
    \vdur,    1.0,            // life 1.0 s
    \vattack, 0.1,            // spin grows in over 0.1 s
    \vrelease, 0.4,           // and fades out over 0.4 s (hold = 0.5 s)
    \vview,   \demo
).play;
)
~ex5.stop;


// ---- 6. SHOWCASE: outlined shapes, lifetime decoupled from rhythm ----------
// \vfill false  = stroke only.   \vsustain = explicit visual lifetime in
// seconds (ignores \dur).   \vstretch = extra envelope time-scale.
(
~ex6.stop;

~ex6 = AVbind(
    \instrument, \sine,
    \freq, Pseq([60, 67, 64, 72, 69].midicps, inf),
    \amp, 0.18,
    \dur, 0.2,                 // notes every 0.2 s ...

    \vinstrument, \circle,
    \vx, Pseq([-0.6, -0.3, 0, 0.3, 0.6], inf),
	\vy, (Pkey(\freq).cpsmidi - 66) / 12.neg,
    \vsize, 120,
    \vfill, true,             // outline only
    \vcolor, Pseq([Color.red, Color.yellow, Color.green, Color.cyan, Color.magenta], inf),
	// \vsustain, 2.0,            // ... but each square lives 2 s -> ~10 overlap
	// \vstretch, 1.0,
	\vrelease,2,
	\vdur, 1.4,
	\vtimeDelay, 0.1,
    \vattack, 0.001,
    \vview, \demo
).play;
)
~ex6.stop;


// ---- 7. Parallel patterns, aligned -----------------------------------------
// Audio and visuals as SEPARATE patterns on ONE shared clock + same \dur.
// (Audio Pbind uses .play ; visual Vbind uses .vplay ; Vbind keys are PLAIN.)
(
~clock = TempoClock(2);
~ex7audio = Pbind(
    \instrument, \sine,
    \freq, Pseq([60, 64, 67, 72].midicps, inf),
    \amp, 0.2, \dur, 0.5
).play(~clock);

~ex7vis = Vbind(
    \instrument, \square,
    \x, Pseq([-0.6, -0.2, 0.2, 0.6], inf),
    \y, 0, \size, 80,
    \color, Pseq([Color.red, Color.green, Color.blue, Color.yellow], inf),
    \dur, 0.5,                 // same dur + same clock => in step
    \view, \demo
).vplay(~clock);
)
( ~ex7audio.stop; ~ex7vis.stop; )


// ---- 8. Parallel patterns, reactive ----------------------------------------
// Audio pattern writes globals; visual pattern reads them. Minimal form of
// what hybridDemo.scd does.
(
~lastAmp = 0.2; ~lastFreq = 440;
~clock2 = TempoClock(3);

~ex8audio = Pbind(
    \instrument, \sine,
    \freq, Pexprand(200, 900),
    \amp,  Pexprand(0.1, 0.5),
    \dur,  Pexprand(0.35, 0.5),
    \store, Pfunc({ |ev| ~lastAmp = ev[\amp]; ~lastFreq = ev[\freq]; 0 })
).play(~clock2);

~ex8vis = Vbind(
    \instrument, \circle,
    \y, 0,
    \x, Pfunc({ ~lastFreq.linlin(200, 900, -0.8, 0.8) }),
    \size, Pfunc({ ~lastAmp * 250 + 20 }),
    \color, Pfunc({ Color.hsv(~lastFreq.linlin(200,900,0,1), 0.8, 1) }),
    \dur, 0.42,
    \view, \demo
).vplay(~clock2);
)
( ~ex8audio.stop; ~ex8vis.stop; )


// ---- Cleanup ---------------------------------------------------------------
(
[~ex1, ~ex2, ~ex3, ~ex4, ~ex5, ~ex6,
 ~ex7audio, ~ex7vis, ~ex8audio, ~ex8vis].do { |p| p !? { p.stop } };
s.freeAll;
VisualServer.default.freeAll;
"✓ stopped - close the window manually".postln;
)
