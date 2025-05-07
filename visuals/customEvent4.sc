(
    // Dictionary to store visual events for each view
    var views = Dictionary.new;
    var defaultEnv = Env.asr(0.01, 1, 1);

    // Function to generate shape points
    var shapeLib = (
        circle: { |pos, size, numPoints=32|
            Array.fill(numPoints, { |i|
                var angle = i / numPoints * 2pi;
                pos + (Polar(size, angle).asPoint);
            });
        },
        square: { |pos, size, numPoints=4|
            [
                pos + (size.neg @ size.neg),
                pos + (size @ size.neg),
                pos + (size @ size),
                pos + (size.neg @ size)
            ];
        },
        line: { |pos, size, numPoints=2|
            [
                pos - (size @ 0),
                pos + (size @ 0)
            ];
        },
        triangle: { |pos, size, numPoints=3|
            var height = size * sqrt(3) / 2;
            [
                pos + (0 @ (height.neg / 1)), // Top point
                pos + ((size.neg / 1) @ (height / 2)), // Bottom-left
                pos + ((size / 1) @ (height / 2)) // Bottom-right
            ];
        },
        star: { |pos, size, numPoints=10|
            var innerRatio = 0.5, rotation = 0;
            Array.fill(numPoints, { |i|
                var angle = (i * pi / 5) + rotation; // Alternate between inner and outer points
                var radius = if(i % 2 == 0, { size }, { size * innerRatio });
                pos + (radius * cos(angle) @ (radius * sin(angle)));
            });
        },
        hexagon: { |pos, size, numPoints=6|
            Array.fill(numPoints, { |i|
                var angle = (i * (2pi / 6)) + 0;
                pos + ((size * cos(angle)) @ (size * sin(angle)));
            });
        },
        cross: { |pos, size, numPoints=12|
            var thickness = size / 3;
            var halfThick = thickness / 2;
            [
                // Points for vertical bar (top to bottom)
                Point(pos.x - halfThick, pos.y - size),
                Point(pos.x + halfThick, pos.y - size),
                Point(pos.x + halfThick, pos.y - halfThick),

                // Points for horizontal bar (right side)
                Point(pos.x + size, pos.y - halfThick),
                Point(pos.x + size, pos.y + halfThick),

                // Bottom of vertical bar
                Point(pos.x + halfThick, pos.y + halfThick),
                Point(pos.x + halfThick, pos.y + size),
                Point(pos.x - halfThick, pos.y + size),
                Point(pos.x - halfThick, pos.y + halfThick),

                // Left side of horizontal bar
                Point(pos.x - size, pos.y + halfThick),
                Point(pos.x - size, pos.y - halfThick),
                Point(pos.x - halfThick, pos.y - halfThick)
            ];
        },
        wave: { |pos, size, numPoints=64|
            Array.fill(numPoints, { |i|
                var x = pos.x + (i / (numPoints-1) * size * 2) - size; // Map x across the size
                var y = pos.y + (sin(i / (numPoints-1) * 2pi) * (size / 2));
                x @ y
            });
        },
        leaf: { |pos, size, numPoints=20|
            var width = size / 3;
            var points = Array.new(numPoints + 1);

            // Create right edge
            (numPoints/2 + 1).do { |i|
                var t = i / (numPoints/2);
                var x = pos.x + (size * t);
                var y = pos.y + (width * sin(t * pi) * (0.5 + (sin(t * pi * 0.2) * 0.5)));
                points = points.add(x @ y);
            };

            // Create left edge coming back
            (numPoints/2).do { |i|
                var j = numPoints/2 - i - 1; // Count backward
                var t = j / (numPoints/2);
                var x = pos.x + (size * t);
                var y = pos.y - (width * sin(t * pi) * (0.4 + (sin(t * pi * 0.2) * 0.5)));
                points = points.add(x @ y);
            };

            points;
        },
        spiral: { |pos, size, numPoints=100|
            var turns = 3, startRadius = 0;
            Array.fill(numPoints, { |i|
                var progress = i / (numPoints - 1); // 0 to 1
                var angle = progress * turns * 2pi; // Angle increases with each point
                var radius = startRadius + ((size - startRadius) * progress); // Radius increases linearly
                pos + (radius * cos(angle) @ (radius * sin(angle)));
            });
        },
        blobby: { |pos, size, numPoints=64|
            Array.fill(numPoints, { |i|
                var angle = i / numPoints * 2pi;
                var radius = size * (0.8 + (0.3 * sin(angle * 3)) + (0.15 * cos(angle * 5)));
                pos + (radius * cos(angle) @ (radius * sin(angle)));
            });
        }
    );

    // Function to create a new UserView with its own visualEvents list
    var makeView = { |name, rect|
        var visualEvents = List.new(20);

        // Define the updateView function for this specific view
        var updateView = {|view|
            var now = thisThread.seconds;

            views[name][\visualEvents].do { |event|
                var elapsed = 0.0, normTime = 0.0;
                var pos = 10@10, size = 50.0, col = Color.white;

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
                    var x = event[\sx].blend(event[\ex], event[\xEnv].at(normTime));
                    var y = event[\sy].blend(event[\ey], event[\yEnv].at(normTime));
                    var points;
                    pos = x @ y;

                    // Calculate size, color, and other properties
                    size = event[\startSize].blend(event[\endSize], event[\sizeEnv].at(normTime));
                    col = event[\startColor].blend(event[\endColor], event[\colorEnv].at(normTime));
                    col = col.alpha_(event[\alphaEnv].at(normTime));

                    // Draw the shape
                    Pen.width = max(1, size.squared.lincurve(1, 50000, 1, 20, 0.1));
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
                        var modAmp = baseModAmp * (size / event[\startSize]);
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
                        Pen.fill;
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
            visualEvents = visualEvents.select { |event|
                var now = thisThread.seconds;
                var elapsed = now - event[\startTime];
                var duration = event[\duration] ? 1;
                elapsed < duration;
            };
        };

        // Create the UserView and store it in the views dictionary
        var view = UserView()
            .background_(Color.black)
            .animate_(true)
            .frameRate_(60)
            .drawFunc_(updateView);

        views[name] = (view: view, visualEvents: visualEvents);
        view.bounds = rect;
        view;
    };

    // Create a window with multiple views
    var window = Window("Visual Synthesizer", Rect(100, 100, 1200, 800))
        .fullScreen
        .front
        .background_(Color.white.alpha_(1))
        .layout_(
            GridLayout.rows(
                [makeView.(\view1), makeView.(\view2)],
                [makeView.(\view3), makeView.(\view4)]
            ).margins_(0).hSpacing_(1).vSpacing_(1)
        );

    var envLibrary = (
        linear: { Env([0, 1], [1], \linear) },
        spring: { Env([0, 1.2, 0.8, 1.1, 0.9, 1.03, 0.97, 1], [0.3, 0.1, 0.1, 0.1, 0.1, 0.1, 0.2], [\sine, \sine, \sine, \sine, \sine, \sine, \sine]) },
        bounce: { Env([0, 1.1, 0.9, 1.03, 0.97, 1], [0.1, 0.1, 0.1, 0.1, 0.2], [\sine, \sine, \sine, \sine, \sine]) },
        overshoot: { Env([0, -0.1, 1], [0.2, 0.8], [\sine, 3]) },
        anticipate: { Env([0, -0.2, 1], [0.3, 0.7], [\sine, \sin]) },
        step: { Env([0, 0.2, 0.2, 0.4, 0.4, 0.6, 0.6, 0.8, 0.8, 1], [0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1], [\step, \step, \step, \step, \step, \step, \step, \step, \step]) },

        firstQuarter: {
            Env(
                [0, 1],
                [1],
                \sine
            )
        },
        secondQuarter: {
            Env(
                [1, 0],
                [1],
                \sine
            )
        },
        circle: {
            var sig = Signal.newClear(100);
            sig.waveFill({ |x|
                1 - sqrt(1 - x.squared)
            }, 0, 1);
            Env.new(sig, 0.01)
        },
        ripple: {
            var sig = Signal.newClear(100);
            sig.waveFill({ |x|
                var freq = 7; // frequency of oscillation
                var decay = 3; // rate of decay
                0.5 + (0.5 * sin(freq * x * pi) * exp(decay.neg * x))
            }, 0, 1);
            Env.new(sig, 0.01)
        },
        heartBeat: {
            var sig = Signal.newClear(100);
            sig.waveFill({ |x|
                var pulse1 = exp(-200 * (x - 0.2).squared) * 0.8;
                var pulse2 = exp(-200 * (x - 0.4).squared) * 0.8;
                var baseline = 0.2;
                baseline + pulse1 + pulse2
            }, 0, 1);
            Env.new(sig, 0.01)
        },
        wobble: {
            var sig = Signal.newClear(100);
            sig.waveFill({ |x|
                var cycles = 5; // number of growth cycles
                var baseGrowth = x; // underlying trend
                var seasonalEffect = 0.05 * sin(cycles * 2pi * x); // seasonal variation
                baseGrowth + seasonalEffect
            }, 0, 1);
            Env.new(sig, 0.01)
        },
        gust: {
            var sig = Signal.newClear(100);
            sig.waveFill({ |x|
                var baseWind = 0.2 + (0.3 * x); // gradually increasing base wind
                var gust = 0.5 * exp(-30 * (x - 0.7).squared); // sudden gust
                baseWind + gust
            }, 0, 1);
            Env.new(sig, 0.01)
        },
        butterfly: {
            var sig = Signal.newClear(100);
            sig.waveFill({ |x|
                var flutterFreq = 20;
                var hoverPoint1 = exp(-50 * (x - 0.3).squared);
                var hoverPoint2 = exp(-50 * (x - 0.7).squared);
                var flutter = 0.1 * sin(flutterFreq * pi * x);
                (0.5 * x) + (0.5 * (x.sqrt)) + flutter - (0.2 * hoverPoint1) - (0.2 * hoverPoint2)
            }, 1, 0);
            Env.new(sig, 0.01)
        },
        earthquake: {
            var sig = Signal.newClear(100);
            sig.waveFill({ |x|
                var pWave = 0.3 * exp(-30 * (x - 0.2).squared) * sin(40 * pi * x);
                var sWave = 0.7 * exp(-15 * (x - 0.5).squared) * sin(20 * pi * x);
                0.5 + pWave + sWave
            }, 0, 1);
            Env.new(sig, 0.01)
        }
    );

    // Function to retrieve a custom envelope by name
    var ce = { |name|
        envLibrary[name].value
    };

    // Add a custom event type for visual events
    Event.addEventType(\customEvent, {
        var viewName = ~viewName ? \view1; // Default to view1 if not specified
        var visualEvents = views[viewName][\visualEvents];

        var event = (
            \shape: ~shape ? \circle,
            \points: ~points,  // Optional custom points array
            \numPoints: ~numPoints, // Optional number of points to generate
            \sx: ~sx ? 400,
            \sy: ~sy ? 200,
            \ex: ~ex ? 400,
            \ey: ~ey ? 0,
            \xEnv: ~xEnv ? defaultEnv,
            \yEnv: ~yEnv ? defaultEnv,
            \startSize: ~startSize ? 50,
            \endSize: ~endSize ? 100,
            \sizeEnv: ~sizeEnv ? defaultEnv,
            \startColor: ~startColor ? Color.white,
            \endColor: ~endColor ? Color.red.alpha_(0.2),
            \colorEnv: ~colorEnv ? defaultEnv,
            \rotation: ~rotation ? 0,
            \duration: ~duration ? 5,
            \envelope: ~envelope ? defaultEnv,
            \alphaEnv: ~alphaEnv ? Env([1, 1, 0], [0.0, 1], \sin),
            \fill: ~fill ? false,  // Whether to fill the shape or stroke it
            \closed: ~closed ? true,  // Whether to close the shape
            \modulation: ~modulation,  // Optional modulation settings
            \startTime: thisThread.seconds
        );
        visualEvents.add(event);
        ~type = \note;
        currentEnvironment.play;
    });
    s.waitForBoot({
        // Example patterns to add visual events to different views
        Pbind(
            \type, \customEvent,
            \viewName, \view1, // Specify the view
            \shape, \line,
		\note, Prand([0,3,6,9], inf),

		\sx, Pn(Pseries(100,20,20),inf),
		\sy, 300 - (Pkey(\note) * 20),
		\ex, 100,
		\ey, Pkey(\sy),
            \xEnv, Pfunc { ce.(\linear) },
            \yEnv, Pfunc { ce.(\linear) },
            \startSize, 20,
            \endSize, 20,
            \sizeEnv, Pfunc { ce.(\spring) },
		\startColor, Pfunc{|e|Color.hsv(e.note/12,0.5,1)},
		\endColor, Pkey(\startColor),
            \rotation, pi/2,//Pseg(Pseq([-pi, pi], inf), 80, \linear, inf),
            \modulation, (
                type: \radial,
                freq: 3,
                amp: 10,
                harmonics: 3
            ),
		// \fill, true,
            \duration, 0.8,
            \dur, 0.1,
		\latency,0.03
        ).play;
/*
        Pbind(
            \type, \customEvent,
            \viewName, \view2, // Specify the view
            \shape, \hexagon,
            \numPoints, 64,
            \sx, 300,
            \sy, 200,
            \ex, 300,
            \ey, 190,
            \xEnv, Pfunc { ce.(\anticipate) },
            \yEnv, Pfunc { ce.(\bounce) },
            \startSize, 90,
            \endSize, 10,
            \sizeEnv, Pfunc { ce.(\linear) },
            \startColor, Color.green,
            \endColor, Color.yellow,
            \rotation, Pseg(Pseq([-pi, pi], inf), 8, \linear, inf),
            \modulation, (
                type: \noise,
                freq: 1,
                amp: 18,
                harmonics: 1
            ),
            \fill, false,
            \duration, 3,
            \dur, 0.25,
            \degree, Pseg(Pseq([0, 24], inf), 8, \linear, inf),
        ).play;

        Pbind(
            \type, \customEvent,
            \viewName, \view3, // Specify the view
            \shape, \wave,
            \numPoints, 100,
            \sx, 300,
            \sy, 100,
            \ex, 300,
            \ey, 300,
            \xEnv, Pfunc { ce.(\anticipate) },
            \yEnv, Pfunc { ce.(\bounce) },
            \startSize, 70,
            \endSize, 0,
            \sizeEnv, Pfunc { ce.(\linear) },
            \startColor, Color.red,
            \endColor, Color.cyan,
            \rotation, Pseg(Pseq([-pi, pi], inf), 8, \linear, inf),
            \modulation, (
                type: \phase,
                freq: 0.2,
                amp: 30,
                harmonics: 1
            ),
            \closed, false,
            \duration, 8,
            \dur, 0.6,
            \octave, 3,
            \degree, Pseg(Pseq([0, 24], inf), 16, \linear, inf),
        ).play;

        Pbind(
            \type, \customEvent,
            \viewName, \view4, // Specify the view
            \shape, \star,
            \numPoints, 10,
            \sx, 300,
            \sy, 200,
            \ex, 500,
            \ey, 200,
            \xEnv, Pfunc { ce.(\anticipate) },
            \yEnv, Pfunc { ce.(\bounce) },
            \startSize, 50,
            \endSize, 0,
            \sizeEnv, Pfunc { ce.(\linear) },
            \startColor, Color.red,
            \endColor, Color.blue,
            \rotation, Pseg(Pseq([-pi, pi], inf), 8, \linear, inf),
            \modulation, (
                type: \noise,
                freq: 2,
                amp: 6,
                harmonics: 2
            ),
            \duration, 1,
            \dur, 0.125,
            \release, 0.1,
            \decay, 0.1,
            \sustain, 0.1,
            \octave, 3,
            \degree, Pseg(Pseq([0, 8, 16, 32], inf) + 30, 8, \linear, inf),
        ).play;
	*/
    });
)