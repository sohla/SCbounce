// Live Input Visual Examples
// Demonstrates using live data streams to drive visuals
// Standalone demo - includes dependency loading

// Load Visual Server system
(thisProcess.nowExecutingPath.dirname.dirname +/+ "loadVisualServer.scd").load;

// Wait for classes to load, then initialize
(
// Initialize
v = VisualServer.default;
v.start;

// Create view
~view = v.createView(\liveDemo, Rect(100, 100, 900, 700), Color.black);

// Mock IMU data for demonstration (replace with real data sources)
~mockIMU = (
    accel: (x: 0, y: 0, z: 0),
    gyro: (x: 0, y: 0, z: 0),
    mag: (x: 0, y: 0, z: 0)
);

// Routine to simulate changing IMU data
~imuSimulator = Routine({
    var time = 0;
    loop {
        time = time + 0.05;
        
        // Simulate accelerometer data
        ~mockIMU.accel.x = sin(time * 1.2) * 0.8;
        ~mockIMU.accel.y = cos(time * 0.8) * 0.6;
        ~mockIMU.accel.z = sin(time * 2.1) * 0.4;
        
        // Simulate gyroscope data
        ~mockIMU.gyro.x = sin(time * 0.7) * pi;
        ~mockIMU.gyro.y = cos(time * 1.1) * pi;
        ~mockIMU.gyro.z = sin(time * 0.5) * pi;
        
        0.05.wait; // 20 FPS update rate
    };
}).play;

// Add performance monitor
VisualRenderer.default.addPerformanceMonitor(\liveDemo);
)

// Example 1: Direct mapping - circle position follows accelerometer
(
// Create a live visual node
~liveCircle = v.vnew(\live, 3001, \liveDemo, [
    \x, { ~mockIMU.accel.x },           // X follows accel X
    \y, { ~mockIMU.accel.y },           // Y follows accel Y  
    \size, { ~mockIMU.accel.z.abs * 50 + 30 }, // Size follows accel Z magnitude
    \color, { 
        Color.hsv(
            (~mockIMU.accel.x + 1) / 2,      // Hue from accel X
            0.8,
            (~mockIMU.accel.z.abs + 0.2).clip(0, 1) // Brightness from accel Z
        )
    },
    \dur, inf,
    \fill, true
]);
)

// Example 2: Multiple live elements with different mappings
(
// Main circle - position from accelerometer
v.vnew(\live, 3002, \liveDemo, [
    \x, { ~mockIMU.accel.x * 0.8 },
    \y, { ~mockIMU.accel.y * 0.8 },
    \size, 60,
    \color, Color.white,
    \dur, inf
]);

// Trail circles with delay
4.do { |i|
    var delay = (i + 1) * 0.1;
    var delayedPos = Array.fill(2, 0); // [x, y]
    
    // Create delayed position tracking
    Routine({
        var history = Array.fill(10, [0, 0]);
        var writeIndex = 0;
        
        loop {
            // Store current position
            history[writeIndex] = [~mockIMU.accel.x, ~mockIMU.accel.y];
            writeIndex = (writeIndex + 1) % history.size;
            
            // Read delayed position
            var readIndex = (writeIndex + (i + 1) * 2) % history.size;
            delayedPos[0] = history[readIndex][0];
            delayedPos[1] = history[readIndex][1];
            
            0.05.wait;
        };
    }).play;
    
    v.vnew(\live, 3010 + i, \liveDemo, [
        \x, { delayedPos[0] * 0.8 },
        \y, { delayedPos[1] * 0.8 },
        \size, 40 - (i * 8),
        \color, { Color.white.alpha_(0.8 - (i * 0.15)) },
        \dur, inf
    ]);
};
)

// Example 3: Gyroscope-driven spinner
(
v.vnew(\spinner, 3020, \liveDemo, [
    \x, 0,
    \y, { ~mockIMU.accel.y * 0.3 }, // Y position from accel
    \length, { ~mockIMU.accel.z.abs * 80 + 40 }, // Length from accel Z
    \speed, { ~mockIMU.gyro.z * 0.5 }, // Rotation speed from gyro Z
    \color, { 
        Color.hsv(~mockIMU.gyro.z.abs.linlin(0, pi, 0, 1), 0.8, 1.0)
    },
    \width, 3,
    \dur, inf
]);
)

