// Visual Event class
VisualEvent {
    var <>shape;      // Symbol: \circle, \square, \line, etc.
    var <>position;   // Point: coordinates for the visual
    var <>size;       // Float or Point: dimensions
    var <>color;      // Color: object color
    var <>duration;   // Float: how long it appears
    var <>envelope;   // Env: controls size/opacity changes
    var <>node;       // Int: ID for tracking
    var <>startTime;  // Float: when it starts
    var <>synth;      // Associated synth (optional)

    *new { |shape=\circle, position=(400@300), size=50, color, duration=1, envelope|
        ^super.newCopyArgs(
            shape,
            position,
            size,
            color ?? { Color.hsv(0.5.rand, 0.7, 0.9, 0.7) },
            duration,
            envelope ?? { Env([0, 1, 1, 0], [0.1, 0.8, 0.1], \sin) }
        );
    }

    // Play the visual, possibly synced with audio
    play { |server, syncAudio=false|
        var visualFunc;
        startTime = thisThread.seconds;

        // Assign unique node ID
        node = UniqueID.next;

        // Define visualization functions based on shape
        visualFunc = switch(shape,
            \circle, { { |t, env|
                Pen.fillColor = color.alpha_(color.alpha * env);
                Pen.fillOval(Rect.aboutPoint(position, size * env, size * env));
            } },
            \square, { { |t, env|
                Pen.fillColor = color.alpha_(color.alpha * env);
                Pen.fillRect(Rect.aboutPoint(position, size * env, size * env));
            } },
            \line, { { |t, env|
                Pen.strokeColor = color.alpha_(color.alpha * env);
                Pen.width = size * env;
                Pen.line(position, position + (100@0));
                Pen.stroke;
            } }
        );

        // Add to global renderer
        VisualSynth.renderer.addEvent(this, visualFunc);

        // Optionally create sound
        if(syncAudio) {
            synth = Synth(\visualSynth, [
                \freq, position.y.linexp(0, 600, 1000, 100),
                \amp, size / 100,
                \pan, position.x.linlin(0, 800, -1, 1),
                \dur, duration
            ]);
        };

        // Schedule removal
        SystemClock.sched(duration, {
            VisualSynth.renderer.removeEvent(this);
            if(syncAudio) { synth.release; };
            nil;
        });

        ^this;
    }

    // Get current envelope value based on time
    currentEnv {
        var now = thisThread.seconds;
        var elapsed = now - startTime;
        var normalized = elapsed / duration;
        ^envelope.at(normalized * envelope.times.sum);
    }
}

// Visual Renderer
VisualRenderer {
    var <>window;
    var <>events;
    var <>isRendering = false;

    *new {
        ^super.new.init;
    }

    init {
        events = Dictionary.new;

        window = Window("Visual Synthesizer", Rect(100, 100, 800, 600))
            .front
            .alwaysOnTop_(true);

        window.view.background = Color.black;
        window.onClose = { this.stopRendering; };

        window.drawFunc = {
            events.keysValuesDo { |id, eventData|
                var event = eventData[0];
                var func = eventData[1];
                var envVal = event.currentEnv;

                func.value(event.duration, envVal);
            };
        };
    }

    addEvent { |event, renderFunc|
        events[event.node] = [event, renderFunc];
        if(isRendering.not) { this.startRendering; };
    }

    removeEvent { |event|
        events.removeAt(event.node);
        if(events.isEmpty) { this.stopRendering; };
    }

    startRendering {
        isRendering = true;

        // Start animation loop
        AppClock.sched(0, {
            if(isRendering) {
                window.refresh;
                0.016; // ~60 FPS
            } {
                nil;
            };
        });
    }

    stopRendering {
        isRendering = false;
    }

    clear {
        events = Dictionary.new;
        window.refresh;
    }
}

// Visual Synth - main controller
VisualSynth {
    classvar <>renderer;

    *initClass {
        renderer = VisualRenderer.new;

        // Initialize default synth
		// SynthDef(\visualSynth, {
		// 	|freq=440, amp=0.5, pan=0, dur=1|
		// 	var env = EnvGen.kr(Env.perc(0.01, dur, 1, -4), doneAction: 2);
		// 	var osc = SinOsc.ar(freq) * env * amp;
		// 	Out.ar(0, Pan2.ar(osc, pan));
		// }).add;
    }

    *trigger { |shape, position, size, color, duration, envelope, sync=true|
        ^VisualEvent(shape, position, size, color, duration, envelope)
            .play(Server.default, sync);
    }

    *clear {
        renderer.clear;
    }
}
