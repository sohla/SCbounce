// Auto-registers the visual event types at class-library compile time.
//
// SC runs *initClass automatically on every recompile, so the user no longer
// has to load VisualEventTypes.scd manually. This is the canonical SC pattern
// (see Pdef:*initClass / EventTypesWithCleanup:*initClass in the core lib):
// register event types inside *initClass, guarded by Class.initClassTree(Event)
// so Event.default[\eventTypes] exists first (SC does not guarantee *initClass
// ordering between classes).
//
// The event-type function bodies reference VisualServer / VisualSynthDef only
// at event-PLAY time (not at initClass time), so there is no initClass
// ordering dependency on those classes - only Event must be ready.
//
// Each visual event type forwards every provided shape param via
// VisualServer.visualArgsFrom; each VisualSynthDef supplies its own defaults.
// NOTE: the standard event play function forces ~server = Server.default
// (the AUDIO server), so use ~vserver (else VisualServer.default).
//
// \timeDelay (Vbind) / \vtimeDelay (AVbind): seconds to defer the visual
// before vnew. Vbind default 0 (immediate, unchanged). AVbind defaults it to
// the audio server's latency so audio (sent at +s.latency) and visual
// coincide; override to also compensate projector/display lag.

VisualEventTypes {
    *initClass {
        Class.initClassTree(Event);

        Event.addEventType(\visual, {
            var server = ~vserver ?? VisualServer.default;
            VisualEventTypes.spawn(server, ~instrument ?? \circle,
                ~nodeID ?? server.nextID, ~view ?? \default,
                VisualServer.visualArgsFrom(currentEnvironment),
                (~timeDelay ? 0).max(0));
        });

        Event.addEventType(\vpulse, {
            var server = ~vserver ?? VisualServer.default;
            VisualEventTypes.spawn(server, \pulse,
                ~nodeID ?? server.nextID, ~view ?? \default,
                VisualServer.visualArgsFrom(currentEnvironment),
                (~timeDelay ? 0).max(0));
        });

        Event.addEventType(\vline, {
            var server = ~vserver ?? VisualServer.default;
            VisualEventTypes.spawn(server, \line,
                ~nodeID ?? server.nextID, ~view ?? \default,
                VisualServer.visualArgsFrom(currentEnvironment),
                (~timeDelay ? 0).max(0));
        });

        Event.addEventType(\vlive, {
            var server = ~vserver ?? VisualServer.default;
            VisualEventTypes.spawn(server, \live,
                ~nodeID ?? server.nextID, ~view ?? \default,
                VisualServer.visualArgsFrom(currentEnvironment),
                (~timeDelay ? 0).max(0));
        });

        // Combined audio-visual event type.
        // Plays the audio note, then builds a separate visual event routed to
        // the correct sub-type so shape-specific params (startSize/endSize/
        // curve, line endpoints, ...) are not lost.
        Event.addEventType(\audioVisual, {
            var vinst, e;

            // Play audio first
            ~type = \note;
            currentEnvironment.play;

            // In AVbind every VISUAL key is v-prefixed (the audio side keeps
            // the plain SC note keys: \instrument \freq \amp \dur \legato
            // \sustain ...). One rule, no collisions. Vbind stays plain.
            vinst = ~vinstrument ? \circle;
            e = ();
            e[\type] = switch(vinst,
                \pulse, { \vpulse },
                \line,  { \vline },
                \live,  { \vlive },
                { \visual }        // circle / square / anything else
            );
            e[\instrument] = vinst;
            e[\view]       = ~vview ? \default;
            e[\dur]        = ~vdur ? (~dur ? 1);   // visual node base lifetime

            // Generic appearance/position
            e[\x]        = ~vx ? 0;
            e[\y]        = ~vy ? 0;
            e[\size]     = ~vsize ?? ((~amp ? 0.5) * 100);   // audio amp -> default
            e[\color]    = ~vcolor ?? Color.hsv(((~freq ? 440).cpsmidi / 127).clip(0, 1), 1, 1);
            e[\fill]     = ~vfill ? true;
            e[\rotation] = ~vrotation;   // nil -> def default (0)

            // Pulse
            e[\startSize] = ~vstartSize ? 10;
            e[\endSize]   = ~vendSize ?? ((~amp ? 0.5) * 200);
            e[\curve]     = ~vcurve ? \exp;

            // Line
            e[\x1] = ~vx1 ? -0.5;  e[\y1] = ~vy1 ? 0;
            e[\x2] = ~vx2 ? 0.5;   e[\y2] = ~vy2 ? 0;
            e[\width] = ~vwidth ? 1;

            // Envelope (nil -> envState default; legato/sustain/etc. here are
            // the VISUAL ones, separate from the audio note's legato/sustain)
            e[\attack]  = ~vattack;
            e[\release] = ~vrelease;
            e[\legato]  = ~vlegato;
            e[\sustain] = ~vsustain;
            e[\stretch] = ~vstretch;

            // Delay the visual so it coincides with the heard audio (which is
            // sent at +server.latency). Default = the audio server's latency;
            // override \vtimeDelay to also compensate projector/display lag.
            e[\timeDelay] = ~vtimeDelay ? (~server ? Server.default).latency;

            e.play;
        });

        "VisualServer: event types registered (\\visual \\vpulse \\vline \\vlive \\audioVisual)".postln;
    }

    // Create the visual node now, or after `delay` seconds. Scheduled on
    // AppClock (same thread as the UserView drawFunc, so no race with
    // rendering). `args` is captured by the caller while currentEnvironment
    // is still valid; the nodeID is pre-allocated so it stays stable.
    *spawn { |server, def, nodeID, view, args, delay = 0|
        if (delay > 0) {
            AppClock.sched(delay, { server.vnew(def, nodeID, view, args); nil });
        } {
            server.vnew(def, nodeID, view, args);
        };
    }
}
