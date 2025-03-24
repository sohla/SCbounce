(
var visualEvents = List.new(20);
var defaultEnv = Env.asr(0.01, 1, 1);
var view;
var p;
var controlBus = Bus.control(s, 4);

var updateView = {|v|
    var now = thisThread.seconds;
    var count = 0;

    visualEvents.do { |event|
        var elapsed = 0.0, normTime = 0.0, envVal = 1.0;
        var shape = \circle, pos = 10@10, size = 50.0, col = Color.white;

        if (event[\startTime].notNil) {
            elapsed = now - event[\startTime];
        };

        if (event[\duration].notNil && (event[\duration] != 0)) {
            normTime = elapsed / event[\duration];
        } {
            normTime = 0;
        };

        if (normTime < 1.0) {

            var x = event[\sx].blend(event[\ex], event[\xEnv].at(normTime));
            var y = event[\sy].blend(event[\ey], event[\yEnv].at(normTime));
            shape = event[\shape];
            envVal = event[\envelope].at(normTime);
            pos = x @ y;

            size = event[\startSize].blend(event[\endSize], event[\sizeEnv].at(normTime));
            col = event[\startColor].blend(event[\endColor], event[\colorEnv].at(normTime));
            // col = Color.hsv(event[\startColor].hue.blend(event[\endColor].hue, event[\colorEnv].at(normTime)),1,1);
            col = col.alpha_(event[\alphaEnv].at(normTime));
            count = count + 1;

            Pen.width = max(1, size.squared.lincurve(1, 100000, 1, 20, 0.1));
            Pen.strokeColor = col;
            Pen.fillColor = col;
            Pen.rotate(event[\rotation], pos.x, pos.y);

            switch (event[\shape],
                \circle, {
                    Pen.fillOval(Rect.aboutPoint(pos, size, size));
                },
                \square, {
                    Pen.fillRect(Rect.aboutPoint(pos, size, size));
                },
                \line, {
                    // Pen.width = max(1, size.squared.lincurve(1, 100000, 1, 20, 0.1));
                    Pen.moveTo(pos - (size @ 0));
                    Pen.lineTo(pos + (size @ 0));
                    Pen.stroke;
                },
                \triangle, { // Equilateral triangle
                    var height = size * sqrt(3) / 2;
                    var points = [
                        pos + (0 @ (height.neg / 1)), // Top point
                        pos + ((size.neg / 1) @ (height / 2)), // Bottom-left
                        pos + ((size / 1) @ (height / 2)) // Bottom-right
                    ];
                    Pen.moveTo(points[0]);
                    Pen.lineTo(points[1]);
                    Pen.lineTo(points[2]);
                    Pen.lineTo(points[0]); // Close the triangle
                    Pen.fill;
                },
                \star, { // 5-pointed star
                    var innerRatio=0.5, rotation=0;
                    var points = Array.fill(10, { |i|
                        var angle = (i * pi / 5) + rotation; // Alternate between inner and outer points
                        var radius = if(i % 2 == 0, { size }, { size * innerRatio });

                        // Return a Point with x @ y coordinates
                        pos + (radius * cos(angle) @ (radius * sin(angle)))
                    });

                    // Move to first point
                    Pen.moveTo(points[0]);

                    // Draw lines to all other points
                    points.do { |point, i|
                        if (i > 0) { Pen.lineTo(point) };
                    };

                    // Close the path by connecting back to the first point
                    Pen.lineTo(points[0]);
                    Pen.stroke;
                },
                \hexagon, { // Regular hexagon

                    // Create array of points for a regular hexagon
                    var points = Array.fill(6, { |i|
                        var angle = (i * (2pi / 6)) + 0;

                        // Return a Point with x @ y coordinates
                        pos + ((size * cos(angle)) @ (size * sin(angle)))
                    });

                    // Start at the first vertex, not at the center
                    Pen.moveTo(points[0]);

                    // Draw lines to all other vertices
                    points[1..].do { |point|
                        Pen.lineTo(point);
                    };

                    // Close the path by connecting back to the first point
                    Pen.lineTo(points[0]);
                    Pen.stroke;

                },
                \cross, { // Cross shape

                    // If thickness is not specified, default to size/3
                    var thickness = thickness ? (size / 3);
                    var halfThick = thickness / 2;

                    // Calculate the eight points of the cross (clockwise from top-left of vertical bar)
                    var points = [
                        // Points for vertical bar (top to bottom)
                        Point(pos.x - halfThick, pos.y - size),
                        Point(pos.x + halfThick, pos.y - size),
                        Point(pos.x + halfThick, pos.y - halfThick),

                        // Points for horizontal bar (right side)
                        Point(pos.x + size, pos.y - halfThick),
                        Point(pos.x + size, pos.y + halfThick),

                        // Bottom of vertical bar
                        Point(pos.x + halfThick, pos.y + halfThick),
                        Point(pos.x + halfThick, pos.y + size),
                        Point(pos.x - halfThick, pos.y + size),
                        Point(pos.x - halfThick, pos.y + halfThick),

                        // Left side of horizontal bar
                        Point(pos.x - size, pos.y + halfThick),
                        Point(pos.x - size, pos.y - halfThick),
                        Point(pos.x - halfThick, pos.y - halfThick)
                    ];

                    // Draw the cross
                    Pen.moveTo(points[0]);
                    points[1..].do { |point|
                        Pen.lineTo(point);
                    };
                    // Close the path
                    Pen.lineTo(points[0]);
                    Pen.fill;
                },
                \wave, { // Sine wave
                    var points = Array.fill(100, { |i|
                        var x = pos.x + (i / 100 * size * 2) - size; // Map x across the size
                        var y = pos.y + (sin(i / 100 * 2pi) * (size / 2));
                        x @ y
                    });
                    Pen.moveTo(points[0]);
                    points.do { |point, i|
                        if (i > 0) { Pen.lineTo(point) };
                    };
                    Pen.stroke;
                },
                \leaf,{
                    var numPoints = 10;
                    var width = width ? (size / 3);

                    var points = Array.fill(numPoints * 2 + 1, { |i|
                        var t;
                        var x, y;

                        if (i <= numPoints) {
                            // First half - going from base to tip along right edge
                            t = i / numPoints;
                            x = pos.x + (size * t);
                            // Create curved edge - peak in the middle, tapering at ends
                            y = pos.y + (width * sin(t * pi) * (0.5 + (sin(t * pi * 0.2) * 0.5)));
                        } {
                            // Second half - coming back from tip to base along left edge
                            t = (numPoints * 2 - i) / numPoints;
                            x = pos.x + (size * t);
                            // Mirror the right edge, but with slight asymmetry
                            y = pos.y - (width * sin(t * pi) * (0.4 + (sin(t * pi * 0.2) * 0.5)));
                        };

                        Point(x, y);
                    });

                    // Draw the leaf outline
                    Pen.moveTo(points[0]);
                    points[1..].do { |point|
                        Pen.lineTo(point);
                    };
                    Pen.stroke;
                },

                \spiral, {
                    var maxRadius = size, turns=3, startRadius=0;
                    var numPoints = 100 * turns; // More points for smoother spiral
                    var points = Array.fill(numPoints, { |i|
                        var progress = i / (numPoints - 1); // 0 to 1
                        var angle = progress * turns * 2pi; // Angle increases with each point
                        var radius = startRadius + ((maxRadius - startRadius) * progress); // Radius increases linearly

                        // Convert polar coordinates to Cartesian
                        var x = pos.x + (radius * cos(angle));
                        var y = pos.y + (radius * sin(angle));

                        x @ y
                    });

                    // Draw the spiral
                    Pen.moveTo(points[0]);
                    points[1..].do { |point|
                        Pen.lineTo(point);
                    };
                    Pen.stroke;
                }
            );
            Pen.rotate(event[\rotation].neg, pos.x, pos.y);
        };
    };

    visualEvents = visualEvents.select { |event|
        var now = thisThread.seconds;
        var elapsed = now - event[\startTime];
        var duration = event[\duration] ? 1;
        elapsed < duration;
    };
};

var makeView = {
    view = UserView()
        .background_(Color.black)
        .animate_(true)
        .frameRate_(60)
        .drawFunc_(updateView)
};

var window = Window("Visual Synthesizer", Rect(1000, 100, 1200*2, 800*2))
    .fullScreen
    .front
    .background_(Color.white().alpha_(1))
    .layout_(GridLayout.rows(
        [makeView.(), makeView.()],
        [makeView.(), makeView.()],
    ).margins_(0).hSpacing_(1).vSpacing_(1));

var addVisual = { |shape, sx, sy, ex, ey, xEnv, yEnv, startSize, endSize, sizeEnv, startColor, endColor, colorEnv, rotation, duration, envelope, alphaEnv|
    var event;

    shape = shape ? \circle;
    sx = sx ? 10;
    sy = sy ? 10;
    ex = ex ? 200;
    ey = ey ? 200;
    xEnv = xEnv ? defaultEnv;
    yEnv = yEnv ? defaultEnv;
    startSize = startSize ? 50;
    endSize = endSize ? 100;
    sizeEnv = sizeEnv ? defaultEnv;
    startColor = startColor ? Color.white;
    endColor = endColor ? Color.red;
    colorEnv = colorEnv ? defaultEnv;
    rotation = rotation ? 0;
    duration = duration ? 5;
    envelope = envelope ? defaultEnv;
    alphaEnv = alphaEnv ? Env([0, 1, 0], [0.0, 1], \sin);

    event = (
        \shape: shape,
        \sx: sx,
        \sy: sy,
        \ex: ex,
        \ey: ey,
        \xEnv: xEnv,
        \yEnv: yEnv,
        \startSize: startSize,
        \endSize: endSize,
        \sizeEnv: sizeEnv,
        \startColor: startColor,
        \endColor: endColor,
        \colorEnv: colorEnv,
        \rotation: rotation,
        \duration: duration,
        \envelope: envelope,
        \alphaEnv: alphaEnv,
        \startTime: thisThread.seconds
    );

    visualEvents = visualEvents.add(event);
};


var envLibrary = (
    linear: { Env([0, 1], [1], \linear) },
    spring: { Env([0, 1.2, 0.8, 1.1, 0.9, 1.03, 0.97, 1], [0.3, 0.1, 0.1, 0.1, 0.1, 0.1, 0.2], [\sine, \sine, \sine, \sine, \sine, \sine, \sine]) },
    bounce: { Env([0, 1.1, 0.9, 1.03, 0.97, 1], [0.1, 0.1, 0.1, 0.1, 0.2], [\sine, \sine, \sine, \sine, \sine]) },
    overshoot: { Env([0, -0.1, 1], [0.2, 0.8], [\sine, 3]) },
    anticipate: { Env([0, -0.2, 1], [0.3, 0.7], [\sine, \sin]) },
    step: { Env([0, 0.2, 0.2, 0.4, 0.4, 0.6, 0.6, 0.8, 0.8, 1], [0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1], [\step, \step, \step, \step, \step, \step, \step, \step, \step]) },

    firstQuarter: {
        Env(
            [0, 1],
            [0.2],
            \sine
        )
    },
    secondQuarter: {
        Env(
            [1, 0],
            [1],
            \sine
        )
    },
    circle: {
        var sig = Signal.newClear(100);
        sig.waveFill({ |x|
            1 - sqrt(1 - x.squared)
        }, 0, 1);
        Env.new(sig, 0.01)
        },
    ripple: {
        var sig = Signal.newClear(100);
        sig.waveFill({ |x|
            var freq = 7; // frequency of oscillation
            var decay = 3; // rate of decay
            0.5 + (0.5 * sin(freq * x * pi) * exp(decay.neg * x))
        }, 0, 1);
        Env.new(sig, 0.01)
    },
    heartBeat: {
        var sig = Signal.newClear(100);
        sig.waveFill({ |x|
            var pulse1 = exp(-200 * (x - 0.2).squared) * 0.8;
            var pulse2 = exp(-200 * (x - 0.4).squared) * 0.8;
            var baseline = 0.2;
            baseline + pulse1 + pulse2
        }, 0, 1);
        Env.new(sig, 0.01)
    },
    wobble: {
        var sig = Signal.newClear(100);
        sig.waveFill({ |x|
            var cycles = 5; // number of growth cycles
            var baseGrowth = x; // underlying trend
            var seasonalEffect = 0.05 * sin(cycles * 2pi * x); // seasonal variation
            baseGrowth + seasonalEffect
        }, 0, 1);
        Env.new(sig, 0.01)
    },
    gust: {
        var sig = Signal.newClear(100);
        sig.waveFill({ |x|
            var baseWind = 0.2 + (0.3 * x); // gradually increasing base wind
            var gust = 0.5 * exp(-30 * (x - 0.7).squared); // sudden gust
            baseWind + gust
        }, 0, 1);
        Env.new(sig, 0.01)
    },
    butterfly: {
        var sig = Signal.newClear(100);
        sig.waveFill({ |x|
            var flutterFreq = 20;
            var hoverPoint1 = exp(-50 * (x - 0.3).squared);
            var hoverPoint2 = exp(-50 * (x - 0.7).squared);
            var flutter = 0.1 * sin(flutterFreq * pi * x);
            (0.5 * x) + (0.5 * (x.sqrt)) + flutter - (0.2 * hoverPoint1) - (0.2 * hoverPoint2)
        }, 1, 0);
        Env.new(sig, 0.01)
    },
    earthquake: {
        var sig = Signal.newClear(100);
        sig.waveFill({ |x|
            var pWave = 0.3 * exp(-30 * (x - 0.2).squared) * sin(40 * pi * x);
            var sWave = 0.7 * exp(-15 * (x - 0.5).squared) * sin(20 * pi * x);
            0.5 + pWave + sWave
        }, 0, 1);
        Env.new(sig, 0.01)
    }

);

// Function to retrieve a custom envelope by name
var ce = { |name|
    envLibrary[name].value
};

Event.addEventType(\customEvent, {
    addVisual.(
        shape: ~shape ? \circle,
        sx: ~sx ? 10,
        sy: ~sy ? 10,
        ex: ~ex ? 200,
        ey: ~ey ? 200,
        xEnv: ~xEnv ? defaultEnv,
        yEnv: ~yEnv ? defaultEnv,
        startSize: ~startSize ? 50,
        endSize: ~endSize ? 100,
        sizeEnv: ~sizeEnv ? defaultEnv,
        startColor: ~startColor ? Color.white,
        endColor: ~endColor ? Color.red,
        colorEnv: ~colorEnv ? defaultEnv,
        rotation: ~rotation ? 0,
        duration: ~duration ? 5,
        envelope: ~envelope ? defaultEnv,
        alphaEnv: ~alphaEnv ? Env([0, 1, 0.4, 0], [0.01, 0.09, 0.9], \sin)
    );

    ~type = \note;
    currentEnvironment.play;
});

CmdPeriod.doOnce({
    window.close;
    controlBus.free;
});


SynthDef(\versatilePerc, {
    |out=0, freq=50, tension=0.01, decay=0.5, clickLevel=0.5, amp=0.5, dist = 3, pan = 0|
    var pitch_contour, drum_osc, click_osc, drum_env, click_env, sig, pch;

    pitch_contour = Line.kr(1, 0, 0.02);
	pch = freq * (0.992 + (pitch_contour * tension));
	drum_osc = SinOsc.ar([pch,pch*1.004], LFNoise2.ar([7,8],4,-4),0.5);
    click_osc = LPF.ar(WhiteNoise.ar(1), 1500);
    drum_env = EnvGen.ar(
        Env.perc(attackTime: 0.005, releaseTime: decay, curve: -4),
        doneAction: 2
    );
    click_env = EnvGen.ar(
        Env.perc(attackTime: 0.001, releaseTime: 0.01),
        levelScale: clickLevel
    );
	sig = (drum_osc * drum_env) + (click_osc * click_env);
	sig = (sig * dist).tanh.distort;
	Out.ar(out, Pan2.ar(sig[0],pan,amp))
}).add;

SynthDef(\woodBamboo, {
    |out=0, freq=1000, ringTime=0.1, ringMix=0.5, noiseMix=0.5, amp=0.5|
    var exciter, resonator, noiseSig, output;
    var freqs, amps, times;
    ~makeBambooArrays = { |freq = 440|
        var freqs, amps, times;
        freqs = freq * [1.0, 3.93, 4.97, 6.23, 9.11];
        amps = [1.0, 0.5, 0.35, 0.18, 0.12];
        times = [1.8, 0.9, 0.7, 0.4, 0.2] * ringTime;
        [freqs, amps, times]
    };

    #freq, amps, times = ~makeBambooArrays.(freq);
    exciter = Impulse.ar(0);
    resonator = Klank.ar(
        `[freq, amps, times],
        exciter
    );
    noiseSig = LPF.ar(PinkNoise.ar,9400) * EnvGen.ar(Env.perc(0.01, 0.01));
    output = (resonator * ringMix) + (noiseSig * noiseMix);
    output = output * EnvGen.ar(Env.perc(0.01, ringTime * 2), doneAction: 2);
    Out.ar(out, Pan2.ar(output, 0, amp));
}).add;

// 1. FM Bell Synth - metallic percussive sounds with frequency modulation
SynthDef(\fmBell, {
    |out=0, freq=440, modIndex=3, modRatio=2.2, amp=0.5, att=0.01, rel=2, pan=0|
    var carrier, modulator, env;
    modulator = SinOsc.ar(freq * modRatio) * freq * modIndex;
    carrier = SinOsc.ar(freq + modulator);
    env = EnvGen.ar(Env.perc(att, rel, curve: -4), doneAction: 2);
    Out.ar(out, Pan2.ar(carrier * env * amp, pan));
}).add;

// 2. Ambient Pad - atmospheric sustained sounds with chorus effect
SynthDef(\ambientPad, {
    |out=0, freq=440, amp=0.3, att=0.5, decay=0.5, sus=1.0, rel=2.0, cutoff=1200, rq=0.5, pan=0, lfoRate=4, lfoDepth=0.03|
    var sig, env, chorus;
    env = EnvGen.ar(Env.adsr(att, decay, sus, rel), doneAction: 2);
    sig = Mix.ar([
        Saw.ar(freq * LFNoise2.kr(0.1, 0.01, 1)),
        Saw.ar(freq * 0.99 * LFNoise2.kr(0.13, 0.01, 1)),
        Saw.ar(freq * 1.01 * LFNoise2.kr(0.17, 0.01, 1))
    ]);
    // Gentle chorus effect
    chorus = DelayC.ar(sig, 0.05, SinOsc.kr(lfoRate, [0, 2pi]).range(0, lfoDepth) + 0.005);
    sig = Mix.ar([sig, chorus]) * 0.5;
    sig = RLPF.ar(sig, cutoff * env.linexp(0, 1, 0.6, 1), rq);
    Out.ar(out, Pan2.ar(sig * env * amp, pan));
}).add;
// 1. FM Bell Synth - metallic percussive sounds with frequency modulation
SynthDef(\fmBell, {
    |out=0, freq=440, modIndex=3, modRatio=2.2, amp=0.5, att=0.01, rel=2, pan=0|
    var carrier, modulator, env;
    modulator = SinOsc.ar(freq * modRatio) * freq * modIndex;
    carrier = SinOsc.ar(freq + modulator);
    env = EnvGen.ar(Env.perc(att, rel, curve: -4), doneAction: 2);
    Out.ar(out, Pan2.ar(carrier * env * amp, pan));
}).add;

// 2. Ambient Pad - atmospheric sustained sounds with chorus effect
SynthDef(\ambientPad, {
    |out=0, freq=440, amp=0.3, att=0.5, decay=0.5, sus=1.0, rel=2.0, cutoff=1200, rq=0.5, pan=0, lfoRate=4, lfoDepth=0.03|
    var sig, env, chorus;
    env = EnvGen.ar(Env.adsr(att, decay, sus, rel), doneAction: 2);
    sig = Mix.ar([
        Saw.ar(freq * LFNoise2.kr(0.1, 0.01, 1)),
        Saw.ar(freq * 0.99 * LFNoise2.kr(0.13, 0.01, 1)),
        Saw.ar(freq * 1.01 * LFNoise2.kr(0.17, 0.01, 1))
    ]);
    // Gentle chorus effect
    chorus = DelayC.ar(sig, 0.05, SinOsc.kr(lfoRate, [0, 2pi]).range(0, lfoDepth) + 0.005);
    sig = Mix.ar([sig, chorus]) * 0.5;
    sig = RLPF.ar(sig, cutoff * env.linexp(0, 1, 0.6, 1), rq);
    Out.ar(out, Pan2.ar(sig * env * amp, pan));
}).add;



// 3. Glitch Percussion - digital, glitchy percussion sounds without Decimator
SynthDef(\glitchPerc, {
    |out=0, freq=300, amp=0.5, density=20, decay=0.5, pan=0, ffreq=2000|
    var sig, env, dust, clickyEnv, noiseAmt;
    // Use a mix of noise bursts and oscillators for glitchy texture
    dust = Dust2.ar(density);
    env = EnvGen.ar(Env.perc(0.001, decay), doneAction: 2);
    // Create glitchy effect with sample and hold noise, ring modulation
    clickyEnv = EnvGen.ar(Env.perc(0.0005, 0.01, curve: 0), dust);
    noiseAmt = LFNoise0.kr(10).range(0.1, 0.9);

    sig = Mix([
        // Ring modulation for metallic tones
        SinOsc.ar(freq) * SinOsc.ar(LFNoise0.kr(8).exprange(freq * 0.8, freq * 8)),
        // Sample & hold noise for digital artifacts
        LFNoise0.ar(LFNoise0.kr(4).exprange(100, 8000)) * noiseAmt,
        // Clicky transients
        WhiteNoise.ar(0.2) * clickyEnv
    ]);

    // Chaotic filter behavior
    sig = RLPF.ar(sig,
        LFNoise1.kr(5).exprange(ffreq * 0.5, ffreq * 2) * env.linexp(0, 1, 0.5, 1),
        LFNoise1.kr(2).range(0.05, 0.5)
    );

    sig = (sig * 3).clip(-0.8, 0.8); // Clip for distortion
    sig = sig * env * amp;
    Out.ar(out, Pan2.ar(sig, pan));
}).add;

// 4. Plucked String - physical modeling-inspired string plucks
SynthDef(\pluck, {
    |out=0, freq=440, amp=0.5, decay=1, coef=0.1, pan=0|
    var sig, env;
    sig = Pluck.ar(
        WhiteNoise.ar(0.1),
        1,
        freq.reciprocal,
        freq.reciprocal,
        decay,
        coef
    );
    env = EnvGen.ar(Env.perc(0.001, decay), doneAction: 2);
    sig = sig * env * amp;
    Out.ar(out, Pan2.ar(sig, pan));
}).add;

// 5. Resonant Sweep - filter sweeps with resonance
SynthDef(\resonantSweep, {
    |out=0, freq=440, amp=0.4, att=0.01, sus=0, rel=1, ffreq=1000, startRq=1.0, endRq=0.01, pan=0|
    var sig, env, filterEnv;
    env = EnvGen.ar(Env.linen(att, sus, rel), doneAction: 2);
    filterEnv = XLine.kr(startRq, endRq, rel);
    sig = Mix.ar([
        LFSaw.ar(freq),
        LFSaw.ar(freq * 1.01),
        LFPulse.ar(freq * 0.5, 0, 0.5)
    ]) * 0.3;
    sig = RLPF.ar(sig, ffreq, filterEnv);
    sig = sig * env * amp;
    Out.ar(out, Pan2.ar(sig, pan));
}).add;


// 4. Plucked String - physical modeling-inspired string plucks
SynthDef(\pluck, {
    |out=0, freq=440, amp=0.5, decay=1, coef=0.1, pan=0|
    var sig, env;
    sig = Pluck.ar(
        WhiteNoise.ar(0.1),
        1,
        freq.reciprocal,
        freq.reciprocal,
        decay,
        coef
    );
    env = EnvGen.ar(Env.perc(0.001, decay), doneAction: 2);
    sig = sig * env * amp;
    Out.ar(out, Pan2.ar(sig, pan));
}).add;

// 5. Resonant Sweep - filter sweeps with resonance
SynthDef(\resonantSweep, {
    |out=0, freq=440, amp=0.4, att=0.01, sus=0, rel=1, ffreq=1000, startRq=1.0, endRq=0.01, pan=0|
    var sig, env, filterEnv;
    env = EnvGen.ar(Env.linen(att, sus, rel), doneAction: 2);
    filterEnv = XLine.kr(startRq, endRq, rel);
    sig = Mix.ar([
        LFSaw.ar(freq),
        LFSaw.ar(freq * 1.01),
        LFPulse.ar(freq * 0.5, 0, 0.5)
    ]) * 0.3;
    sig = RLPF.ar(sig, ffreq, filterEnv);
    sig = sig * env * amp;
    Out.ar(out, Pan2.ar(sig, pan));
}).add;

SynthDef(\mouseX, { |bus| Out.kr(bus, MouseX.kr(0,1.0))}).add;
SynthDef(\mouseY, { |bus| Out.kr(bus, MouseY.kr(0,1.0))}).add;

s.waitForBoot({

	var mx = Bus.control(s,1);
	var my = Bus.control(s,1);
	Synth(\mouseX, [\bus, mx.index]);
	Synth(\mouseY, [\bus, my.index]);

    Pbindef(\pa,
        \instrument, \woodBamboo,
        \octave, Pseq([3,4,5].stutter(4), inf),
		\note, Pseq([0,4,7,11], inf),
        \root, Pseq([0,3,-2,7,-4,2].stutter(4*3), inf),
        \ringTime, 2,
        \ii, Pseq([0,1,2,3,4,5,6,7,8].stutter(12), inf),
        \midiNote, Pfunc{|e| ((e.octave * 12) + (e.note) + (e.root))}, //make ourselves
        \type, \customEvent,
        \shape, Pindex([\triangle,\line,\spiral,\star,\wave,\square,\circle,\cross,\hexagon],0, inf),
        \sx, 11 + Pkey(\root) * 30,
        \sy, 80 - Pkey(\midiNote) * 10,
        \ex, Pkey(\sx) - 130,
        \ey, 75 - Pkey(\midiNote) * 10,
        \xEnv, Pfunc { ce.(\firstQuarter) },
        \yEnv, Pfunc { ce.(\linear) },
        \startSize, 130,
        \endSize, 0,
        \sizeEnv, Pfunc { ce.(\linear) },
        \hue, Pseg(Pseq([0.0,0.79999], inf), 4, \linear, inf),
        \startColor,  Pfunc({|e|Color.hsv(e.hue,1,1)}),
        \endColor,  Pfunc({|e|Color.hsv(e.hue + 0.2,1,0)}),
        \colorEnv, Pfunc { Env([0, 1], [1], \linear) },
        \rotation, Pseg(Pseq([-pi/2, pi/2], inf), 2, \linear, inf),
        // \dur, Pxrand([0.125 * 1], inf),
        \dur, 0.125,
		\duration, 2,

    ).play(quant: 0.0);

    Pbindef(\pb,
        \instrument, \versatilePerc,
        \octave, Pxrand([2,3].stutter(7), inf),
		\note, Pseq([0,12,7], inf),
        \root, Pseq([0,3,-2,7,-4,2].stutter(4), inf),
        \amp,0.4,
        \noiseMix, 0.1,
        \decay, Pwhite(0.1, 1.0, inf),
        \midiNote, Pfunc{|e| ((e.octave * 12) + (e.note) + (e.root))}, //make ourselves
        \type, \customEvent,
        \shape, Pseq([\leaf], inf),
        \sx, 390,
        \sy, 60 - Pkey(\midiNote) * 10,
        \ex, 400,
        \ey, 200,
        \xEnv, Pfunc { ce.(\ripple) },
        \yEnv, Pfunc { ce.(\linear) },
        \startSize, 230,
        \endSize, 23,
        \sizeEnv, Pfunc { ce.(\firstQuarter) },
        \hue, Pseg(Pseq([0.0,0.49999], inf), 4, \linear, inf),
        \startColor,  Pfunc({|e|Color.hsv(e.hue,1,1)}),
        \endColor,  Pfunc({|e|Color.hsv(e.hue + 0.5,1,0)}),
        \colorEnv, Pfunc { Env([0, 1], [1], \linear) },
        \rotation, Pseg(Pseq([-pi,-pi], inf), 4, \linear, inf),
        \dur, Pseq([0.125 * 4, 0.125 * 2], inf),
		\duration, 7,

    ).play(quant: 0.0);

    Pbindef(\pa,
        \shape, Pindex([\triangle,\line,\spiral,\star,\wave,\circle,\cross,\hexagon],Pkey(\ii), inf)

        );




// Pbindef 1: Orbital Patterns with FM Bell synth
	// Pbindef(\orbital,
	// 	\instrument, \fmBell,
	// 	\octave, Prand([4, 5, 6], inf),
	// 	\degree, Pseq([0, 2, 4, 7, 9], inf),
	// 	\scale, Scale.minor,
	// 	\root, Pstutter(8, Pseq([0, 5, 3, -2], inf)),
	// 	\modIndex, Pseg(Pseq([1, 5], inf), 8, \exp, inf),
	// 	\modRatio, Pwhite(1.5, 2.5, inf),
	// 	\amp, 0.25,
	// 	\pan, Pseg(Pseq([-0.8, 0.8], inf), 16, \sine, inf),
	// 	\att, 0.01,
	// 	\rel, Pwhite(0.5, 2.0, inf),
	// 	// Visual parameters
	// 	\type, \customEvent,
	// 	\shape, \circle,
	// 	\sx, Pfunc({ 300 + (100 * sin(thisThread.seconds * 0.5)) }),
	// 	\sy, Pfunc({ 200 + (100 * cos(thisThread.seconds * 0.3)) }),
	// 	\ex, Pkey(\sx) + Pwhite(-50, 50, inf),
	// 	\ey, Pkey(\sy) + Pwhite(-50, 50, inf),
	// 	\xEnv, Pfunc { ce.(\ripple) },
	// 	\yEnv, Pfunc { ce.(\circle) },
	// 	\startSize, Pkey(\rel) * 10,
	// 	\endSize, 2,
	// 	\sizeEnv, Pfunc { ce.(\linear) },
	// 	\startColor, Pfunc({ |e| Color.hsv((e.degree/12) % 1, 0.8, 1) }),
	// 	\endColor, Pfunc({ |e| Color.hsv(((e.degree+3)/12) % 1, 1, 0.5) }),
	// 	\colorEnv, Pfunc { ce.(\butterfly) },
	// 	\rotation, Pkey(\modIndex) * pi/4,
	// 	\dur, Pswitch([
	// 		Pseq([0.125, 0.125, 0.25], 1),
	// 		Pseq([0.25, 0.25], 1),
	// 		Pseq([0.125, 0.125, 0.125, 0.125], 1)
	// 	], Pwhite(0, 2, inf)) * 0.5,
	// 	\duration, 30
	// ).play(quant: 0);


	// Pbindef 3: Glitchy Digital Rhythm with glitch percussion synth
	// Pbindef(\glitchRhythm,
	// 	\instrument, \glitchPerc,
	// 	\freq, Prand([100, 200, 300, 450, 600], inf),
	// 	\amp, 0.3,
	// 	\density, Pwhite(10, 50, inf),
	// 	\decay, Pwhite(0.05, 0.2, inf),
	// 	\ffreq, Pwhite(1000, 8000, inf),
	// 	\pan, Pwhite(-0.7, 0.7, inf),
	// 	// Visual parameters
	// 	\type, \customEvent,
	// 	\shape, Prand([\cross, \star, \triangle], inf),
	// 	\sx, Pwhite(100, 500, inf),
	// 	\sy, Pwhite(100, 400, inf),
	// 	\ex, Pwhite(100, 500, inf),
	// 	\ey, Pwhite(100, 400, inf),
	// 	\xEnv, Pfunc { ce.(\linear) },
	// 	\yEnv, Pfunc { ce.(\linear) },
	// 	\startSize, Pwhite(50, 150, inf),
	// 	\endSize, 2,
	// 	\sizeEnv, Pfunc { ce.(\step) },
	// 	\startColor, Pfunc({ Color.hsv(0.05, 1, 1) }),
	// 	\endColor, Pfunc({ Color.hsv(0, 0.9, 1) }),
	// 	\colorEnv, Pfunc { Env([0, 1], [1], \step) },
	// 	\rotation, 0,
	// 	\dur, 0.25,
	// 	\duration, 1.5
	// ).play(quant: 0);

	// Pbindef 4: Melodic Plucks with plucked string synth
	// Pbindef(\melodicPlucks,
	// 	\instrument, \pluck,
	// 	\octave, Pseq([4, 5], inf),
	// 	\degree, Pseq([0, 2, 4, 3, 7, 6, 4, 2], inf),
	// 	\scale, Scale.harmonicMinor,
	// 	\root, Pseq([0, 3, 5, -2], inf),
	// 	\amp, 0.4,
	// 	\decay, Pwhite(0.3, 1.5, inf),
	// 	\coef, Pwhite(0.05, 0.2, inf),
	// 	\pan, Pseg(Pseq([-0.6, 0.6], inf), 4, \sine, inf),
	// 	// Visual parameters
	// 	\type, \customEvent,
	// 	\shape, \square,
	// 	\sx, 300,
	// 	\sy, Pfunc({ |e| 300.neg + ((e.degree + (e.octave * 7)) * 15) }),
	// 	\ex, 300,
	// 	\ey, Pkey(\sy),
	// 	\xEnv, Pfunc { ce.(\anticipate) },
	// 	\yEnv, Pfunc { ce.(\linear) },
	// 	\startSize, Pkey(\decay) * 40,
	// 	\endSize, 10,
	// 	\sizeEnv, Pfunc { ce.(\bounce) },
	// 	\startColor, Pfunc({ |e| Color.hsv(((e.degree + (e.root * 7))/12) % 1, 0.9, 0.9) }),
	// 	\endColor, Pfunc({ |e| Color.hsv(((e.degree + (e.root * 7) + 7)/12) % 1, 0.7, 0.6) }),
	// 	\colorEnv, Pfunc { ce.(\linear) },
	// 	\rotation, Pwhite(pi.neg,pi),
	// 	\dur, Pwrand([0.25, 0.5, 0.125], [0.6, 0.3, 0.1], inf),
	// 	\duration, 2
	// ).play(quant: 0);




});
)

/*

timing of envelopes : this is set by the dif between start and end time and duration
how do we make an instance fpor each UserView

*/