// Example 4: Particle-like system driven by movement
(
// Create multiple circles that react to movement intensity
~particles = Array.newClear(8);

8.do { |i|
    var angle = i * 2pi / 8;
    var baseX = cos(angle) * 0.3;
    var baseY = sin(angle) * 0.3;
    
    ~particles[i] = v.vnew(\live, 3030 + i, \liveDemo, [
        \x, { 
            var movement = (~mockIMU.accel.x.squared + ~mockIMU.accel.y.squared).sqrt;
            baseX + (~mockIMU.accel.x * movement * 0.3);
        },
        \y, { 
            var movement = (~mockIMU.accel.x.squared + ~mockIMU.accel.y.squared).sqrt;
            baseY + (~mockIMU.accel.y * movement * 0.3);
        },
        \size, { 
            var movement = (~mockIMU.accel.x.squared + ~mockIMU.accel.y.squared).sqrt;
            movement * 40 + 10;
        },
        \color, { 
            var movement = (~mockIMU.accel.x.squared + ~mockIMU.accel.y.squared).sqrt;
            Color.hsv(i / 8, 0.8, movement + 0.2);
        },
        \dur, inf
    ]);
};
)

// Example 5: Live pattern using sensor data
(
// Pattern that uses live input as source
~livePattern = Vbind(
    \instrument, \circle,
    \x, LiveStream({ ~mockIMU.accel.x }, nil, 0.1),
    \y, LiveStream({ ~mockIMU.accel.y }, nil, 0.1),
    \size, LiveStream({ ~mockIMU.accel.z.abs * 30 + 20 }, nil, 0.1),
    \color, Pfunc({
        Color.hsv((~mockIMU.accel.x + 1) / 2, 0.8, 1.0);
    }),
    \dur, 0.2,
    \view, \liveDemo
);

~livePlayer = ~livePattern.vplay;
)

// Example 6: Threshold-based triggering
(
~threshold = 0.5;
~lastTriggerTime = 0;

// Monitor for sudden movements and trigger visuals
Routine({
    loop {
        var movement = (~mockIMU.accel.x.squared + ~mockIMU.accel.y.squared).sqrt;
        var now = thisThread.seconds;
        
        if (movement > ~threshold && (now - ~lastTriggerTime) > 0.3) {
            // Trigger a burst of visuals
            5.do { |i|
                (
                    type: \vpulse,
                    x: 1.0.rand2 * 0.8,
                    y: 1.0.rand2 * 0.8,
                    startSize: 5,
                    endSize: movement * 100 + 50,
                    color: Color.hsv(1.0.rand, 0.8, 1.0),
                    dur: 1.0 + 1.0.rand,
                    curve: \exp,
                    view: \liveDemo
                ).play;
            };
            
            ~lastTriggerTime = now;
            ("Movement burst triggered! Intensity:" + movement).postln;
        };
        
        0.05.wait;
    };
}).play;
)

// Example 7: Live data smoothing
(
// Smoothed position tracking
~smoothedPos = (x: 0, y: 0);
~smoothingFactor = 0.1;

Routine({
    loop {
        // Low-pass filter on position
        ~smoothedPos.x = (~smoothedPos.x * (1 - ~smoothingFactor)) + 
                         (~mockIMU.accel.x * ~smoothingFactor);
        ~smoothedPos.y = (~smoothedPos.y * (1 - ~smoothingFactor)) + 
                         (~mockIMU.accel.y * ~smoothingFactor);
        0.02.wait;
    };
}).play;

// Smooth circle
v.vnew(\live, 3050, \liveDemo, [
    \x, { ~smoothedPos.x },
    \y, { ~smoothedPos.y },
    \size, 40,
    \color, Color.green,
    \dur, inf,
    \fill, false
]);
)

// Example 8: Real IMU data integration (uncomment when you have real data)
/*
// OSC listener for real IMU data
~imuListener = OSCFunc({ |msg|
    ~mockIMU.accel.x = msg[1];
    ~mockIMU.accel.y = msg[2]; 
    ~mockIMU.accel.z = msg[3];
    ~mockIMU.gyro.x = msg[4];
    ~mockIMU.gyro.y = msg[5];
    ~mockIMU.gyro.z = msg[6];
}, '/imu/data');
*/

// Cleanup
/*
~imuSimulator.stop;
~livePlayer.stop;
v.freeAll;
v.free;
*/