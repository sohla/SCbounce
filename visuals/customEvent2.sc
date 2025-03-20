(
var visualEvents = List.new(20);
var defaultEnv = Env.asr(0.01, 1, 1);
var view;
var p;
var controlBus = Bus.control(s, 4);

var updateView = {
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
            // col = event[\startColor].blend(event[\endColor], event[\colorEnv].at(normTime));
            col = Color.hsv(event[\startColor].hue.blend(event[\endColor].hue, event[\colorEnv].at(normTime)),1,1);
            col = col.alpha_(event[\alphaEnv].at(normTime));
            count = count + 1;

            Pen.width = 1;
            Pen.rotate(event[\rotation], pos.x, pos.y);

            switch (shape,
                \circle, {
                    Pen.fillColor = col;
                    Pen.fillOval(Rect.aboutPoint(pos, size, size));
                },
                \square, {
                    // Pen.strokeColor = col;
                    // Pen.width = max(1, size.squared.lincurve(1, 100000, 1, 40, 0.1));
                    // Pen.strokeRect(Rect.aboutPoint(pos, size, size));
                    Pen.fillColor = col;
                    Pen.fillRect(Rect.aboutPoint(pos, size, size));
                },
                \line, {
                    Pen.strokeColor = col;
                    Pen.width = max(1, size.squared.lincurve(1, 100000, 1, 20, 0.1));
                    Pen.line(pos - (size @ 0), pos + (size @ 0));
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
            [1],
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
        \midiNote, Pfunc{|e| ((e.octave * 12) + (e.note) + (e.root))}, //make ourselves
        \type, \customEvent,
        \shape, \circle,
        \sx, Pfunc{(mx.getSynchronous.linlin(0,1,0,1200))},
        \sy, 90 - Pkey(\midiNote) * 10,
        \ex, Pkey(\sx) + 80,
        \ey, 85 - Pkey(\midiNote) * 10,
        \xEnv, Pfunc { ce.(\ripple) },
        \yEnv, Pfunc { ce.(\linear) },
        \startSize, 30,
        \endSize, 0,
        \sizeEnv, Pfunc { ce.(\linear) },
        \hue, Pseg(Pseq([0.5,0.99999], inf), 4, \linear, inf),
        \startColor,  Pfunc({|e|Color.hsv(e.hue,1,1)}),
        \endColor,  Pfunc({|e|Color.hsv(e.hue - 0.5,1,0)}),
        \colorEnv, Pfunc { Env([0, 1], [1], \linear) },
        \rotation, Pseg(Pseq([-pi, pi], inf), 8, \linear, inf),
        // \dur, Pxrand([0.125 * 1], inf),
        \dur, Pfunc{(my.getSynchronous.linlin(0,1,1,0.125))},
		\duration, 4,

    ).play(quant: 0.0);

    Pbindef(\pb,
        \instrument, \woodBamboo,
        \octave, Pxrand([2,3,4].stutter(7), inf),
		\note, Pseq([0,12,7], inf),
        \root, Pseq([0,3,-2,7,-4,2].stutter(3), inf),
        \amp,0.8,
        \noiseMix, 0.1,
        \ringTime, 0.2,
        \midiNote, Pfunc{|e| ((e.octave * 12) + (e.note) + (e.root))}, //make ourselves
        \type, \customEvent,
        \shape, \square,
        \sx, 600,
        \sy, 10 - Pkey(\root) + Pkey(\octave) * 32,
        \ex, 600,
        \ey, Pkey(\sy) + 100,
        \xEnv, Pfunc { ce.(\anticipate) },
        \yEnv, Pfunc { ce.(\anticipate) },
        \startSize, 60,
        \endSize, 0,
        \sizeEnv, Pfunc { ce.(\linear) },
        \hue, Pseg(Pseq([0.0,0.49999], inf), 4, \linear, inf),
        \startColor,  Pfunc({|e|Color.hsv(e.hue,1,1)}),
        \endColor,  Pfunc({|e|Color.hsv(e.hue + 0.5,1,0)}),
        \colorEnv, Pfunc { Env([0, 1], [1], \linear) },
        \rotation, Pseg(Pseq([-pi, pi], inf), 8, \linear, inf),
        \dur, Pxrand([0.125 * 4], inf),
		\duration, Pkey(\ringTime) * 3,

    ).play(quant: 0.0);    
});
)

/*

timing of envelopes : this is set by the dif between start and end time and duration
how do we make an instance fpor each UserView

*/