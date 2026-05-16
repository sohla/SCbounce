// Quick test of Class-based Visual Server system
// NOTE: Classes must be installed in Extensions folder first!

// Test 1: Check if classes are available and initialize
(
if (VisualServer.isNil) {
    "ERROR: VisualServer class not found!".postln;
    "Please install classes to Extensions folder and recompile class library.".postln;
    "See loadVisualServer.scd for instructions.".postln;
} {
    "✓ Visual Server classes found".postln;

    // Load event types (required for patterns)
    (thisProcess.nowExecutingPath.dirname +/+ "VisualEventTypes.scd").load;
    "✓ Event types loaded".postln;

    // Initialize system
    ~v = VisualServer.default;
    ~v.start;
    ~view = ~v.createView(\test, Rect(300, 200, 600, 400), Color.black);
    "✓ System initialized - view created".postln;

    // Initialize renderer
    ~r = VisualRenderer.default;
    ~r.start;
    "✓ Renderer initialized".postln;
};
)

// Test 2: Basic visual node creation
(
if (VisualServer.notNil) {
    ~circleID = ~v.vnew(\circle, nil, \test, [\x, 0, \y, 0, \size, 50, \color, Color.red]);
    ("✓ Circle created with ID:" + ~circleID).postln;

    // Test parameter updates
    ~v.vset(~circleID, \x, 0.3, \color, Color.green);
    "✓ Circle parameters updated".postln;

    // Create a few more shapes for visual variety
    ~squareID = ~v.vnew(\square, nil, \test, [\x, -0.3, \y, 0.3, \size, 40, \color, Color.blue]);
    ~lineID = ~v.vnew(\line, nil, \test, [\x1, -0.5, \y1, -0.5, \x2, 0.5, \y2, -0.5, \color, Color.yellow]);
    "✓ Additional shapes created".postln;
};
)

// Test 3: Pattern-based visuals
(
if (Vbind.notNil) {
    "Starting pattern tests...".postln;

    // Random circles pattern
    ~circlePattern = Vbind(
        \x, Pwhite(-0.7, 0.7),
        \y, Pwhite(-0.7, 0.7),
        \size, Pexprand(15, 45),
        \color, Pfunc({ Color.hsv(1.0.rand, 0.8, 1.0) }),
        \dur, 0.2,
        \view, \test
    );
    ~circlePlayer = ~circlePattern.vplay;
    "✓ Circle pattern started".postln;

    // Pulse pattern
    ~pulsePattern = Vpulse(
        \x, Pwhite(-0.5, 0.5),
        \y, Pwhite(-0.5, 0.5),
        \startSize, 5,
        \endSize, Pexprand(60, 120),
        \color, Pfunc({ Color.hsv(0.6, 0.8, 0.9) }),
        \dur, 1.5,
        \view, \test
    );
    ~pulsePlayer = ~pulsePattern.vplay;
    "✓ Pulse pattern started".postln;

} {
    "❌ Pattern classes not available".postln;
};
)

// Test 4: Live input simulation
(
if (VisualServer.notNil) {
    "Setting up live input simulation...".postln;

    // Create sensor data container
    ~sensorData = (x: 0, y: 0, size: 50, hue: 0);

    // Simulate sensor changes with routine
    ~simulator = Routine({
        loop {
            // Use [\key] not .key: `size` is Collection:size, so the
            // pseudo-accessor form silently fails for it.
            ~sensorData[\x] = sin(thisThread.seconds * 0.5) * 0.6;
            ~sensorData[\y] = cos(thisThread.seconds * 0.3) * 0.4;
            ~sensorData[\size] = (sin(thisThread.seconds * 1.5) + 1) * 20 + 15;
            ~sensorData[\hue] = (thisThread.seconds * 0.2) % 1.0;
            0.05.wait;
        };
    }).play;

    // Create live visual with function parameters
    ~liveID = ~v.vnew(\live, nil, \test, [
        \x, { ~sensorData[\x] },
        \y, { ~sensorData[\y] },
        \size, { ~sensorData[\size] },
        \color, { Color.hsv(~sensorData[\hue], 0.8, 0.9) },
        \dur, inf
    ]);
    "✓ Live visual created with simulated sensor data".postln;

    // Create a spinner for additional visual interest
    ~spinnerID = ~v.vnew(\spinner, nil, \test, [
        \x, { ~sensorData[\x] * 0.5 },
        \y, { ~sensorData[\y] * 0.5 },
        \length, 80,
        \speed, 2,
        \color, Color.white,
        \dur, inf
    ]);
    "✓ Live spinner created".postln;
};
)

// Test 5: Effects system
(
if (VisualRenderer.notNil) {
    "Adding visual effects...".postln;

    // Add performance monitor (shows FPS and node count)
    ~r.addPerformanceMonitor(\test);
    "✓ Performance monitor added".postln;

    // Add subtle grid overlay
    ~r.addGrid(\test, 60, Color.gray(0.15));
    "✓ Grid overlay added".postln;

    // Add trail effect for motion blur
    ~r.addTrailEffect(\test, 0.05);
    "✓ Trail effect added".postln;

    "All effects active - check the visual window!".postln;
};
)

// Cleanup function
(
~cleanup = {
    "Cleaning up...".postln;

    // Stop routines and patterns
    if (~simulator.notNil) { ~simulator.stop; "✓ Simulator stopped".postln; };
    if (~circlePlayer.notNil) { ~circlePlayer.stop; "✓ Circle pattern stopped".postln; };
    if (~pulsePlayer.notNil) { ~pulsePlayer.stop; "✓ Pulse pattern stopped".postln; };

    // Free all visual nodes
    if (~v.notNil) {
        ~v.freeAll;
        "✓ All visual nodes freed".postln;
    };

    // Clear effects
    if (~r.notNil) {
        ~r.clearEffects(\test);
        "✓ Effects cleared".postln;
    };

    "✓ Cleanup complete - visual window can be closed manually".postln;
};

// Instructions
if (VisualServer.notNil) {
    "".postln;
    "🎉 ALL TESTS COMPLETE!".postln;
    "".postln;
    "What you should see:".postln;
    "- A black window with colorful animated circles".postln;
    "- Expanding pulse animations".postln;
    "- A white spinning line following the main circle".postln;
    "- Grid overlay and performance monitor".postln;
    "- Trail effects for smooth motion".postln;
    "".postln;
    "Run ~cleanup.() when done to stop all animations.".postln;
} {
    "❌ Classes not installed. Please run loadVisualServer.scd for instructions.".postln;
};
)

// ~cleanup.()