var shapeLib = Require("shapeLib.scd");
var spacing = 2;
var views = Dictionary.new;

// Function to create a new UserView with its own visualEvents list
var renderer = { |d|
// var makeView = { |name, rect|
    // var visualEvents = List.new(20);
    var updateViewX = {|view|
    view.postln;
    };
    // Define the updateView function for this specific view
    var updateView = {|view|
        var now = thisThread.seconds;

            
        // visualEvents.do { |event|
        views[d.port][\visualEvents].do { |event|
            var elapsed = 0.0, normTime = 0.0;
            var pos = 10@10, size = 50.0, col = Color.white, width = 1;
            var mx = view.bounds.width / 2.0;
            var my = view.bounds.height / 2.0;

            if (event[\startTime].notNil) {
                elapsed = now - event[\startTime];
            };

            if (event[\duration].notNil && (event[\duration] != 0)) {
                normTime = elapsed / event[\duration];
            } {
                normTime = 0;
            };

            if (normTime < 1.0) {
                // Calculate interpolated positions using envelopes
                var x = (event[\sx].blend(event[\ex], event[\xEnv].at(normTime)) * mx) + mx;
                var y = (event[\sy].blend(event[\ey], event[\yEnv].at(normTime)) * my) + my;
                var points;
                pos = x @ y;

                // Calculate width, size, color, and other properties 
                // TODO size norm (smallest of mx/my)
                size = event[\startSize].blend(event[\endSize], event[\sizeEnv].at(normTime));
                
                width = event[\startWidth].blend(event[\endWidth], event[\widthEnv].at(normTime));
                col = event[\startColor].blend(event[\endColor], event[\colorEnv].at(normTime));
                // col = col.alpha_(event[\alphaEnv].at(normTime));

                // Draw the shape
                Pen.width = width;//max(1, size.squared.lincurve(1, 50000, 1, 20, 0.1));
                Pen.rotate(event[\rotation], pos.x, pos.y);
                Pen.fillColor = col;
                Pen.strokeColor = col;

                // Generate base points for the shape

                if(event[\points].notNil) {
                    // Use predefined points
                    points = event[\points];
                } {
                    // Generate points based on shape name
                    var shapeFunc = shapeLib[event[\shape]] ? shapeLib[\circle];
                    points = shapeFunc.(pos, size, event[\numPoints] ? 32);
                };

                // Apply modulation to points if enabled
                if(event[\modulation].notNil) {

                    var modFreq = event[\modulation][\freq] ? 0.5;
                    var baseModAmp = event[\modulation][\amp] ? 5;
                    var modPhase = event[\modulation][\phase] ? 0;
                    var modType = event[\modulation][\type] ? \radial;
                    // Scale modulation amplitude by the current size for proportional effects
                    var modAmp = baseModAmp * ( 1.0 - event[\sizeEnv].at(normTime));
                    var lfoValue = sin(2pi * modFreq * now + modPhase) * modAmp;
                    var harmonics = event[\modulation][\harmonics] ? 1;

                    points = points.collect { |point, i|
                        var t = i / (points.size - 1); // Normalized position in shape (0-1)
                        var vec, len, angle, modVector;

                        switch(modType,
                            \radial, {
                                var modFactor = lfoValue * sin(harmonics * t * 2pi).abs;
                                // Modulate along radius vector
                                vec = point - pos;
                                len = vec.rho;
                                angle = vec.theta;

                                // Add oscillation to radius with potential harmonic variation
                                len = len + modFactor;
                                pos + Polar(len, angle).asPoint;
                            },
                            \normal, {
                                // Modulate perpendicular to radius
                                vec = point - pos;
                                angle = vec.theta;

                                // Calculate perpendicular movement with potential harmonic variation
                                modVector = Polar(lfoValue * sin(harmonics * t * 2pi), angle + 0.5pi).asPoint;
                                point + modVector;
                            },
                            \noise, {
                                // Random jitter based on time but consistent per frame
                                var seed = (i * 1000) + (now.floor * 50);
                                var rnd = {|s| sin(s * 12345.6789).abs };
                                var jitterFactor = sin(t * harmonics * 2pi);
                                var jitterX = lfoValue * jitterFactor * (rnd.(seed) * 2 - 1);
                                var jitterY = lfoValue * jitterFactor * (rnd.(seed + 500) * 2 - 1);
                                point + (jitterX @ jitterY);
                            },
                            \phase, {
                                // Phase modulation for wave-like shapes
                                // if(event[\shape] == \wave) {
                                    var idx = (i + (lfoValue * harmonics)).wrap(0, points.size - 1);
                                    var idealPoint = points[idx.floor];
                                    var nextPoint = points[idx.ceil % points.size];
                                    var blend = idx - idx.floor;
                                    idealPoint.blend(nextPoint, blend);
                                // } {
                                //     point  // Only applies to wave shapes
                                // };
                            },
                            { point } // Default case - no modulation
                        );
                    };
                };

                // Render the shape
                if(event[\fill] ? false) {
                    // Fill the shape
                    Pen.moveTo(points[0]);
                    points[1..].do { |point| Pen.lineTo(point) };
                    if(event[\closed] ? true) { Pen.lineTo(points[0]) }; // Close the path
                    if(event[\shape] == \line) {
                        Pen.stroke;
                    }{
                        Pen.fill;
                    };
                }  {
                    // Stroke the shape
                    Pen.moveTo(points[0]);
                    points[1..].do { |point| Pen.lineTo(point) };
                    if(event[\closed] ? true) { Pen.lineTo(points[0]) }; // Close the path
                    Pen.stroke;
                };

                Pen.rotate(event[\rotation].neg, pos.x, pos.y);
            };
        };

        // Remove expired events
        views[d.port][\visualEvents] = views[d.port][\visualEvents].select { |event|
            var now = thisThread.seconds;
            var elapsed = now - event[\startTime];
            var duration = event[\duration] ? 1;
            // views[d.port][\visualEvents].size.postln;
            elapsed < duration
        };
    };

    // Create the UserView and store it in the views dictionary
    var view = UserView()
        .mouseDownAction_({false})
        .mouseUpAction_({false})

        .animate_(true)
        // .background_(d.color.alpha_(0.9))
        .frameRate_(60)
        // .clearOnRefresh_(false)
        .drawFunc_({
            // Pen.fillColor = Color.black.alpha_(0.1);
            // Pen.addRect(Rect(0, 0, view.bounds.height, view.bounds.width));
            // Pen.fill;
                updateView.(view);
        });

    views[d.port] = (view: view, visualEvents: List.new(20));
    // visualEvents.postln;
    // view.bounds = rect;
    view
};

