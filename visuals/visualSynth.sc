// Visual Synth with fixed float conversion
(
// Global storage for visuals
~visualEvents = List[];

// Create a window with a UserView
~window = Window("Visual Synthesizer", Rect(100, 100, 800, 600))
    .front
    .alwaysOnTop_(true);

// Create a UserView that fills the window
~view = UserView(~window, ~window.view.bounds)
    .background_(Color.black)
    .animate_(true)
    .frameRate_(60);

// Default envelope
~defaultEnv = Env([0, 1, 1, 0], [0.1, 0.8, 0.1], \sin);

// Add visual events with safety checks
~addVisual = { |shape, position, size, color, duration, envelope, alphaEnv|
    var event;

    // Type safety - make sure all values are valid
    shape = shape ? \circle;
    position = position ? (400@300);
    size = size ? 50;  // Don't convert yet
    color = color ? Color.white;
    duration = duration ? 5;  // Don't convert yet
    envelope = envelope ? ~defaultEnv;
	alphaEnv = alphaEnv ? Env([1,0], [1], \sin);

    // Create the event with safe parameters
    event = (
        \shape: shape,
        \position: position,
        \size: size,
        \color: color,
        \duration: duration,
        \envelope: envelope,

		\alphaEnv: alphaEnv,
        \startTime: thisThread.seconds
    );

    // Add to the list
    ~visualEvents.add(event);
    "Event added. List now has % items".format(~visualEvents.size).postln;
};

// Drawing function with safer type checks
~view.drawFunc = {
    var now = thisThread.seconds;
    var count = 0;

    // Draw each visual event with careful type handling
    ~visualEvents.do { |event|
        // Initialize variables with default values to establish types
        var elapsed = 0.0;
        var normTime = 0.0;
        var envVal = 1.0;
        var shape = \circle;
        var pos = 400@300;
        var size = 50.0;
        var col = Color.white;

        // Get elapsed time
        if(event[\startTime].notNil) {
            elapsed = now - event[\startTime];
        };

        // Get normalized time safely
        if(event[\duration].notNil && (event[\duration] != 0)) {
            normTime = elapsed / event[\duration];
        } {
            normTime = 0;
        };

        // Only draw if not expired
        if(normTime < 1.0) {

			shape = event[\shape];
            pos = event[\position];
			envVal = event[\envelope].at(normTime);
            size = event[\size] * envVal;
			col = event[\color];
			col = col.alpha_(event[\alphaEnv].at(normTime));

            count = count + 1;

            // Safe drawing based on shape
            Pen.width = 1; // Reset pen width

			switch(shape,
                \circle, {
                    Pen.fillColor = col;
                    Pen.fillOval(Rect.aboutPoint(pos, size, size));
                },
                \square, {
                    Pen.fillColor = col;
                    Pen.fillRect(Rect.aboutPoint(pos, size, size));
                },
                \line, {
                    Pen.strokeColor = col;
                    Pen.width = max(1, size);
                    Pen.line(pos, pos + (100@0));
                    Pen.stroke;
                }
            );
        };
    };

    // Remove expired events safely
    ~visualEvents = ~visualEvents.select { |event|
        var now = thisThread.seconds;
        var elapsed = now - event[\startTime];
        var duration = event[\duration] ? 1;
        elapsed < duration;
    };

    // Always draw a fixed debug circle
    Pen.fillColor = Color.green;
    Pen.fillOval(Rect(10, 40, 20, 20));

    // Draw debug count
    Pen.fillColor = Color.white;
    Pen.stringCenteredIn(
        "Active visuals: " ++ count,
        Rect(10, 10, 200, 20),
        Font("Helvetica", 12),
        Color.white
    );
};

// Clean up when window is closed
~window.onClose = {
    ~view.animate = false;
};

// Initialize SynthDef for audio component
SynthDef(\visualSynth, {
    |freq=440, amp=0.5, pan=0, dur=1|
    var env = EnvGen.kr(Env.perc(0.03, dur, 1, -4), doneAction: 2);
    var osc = SinOsc.ar(freq) * env * amp;
    Out.ar(0, Pan2.ar(osc, pan));
}).add;

// Trigger visual with audio
~triggerVisual = { |shape, position, size, color, duration, envelope, sync=true|
    // Add the visual with type safety
    ~addVisual.value(shape, position, size, color, duration, envelope);

    // Create audio if sync is true
    if(sync) {
        Synth(\visualSynth, [
            \freq, position.y.linexp(0, 600, 1000, 100),
            \amp, size / 100,
            \pan, position.x.linlin(0, 800, -1, 1),
            \dur, duration
        ]);
    };
};

// Clear all visuals
~clearVisuals = {
    ~visualEvents = List[];
};

"Visual synth engine initialized!".postln;
)

// Add a test circle directly
~addVisual.value(\circle, 400@300, 100, Color.red, 10);

// Add another with triggerVisual
~triggerVisual.value(\square, 200@200, 90, Color.blue, 0.5, Env.asr(0.1,1,5));

// Pattern with direct visual creation
(
p = Routine({
    inf.do {
        // Create random visual
        {~triggerVisual.value(
            [\circle, \square, \line].choose,
			(30@600.rand)*(20.rand@1),
            rrand(20, 80),
            Color.hsv(1.0.rand, 0.7, 0.9),
            0.4,
            Env.asr(0.03,1,1),
            true
        );
		}.defer;

        // Wait before next
        0.15.wait;
    };
}).play;
)

// Stop pattern
p.stop;

// Clear all visuals
~clearVisuals.value;


(
Event.addEventType(\customEvent, {|e|

	~triggerVisual.value(
            [\circle, \square, \line].choose,
			(30@600.rand)*(20.rand@1),
            rrand(20, 80),
            Color.hsv(1.0.rand, 0.7, 0.9),
            0.4,
            Env.asr(0.03,1,1),
            false
        );
	// ~instrument = \stereoSampler;
    ~type = \note;
    currentEnvironment.play;
});

)
(
Pdef(\tester,
	Pbind(
		\type, \customEvent,
		\dur, 0.1,
		\octave, Pwhite(3,6),
		\note, Pxrand([0,4,7,11], inf),
		)
	).play;
)





