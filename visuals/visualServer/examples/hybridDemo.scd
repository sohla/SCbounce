// Hybrid Pattern + Live Input Demo
// Combines pattern-based visual generation with real-time live input modulation
// Standalone demo - includes dependency loading

// Load Visual Server system
(thisProcess.nowExecutingPath.dirname.dirname +/+ "loadVisualServer.scd").load;

// Wait for classes to load, then initialize
(
// Initialize system
v = VisualServer.default;
v.start;
s.boot;

// Create main view with effects
~view = v.createView(\hybrid, Rect(150, 50, 1200, 800), Color.black);
VisualRenderer.default.addGrid(\hybrid, 100, Color.gray(0.1));
VisualRenderer.default.addPerformanceMonitor(\hybrid);

// Mock sensor data
~sensors = (
    accel: (x: 0, y: 0, z: 0, magnitude: 0),
    gyro: (x: 0, y: 0, z: 0),
    touch: false,
    pressure: 0
);

// Sensor simulation
~sensorSim = Routine({
    var time = 0;
    loop {
        time = time + 0.03;
        
        // Accelerometer with different frequencies
        ~sensors.accel.x = sin(time * 1.3) * 0.9;
        ~sensors.accel.y = cos(time * 0.7) * 0.8;
        ~sensors.accel.z = sin(time * 2.2) * 0.5;
        ~sensors.accel.magnitude = (~sensors.accel.x.squared + 
                                   ~sensors.accel.y.squared + 
                                   ~sensors.accel.z.squared).sqrt;
        
        // Gyroscope
        ~sensors.gyro.x = sin(time * 0.6) * pi;
        ~sensors.gyro.y = cos(time * 1.4) * pi * 0.5;
        ~sensors.gyro.z = sin(time * 0.9) * pi;
        
        // Touch simulation (random events)
        ~sensors.touch = 0.02.coin;
        ~sensors.pressure = if (~sensors.touch) { 0.5 + 0.5.rand } { 
            ~sensors.pressure * 0.95 
        };
        
        0.03.wait;
    };
}).play;

// Audio synths
SynthDef(\pulse, {|freq=440, amp=0.3, dur=0.5, width=0.5|
    var env = EnvGen.kr(Env.perc(0.01, dur), doneAction: 2);
    var sig = Pulse.ar(freq, width) * env * amp;
    Out.ar(0, sig ! 2);
}).add;

SynthDef(\pad, {|freq=220, amp=0.2, gate=1, cutoff=1000|
    var env = EnvGen.kr(Env.asr(0.1, 1, 0.3), gate, doneAction: 2);
    var sig = RLPF.ar(Saw.ar(freq * [1, 1.01]), cutoff, 0.3);
    Out.ar(0, sig * env * amp);
}).add;
)

// 1. Background ambient pattern (not affected by live input)
(
~ambientPattern = Vbind(
    \instrument, \circle,
    \x, Psin(Ptime * 0.2, 0.9),
    \y, Pcos(Ptime * 0.15, 0.7),
    \size, Pexprand(5, 15),
    \color, Pseq([
        Color.blue.alpha_(0.3),
        Color.purple.alpha_(0.2),
        Color.cyan.alpha_(0.25)
    ], inf),
    \dur, Pexprand(2, 5),
    \view, \hybrid
);

~ambientPlayer = ~ambientPattern.vplay;
)

// 2. Pattern modulated by live input
(
~modulatedPattern = Vbind(
    \instrument, \pulse,
    \x, Pwhite(-0.7, 0.7) + Pfunc({ ~sensors.accel.x * 0.3 }), // Base random + sensor offset
    \y, Pwhite(-0.7, 0.7) + Pfunc({ ~sensors.accel.y * 0.3 }),
    \startSize, 10,
    \endSize, Pexprand(40, 80) * Pfunc({ ~sensors.accel.magnitude + 0.5 }), // Size affected by movement
    \color, Pfunc({
        var base = Color.hsv(0.8, 0.7, 1.0); // Base blue
        var modulation = ~sensors.gyro.z.abs / pi;
        Color.hsv(0.8 + (modulation * 0.3), 0.7, 1.0); // Hue shift with gyro
    }),
    \dur, Pexprand(0.3, 1.0) * Pfunc({ 1 + (~sensors.pressure * 2) }), // Duration affected by pressure
    \curve, \exp,
    \view, \hybrid
);

~modulatedPlayer = ~modulatedPattern.vplay;
)

// 3. Audio pattern synchronized with modulated visuals  
(
~audioPattern = Pbind(
    \instrument, \pulse,
    \freq, Pseq([60, 64, 67, 72].midicps, inf) * Pfunc({ 1 + (~sensors.accel.z * 0.1) }),
    \amp, 0.4 * Pfunc({ ~sensors.accel.magnitude * 0.5 + 0.5 }),
    \width, Pfunc({ ~sensors.pressure * 0.4 + 0.3 }),
    \dur, 0.5
);

~audioPlayer = ~audioPattern.play;
)

