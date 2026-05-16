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

VisualEventTypes {
    *initClass {
        Class.initClassTree(Event);

        Event.addEventType(\visual, {
            var server = ~vserver ?? VisualServer.default;
            var def = ~instrument ?? \circle;
            var nodeID = ~nodeID ?? server.nextID;
            server.vnew(def, nodeID, ~view ?? \default, VisualServer.visualArgsFrom(currentEnvironment));
        });

        Event.addEventType(\vpulse, {
            var server = ~vserver ?? VisualServer.default;
            var nodeID = ~nodeID ?? server.nextID;
            server.vnew(\pulse, nodeID, ~view ?? \default, VisualServer.visualArgsFrom(currentEnvironment));
        });

        Event.addEventType(\vline, {
            var server = ~vserver ?? VisualServer.default;
            var nodeID = ~nodeID ?? server.nextID;
            server.vnew(\line, nodeID, ~view ?? \default, VisualServer.visualArgsFrom(currentEnvironment));
        });

        Event.addEventType(\vlive, {
            var server = ~vserver ?? VisualServer.default;
            var nodeID = ~nodeID ?? server.nextID;
            server.vnew(\live, nodeID, ~view ?? \default, VisualServer.visualArgsFrom(currentEnvironment));
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
            e[\dur]        = ~vdur ? (~dur ? 1);

            // Generic
            e[\x]     = ~vx ? (~x ? 0);
            e[\y]     = ~vy ? (~y ? 0);
            e[\size]  = ~vsize ?? ((~amp ? 0.5) * 100);
            e[\color] = ~vcolor ?? Color.hsv(((~freq ? 440).cpsmidi / 127).clip(0, 1), 1, 1);
            e[\fill]  = ~fill ? true;

            // Pulse-specific
            e[\startSize] = ~startSize ? 10;
            e[\endSize]   = ~endSize ?? ((~amp ? 0.5) * 200);
            e[\curve]     = ~curve ? \exp;

            // Line-specific
            e[\x1] = ~vx1 ? -0.5;  e[\y1] = ~vy1 ? 0;
            e[\x2] = ~vx2 ? 0.5;   e[\y2] = ~vy2 ? 0;
            e[\width] = ~width ? 1;

            e.play;
        });

        "VisualServer: event types registered (\\visual \\vpulse \\vline \\vlive \\audioVisual)".postln;
    }
}
