// Audio-Visual Synchronized Patterns
// Standalone demo - includes dependency loading

// Load Visual Server system
(thisProcess.nowExecutingPath.dirname.dirname +/+ "loadVisualServer.scd").load;

// Wait for classes to load, then initialize
(
// Initialize
v = VisualServer.default;
v.start;
s.boot; // Make sure audio server is running

// Create view
~view = v.createView(\audioVisual, Rect(300, 100, 1000, 700), Color.black);

// Simple sine wave synth
SynthDef(\sine, {|freq=440, amp=0.1, dur=1|
    var env = EnvGen.kr(Env.perc(0.01, dur), doneAction: 2);
    var sig = SinOsc.ar(freq) * env * amp;
    Out.ar(0, sig ! 2);
}).add;

// Bass drum synth
SynthDef(\kick, {|freq=60, amp=0.5, dur=0.5|
    var env = EnvGen.kr(Env.perc(0.01, dur), doneAction: 2);
    var sig = SinOsc.ar(freq * env.lincurve(0, 1, 1, 0.2, -4)) * env * amp;
    Out.ar(0, sig ! 2);
}).add;

// Snare synth
SynthDef(\snare, {|amp=0.3, dur=0.2|
    var env = EnvGen.kr(Env.perc(0.01, dur), doneAction: 2);
    var sig = WhiteNoise.ar * env * amp;
    Out.ar(0, sig ! 2);
}).add;
)

// Example 1: Each note creates a visual
(
AVbind(
    // Audio parameters
    \instrument, \sine,
    \freq, Pseq([440, 550, 660, 880].midicps, inf),
    \amp, 0.3,
    \dur, 0.5,
    
    // Visual parameters
    \vinstrument, \pulse,
    \vx, Pkey(\freq).linlin(440, 880, -0.7, 0.7),  // Position follows pitch
    \vy, 0,
    \vstartSize, 10,
    \vendSize, Pkey(\amp) * 200,                     // Size follows amplitude
    \vcolor, Pfunc({|ev| 
        Color.hsv(ev[\freq].cpsmidi / 127, 0.8, 1.0)  // Color follows pitch
    }),
    \vdur, Pkey(\dur) * 2,                           // Visual lasts 2x audio
    \vview, \audioVisual
).play;
)

// Example 2: Drum pattern with explosive visuals
(
var drumPattern = Ppar([
    // Kick drum + big circles
    Pbind(
        \instrument, \kick,
        \freq, 60,
        \amp, 0.6,
        \dur, 1,

        // Big expanding circles
        \type, \audioVisual,
        \vinstrument, \pulse,
        \vx, 0,
        \vy, 0,
        \vstartSize, 20,
        \vendSize, 200,
        \vcolor, Color.red.alpha_(0.8),
        \vdur, 0.8,
        \vcurve, \exp,
        \vview, \audioVisual
    ),
    // Snare + scattered small circles  
    Pbind(
        \instrument, \snare,
        \amp, 0.4,
        \dur, Pseq([Rest(0.5), 0.5], inf),

        \type, \audioVisual,
        \vinstrument, \circle,
        \vx, Pwhite(-0.8, 0.8),
        \vy, Pwhite(-0.8, 0.8),
        \vsize, Pexprand(10, 30),
        \vcolor, Color.white,
        \vdur, 0.3,
        \vview, \audioVisual
    )
]);

drumPattern.play;
)

// Example 3: Melody with trailing visuals
(
Pbind(
    \instrument, \sine,
    \freq, Pseq([
        60, 64, 67, 72, 67, 64,
        60, 64, 67, 71, 67, 64
    ].midicps, inf),
    \amp, Pexprand(0.2, 0.5),
    \dur, 0.25,
    \legato, 0.8
).play;

// Visual trail following the melody
Vbind(
    \instrument, \circle,
    \x, Pseq([
        -0.6, -0.4, -0.2, 0, 0.2, 0.4,
        0.6, 0.4, 0.2, 0, -0.2, -0.4
    ], inf),
    \y, Pfunc({ sin(thisThread.beats * 2) * 0.3 }),  // Sine wave motion
    \size, Pexprand(15, 40),
    \color, Pseq([
        Color.blue, Color.cyan, Color.green, Color.yellow,
        Color.orange, Color.red, Color.magenta, Color.blue
    ], inf),
    \dur, 0.25,
    \view, \audioVisual
).vplay;
)

// Example 4: Responsive system - audio amplitude drives visual size
(
// Global variables for communication
~audioAmp = 0;
~audioPitch = 440;

// Audio pattern that updates globals
Pbind(
    \instrument, \sine,
    \freq, Pexprand(200, 800),
    \amp, Pexprand(0.1, 0.8),
    \dur, Pexprand(0.1, 0.5),
    
    // Update global variables
    \cleanup, Pfunc({|ev|
        ~audioAmp = ev[\amp];
        ~audioPitch = ev[\freq];
    })
).play;

// Visual pattern that reads from globals
Vbind(
    \instrument, \live,
    \x, Pfunc({ (~audioPitch - 500) / 300 }), // Position follows pitch
    \y, 0,
    \size, Pfunc({ ~audioAmp * 100 + 20 }),    // Size follows amplitude
    \color, Pfunc({ 
        Color.hsv(~audioPitch.cpsmidi / 127, 0.8, ~audioAmp + 0.2)
    }),
    \dur, 0.1,
    \view, \audioVisual
).vplay;
)

// Example 5: Phase relationships
(
var tempo = 120;
var clock = TempoClock(tempo/60);

// Slow bass line
Pbind(
    \instrument, \sine,
    \freq, Pseq([40, 45, 50, 55].midicps, inf),
    \amp, 0.4,
    \dur, 2
).play(clock);

// Fast arpeggios
Pbind(
    \instrument, \sine,
    \freq, Pseq([60, 64, 67, 72].midicps, inf),
    \amp, 0.2,
    \dur, 0.25,
    \legato, 0.5
).play(clock);

// Visual bass representation - slow big shapes
Vbind(
    \instrument, \square,
    \x, Pseq([-0.6, -0.2, 0.2, 0.6], inf),
    \y, -0.4,
    \size, 80,
    \color, Pseq([Color.red, Color.green, Color.blue, Color.yellow], inf),
    \dur, 2,
    \view, \audioVisual
).play(clock);

// Visual arpeggio representation - fast small shapes
Vbind(
    \instrument, \circle,
    \x, Pseq([-0.3, -0.1, 0.1, 0.3], inf),
    \y, 0.4,
    \size, 20,
    \color, Pseq([Color.cyan, Color.magenta, Color.yellow, Color.white], inf),
    \dur, 0.25,
    \view, \audioVisual
).vplay(clock);
)

// Stop everything
// s.freeAll;
// v.freeAll;