// 4. Pure live visual elements
(
// Central live circle - direct sensor mapping
~liveCentral = v.vnew(\live, 4001, \hybrid, [
    \x, { ~sensors.accel.x * 0.6 },
    \y, { ~sensors.accel.y * 0.6 },
    \size, { ~sensors.accel.magnitude * 60 + 20 },
    \color, { 
        Color.hsv(
            ~sensors.gyro.z.abs.linlin(0, pi, 0.2, 0.8),
            ~sensors.accel.magnitude.linlin(0, 1.5, 0.5, 1.0),
            1.0
        );
    },
    \dur, inf,
    \fill, { ~sensors.pressure > 0.1 } // Fill when pressure detected
]);

// Rotating indicator
~liveSpinner = v.vnew(\spinner, 4002, \hybrid, [
    \x, { ~sensors.accel.x * 0.3 },
    \y, { ~sensors.accel.y * 0.3 },
    \length, { ~sensors.accel.magnitude * 40 + 30 },
    \speed, { ~sensors.gyro.z * 0.3 },
    \color, Color.white.alpha_(0.8),
    \width, 2,
    \dur, inf
]);
)

// 5. Touch/gesture triggered events
(
~lastTouchTime = 0;
~gestureMonitor = Routine({
    loop {
        var now = thisThread.seconds;
        
        // Touch events trigger visual bursts
        if (~sensors.touch && (now - ~lastTouchTime) > 0.5) {
            // Create burst of circles
            (3 + 5.rand).do {
                (
                    type: \vpulse,
                    x: ~sensors.accel.x + 0.3.rand2,
                    y: ~sensors.accel.y + 0.3.rand2,
                    startSize: 5,
                    endSize: ~sensors.pressure * 100 + 50,
                    color: Color.hsv(1.0.rand, 0.8, 1.0),
                    dur: 1 + 1.rand,
                    curve: [\exp, \sin, \linear].choose,
                    view: \hybrid
                ).play;
            };
            
            // Audio feedback
            (
                instrument: \pulse,
                freq: [200, 300, 400, 500].choose * (1 + (~sensors.pressure * 0.5)),
                amp: ~sensors.pressure * 0.6 + 0.2,
                dur: 0.3
            ).play;
            
            ~lastTouchTime = now;
        };
        
        // High movement triggers lines
        if (~sensors.accel.magnitude > 1.2) {
            (
                type: \vline,
                x1: ~sensors.accel.x * 0.8,
                y1: ~sensors.accel.y * 0.8,
                x2: (~sensors.accel.x + ~sensors.gyro.x * 0.1) * 0.8,
                y2: (~sensors.accel.y + ~sensors.gyro.y * 0.1) * 0.8,
                color: Color.yellow.alpha_(0.8),
                width: ~sensors.accel.magnitude,
                dur: 0.8,
                view: \hybrid
            ).play;
        };
        
        0.05.wait;
    };
}).play;
)

// 6. Environmental response - changes background activity
(
~environmentMonitor = Routine({
    var activityLevel = 0;
    var backgroundHue = 0;
    
    loop {
        // Calculate activity level
        var movement = ~sensors.accel.magnitude;
        var rotation = ~sensors.gyro.x.abs + ~sensors.gyro.y.abs + ~sensors.gyro.z.abs;
        activityLevel = ((activityLevel * 0.9) + ((movement + rotation * 0.2) * 0.1)).clip(0, 1);
        
        // Adjust background based on activity
        if (activityLevel > 0.3) {
            backgroundHue = backgroundHue + 0.01;
            
            // Spawn background particles when active
            if (0.1.coin) {
                (
                    type: \visual,
                    instrument: \circle,
                    x: 1.0.rand2,
                    y: 1.0.rand2, 
                    size: activityLevel * 20 + 5,
                    color: Color.hsv(backgroundHue.wrap(0, 1), 0.3, activityLevel),
                    dur: 2 + 3.rand,
                    view: \hybrid
                ).play;
            };
        };
        
        0.1.wait;
    };
}).play;
)

// 7. Control interface - patterns that respond to specific gestures
(
// Tilt left/right changes visual density
~densityControl = Routine({
    var currentDensity = 1;
    loop {
        var tilt = ~sensors.accel.x;
        var targetDensity = tilt.abs.linlin(0, 1, 0.1, 3.0);
        currentDensity = currentDensity + ((targetDensity - currentDensity) * 0.1);
        
        // Adjust pattern timing based on density
        if (~modulatedPlayer.notNil) {
            // This would require pattern modification - simplified for demo
        };
        
        0.1.wait;
    };
}).play;

// Pressure controls audio pad
~padSynth = nil;
~padControl = Routine({
    loop {
        if (~sensors.pressure > 0.1) {
            if (~padSynth.isNil) {
                ~padSynth = (
                    instrument: \pad,
                    freq: 110 + (~sensors.accel.y.abs * 110),
                    cutoff: 500 + (~sensors.accel.magnitude * 1500),
                    amp: ~sensors.pressure * 0.3
                ).play;
            } {
                ~padSynth.set(
                    \freq, 110 + (~sensors.accel.y.abs * 110),
                    \cutoff, 500 + (~sensors.accel.magnitude * 1500),
                    \amp, ~sensors.pressure * 0.3
                );
            };
        } {
            if (~padSynth.notNil) {
                ~padSynth.release;
                ~padSynth = nil;
            };
        };
        
        0.05.wait;
    };
}).play;
)

// Cleanup function
(
~cleanup = {
    ~sensorSim.stop;
    ~gestureMonitor.stop;
    ~environmentMonitor.stop;
    ~densityControl.stop;
    ~padControl.stop;
    ~ambientPlayer.stop;
    ~modulatedPlayer.stop;
    ~audioPlayer.stop;
    if (~padSynth.notNil) { ~padSynth.free };
    s.freeAll;
    v.freeAll;
    "Hybrid demo stopped".postln;
};

"Hybrid demo running. Evaluate ~cleanup.() to stop.".postln;
)