// var defaultEnv = Env.asr(0.01, 1, 1);
var defaultEnv = Env([0, 1], [1], \linear);

Event.addEventType(\customVisualEvent, {
    var viewID = ~viewID ? 8888; // Default to view1 if not specified
    // var visualEvents = views[viewID][\visualEvents];

    var event = (
        \shape: ~shape ? \circle,
        \points: ~points,  // Optional custom points array
        \numPoints: ~numPoints, // Optional number of points to generate
        \sx: ~sx ? 0.0,
        \sy: ~sy ? 0.0,
        \ex: ~ex ? 0.0,
        \ey: ~ey ? 0.0,
        \xEnv: ~xEnv ? defaultEnv,
        \yEnv: ~yEnv ? defaultEnv,
        \startWidth: ~startWidth ? 1,
        \endWidth: ~endWidth ? 1,
        \widthEnv: ~sizeEnv ? defaultEnv,
        \startSize: ~startSize ? 50,
        \endSize: ~endSize ? 50,
        \sizeEnv: ~sizeEnv ? defaultEnv,
        \startColor: ~startColor ? Color.white,
        \endColor: ~endColor ? Color.red.alpha_(0.2),
        \colorEnv: ~colorEnv ? defaultEnv,
        \rotation: ~rotation ? 0,
        \duration: ~duration ? 0.5,
        \envelope: ~envelope ? defaultEnv,
        \alphaEnv: ~alphaEnv ? Env([1, 1, 0], [0.0, 1], \sin),
        \fill: ~fill ? false,  // Whether to fill the shape or stroke it
        \closed: ~closed ? true,  // Whether to close the shape
        \modulation: ~modulation,  // Optional modulation settings
        \startTime: thisThread.seconds
    );
    views[viewID][\visualEvents] = views[viewID][\visualEvents].add(event);
    ~type = \note;
    currentEnvironment.play;
});


renderer;