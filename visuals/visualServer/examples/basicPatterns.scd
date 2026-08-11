// Basic Visual Pattern Examples
// Run this after installing Visual Server classes

// Check if classes are installed
(
if (VisualServer.isNil) {
    "ERROR: Visual Server classes not installed!".postln;
    "Please run loadVisualServer.scd and follow installation instructions.".postln;
} {
    "✓ Visual Server classes found".postln;
    
    // Event types auto-register at class compile (no manual load needed)

    // Initialize the visual server
    ~v = VisualServer.default;
    ~v.start;
    
    // Create a view
    ~view = ~v.createView(\demo, Rect(200, 200, 800, 600), Color.black);
    "✓ System initialized".postln;
};
)

// Example 1: Colorful random circles
(
if (Vbind.notNil) {
    ~circlePattern = Vbind(
        \x, Pwhite(-0.8, 0.8),
        \y, Pwhite(-0.8, 0.8),
        \size, Pexprand(20, 80),
        \color, Pfunc({ Color.hsv(1.0.rand, 0.8, 1.0) }),
        \dur, Pexprand(0.2, 0.6),
        \view, \demo
    );
    ~circlePlayer = ~circlePattern.vplay;
    "✓ Circle pattern started".postln;
};
)

// Example 2: Expanding pulse animation
(
if (Vpulse.notNil) {
    ~pulsePattern = Vpulse(
        \x, Pseq([-0.6, -0.2, 0.2, 0.6], inf),
        \y, 0,
        \startSize, 5,
        \endSize, Pexprand(80, 150),
        \color, Pseq([Color.cyan, Color.magenta, Color.yellow, Color.white], inf),
        \dur, 1,
        \curve, \exp,
        \view, \demo
    );
    ~pulsePlayer = ~pulsePattern.vplay;
    "✓ Pulse pattern started".postln;
};
)

// Example 3: Position-based color mapping
(
if (Vbind.notNil) {
    ~colorPattern = Vbind(
        \x, Pwhite(-0.9, 0.9),
        \y, Pwhite(-0.9, 0.9),
        \size, Pseq([20, 30, 40, 50, 40, 30], inf),
        \color, Pfunc({ |ev|
            var hue = (ev[\x] + 1) / 2; // Map x position to hue
            Color.hsv(hue, 0.8, 1.0);
        }),
        \dur, 0.25,
        \view, \demo
    );
    ~colorPlayer = ~colorPattern.vplay;
    "✓ Color-mapped pattern started".postln;
};
)

// Example 4: Line patterns
(
if (Vline.notNil) {
    ~linePattern = Vline(
        \x1, Pwhite(-0.8, 0.8),
        \y1, Pwhite(-0.8, 0.8),
        \x2, Pkey(\x1) + Pwhite(-0.2, 0.2),
        \y2, Pkey(\y1) + Pwhite(-0.2, 0.2),
        \color, Pseq([Color.red, Color.green, Color.blue], inf),
        \width, Pexprand(1, 5),
        \dur, 0.3,
        \view, \demo
    );
    ~linePlayer = ~linePattern.vplay;
    "✓ Line pattern started".postln;
};
)

// Example 5: Spinning lines (rotating \line replaces the old \spinner def).
// \rotation is in radians; a Function is re-evaluated every frame so passing
// a time-dependent expression spins the shape continuously.
(
if (VisualServer.notNil) {
    "Creating spinning lines...".postln;
    5.do { |i|
        var xc   = (i - 2) * 0.3;         // centre x (normalised)
        var half = 0.05 + (i * 0.012);    // half-length (normalised)
        var rps  = 0.5 + (i * 0.2);       // revolutions per second
        ~v.vnew(\line, 2000 + i, \demo, [
            \x1, xc - half, \y1, 0, \x2, xc + half, \y2, 0,
            \width, 2 + i,
            \rotation, { thisThread.seconds * 2pi * rps },
            \color, Color.hsv(i / 5, 0.8, 1.0),
            \dur, inf
        ]);
    };
    "✓ 5 spinning lines created".postln;
};
)

// Example 6: Color harmony pattern
(
if (Vbind.notNil) {
    var baseHue = 0.6; // Blue base
    ~harmonyPattern = Vbind(
        \x, Pwhite(-0.7, 0.7),
        \y, Pwhite(-0.7, 0.7),
        \size, Pexprand(15, 60),
        \color, Pfunc({
            var hue = baseHue + [-0.1, 0, 0.1, 0.2].choose; // Analogous colors
            Color.hsv(hue.wrap(0, 1), 0.7 + 0.3.rand, 1.0);
        }),
        \dur, Pexprand(0.2, 0.8),
        \view, \demo
    );
    ~harmonyPlayer = ~harmonyPattern.vplay;
    "✓ Color harmony pattern started".postln;
};
)

// Cleanup function
(
~cleanupPatterns = {
    "Stopping all patterns...".postln;
    if (~circlePlayer.notNil) { ~circlePlayer.stop; "✓ Circle pattern stopped".postln; };
    if (~pulsePlayer.notNil) { ~pulsePlayer.stop; "✓ Pulse pattern stopped".postln; };
    if (~colorPlayer.notNil) { ~colorPlayer.stop; "✓ Color pattern stopped".postln; };
    if (~linePlayer.notNil) { ~linePlayer.stop; "✓ Line pattern stopped".postln; };
    if (~harmonyPlayer.notNil) { ~harmonyPlayer.stop; "✓ Harmony pattern stopped".postln; };
    if (~v.notNil) { ~v.freeAll; "✓ All visual nodes freed".postln; };
    "Pattern cleanup complete".postln;
};

"✓ Examples loaded. Run individual examples or ~cleanupPatterns.() to stop all.".postln;
)