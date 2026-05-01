// Visual Event Types - to be loaded after class compilation
// These need to be in a startup file or loaded separately

Event.addEventType(\visual, {
    var server = ~server ?? VisualServer.default;
    var def = ~instrument ?? \circle;
    var nodeID = ~nodeID ?? server.nextID;
    var viewName = ~view ?? \default;
    
    // Create the visual node
    server.vnew(def, nodeID, viewName, [
        \x, ~x ?? 0,
        \y, ~y ?? 0,
        \size, ~size ?? 50,
        \color, ~color ?? Color.white,
        \dur, ~dur ?? 1,
        \fill, ~fill ?? true
    ]);
});

Event.addEventType(\vpulse, {
    var server = ~server ?? VisualServer.default;
    var nodeID = ~nodeID ?? server.nextID;
    var viewName = ~view ?? \default;
    
    server.vnew(\pulse, nodeID, viewName, [
        \x, ~x ?? 0,
        \y, ~y ?? 0,
        \startSize, ~startSize ?? 10,
        \endSize, ~endSize ?? 100,
        \color, ~color ?? Color.white,
        \dur, ~dur ?? 1,
        \curve, ~curve ?? \exp
    ]);
});

Event.addEventType(\vline, {
    var server = ~server ?? VisualServer.default;
    var nodeID = ~nodeID ?? server.nextID;
    var viewName = ~view ?? \default;
    
    server.vnew(\line, nodeID, viewName, [
        \x1, ~x1 ?? -0.5,
        \y1, ~y1 ?? 0,
        \x2, ~x2 ?? 0.5,
        \y2, ~y2 ?? 0,
        \color, ~color ?? Color.white,
        \width, ~width ?? 1,
        \dur, ~dur ?? 1
    ]);
});

Event.addEventType(\vlive, {
    var server = ~server ?? VisualServer.default;
    var nodeID = ~nodeID ?? server.nextID;
    var viewName = ~view ?? \default;
    
    server.vnew(\live, nodeID, viewName, [
        \x, ~x ?? 0,
        \y, ~y ?? 0,
        \size, ~size ?? 50,
        \color, ~color ?? Color.white,
        \dur, ~dur ?? inf,
        \fill, ~fill ?? true
    ]);
});

// Combined audio-visual event type
Event.addEventType(\audioVisual, {
    // Play audio first
    var originalType = ~type;
    ~type = \note;
    currentEnvironment.play;
    
    // Then trigger visual
    ~type = \visual;
    ~instrument = ~vinstrument ?? \circle;
    ~x = ~vx ?? 0;
    ~y = ~vy ?? 0;
    ~size = ~vsize ?? (~amp ? 0.5 * 100);
    ~color = ~vcolor ?? Color.hsv((~freq ? 440).cpsmidi / 127, 1, 1);
    ~dur = ~vdur ?? (~dur ? 1);
    ~view = ~vview ?? \default;
    
    currentEnvironment.play;
});

"Visual event types loaded".postln;