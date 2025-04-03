    (
        // Dictionary to store visual events for each view
        var views = Dictionary.new;
        var defaultEnv = Env.asr(0.01, 1, 1);
        
        // Function to create a new UserView with its own visualEvents list
        var makeView = { |name, rect|
            var visualEvents = List.new(20);
        
            // Define the updateView function for this specific view
            var updateView = {|view|
                var now = thisThread.seconds;

                views[name][\visualEvents].do { |event|
                    var elapsed = 0.0, normTime = 0.0, envVal = 1.0;
                    var shape = \circle, pos = 10@10, size = 50.0, col = Color.white;
        
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
                        pos = x @ y;
        
                        // Calculate size, color, and other properties
                        size = event[\startSize].blend(event[\endSize], event[\sizeEnv].at(normTime));
                        // col = Color.hsv(
                        //     event[\startColor].hue.blend(event[\endColor].hue, event[\colorEnv].at(normTime)),
                        //     1, 1
                        // );
                        col = event[\startColor].blend(event[\endColor], event[\colorEnv].at(normTime));
                        col = col.alpha_(event[\alphaEnv].at(normTime));
        
                        // Draw the shape
                        // Pen.width = 1;
                        Pen.width = max(1, size.squared.lincurve(1,50000, 1, 20, 0.1));
                        Pen.rotate(event[\rotation], pos.x, pos.y);
                        Pen.fillColor = col;
                        Pen.strokeColor = col;
        
                        switch (event[\shape],
                            \circle, {
                                // Pen.fillOval(Rect.aboutPoint(pos, size, size));
                                Pen.strokeOval(Rect.aboutPoint(pos, size, size));
                            },
                            \square, {
                                // Pen.fillRect(Rect.aboutPoint(pos, size, size));
                                Pen.strokeRect(Rect.aboutPoint(pos, size, size));
                            },
                            \line, {
                                // Pen.width = max(1, size.squared.lincurve(1, 100000, 1, 20, 0.1));
                                Pen.moveTo(pos - (size @ 0));
                                Pen.lineTo(pos + (size @ 0));
                                Pen.stroke;
                            },
                            \triangle, { // Equilateral triangle
                                var height = size * sqrt(3) / 2;
                                var points = [
                                    pos + (0 @ (height.neg / 1)), // Top point
                                    pos + ((size.neg / 1) @ (height / 2)), // Bottom-left
                                    pos + ((size / 1) @ (height / 2)) // Bottom-right
                                ];
                                Pen.moveTo(points[0]);
                                Pen.lineTo(points[1]);
                                Pen.lineTo(points[2]);
                                Pen.lineTo(points[0]); // Close the triangle
                                Pen.fill;
                            },
                            \star, { // 5-pointed star
                                var innerRatio=0.5, rotation=0;
                                var points = Array.fill(10, { |i|
                                    var angle = (i * pi / 5) + rotation; // Alternate between inner and outer points
                                    var radius = if(i % 2 == 0, { size }, { size * innerRatio });

                                    // Return a Point with x @ y coordinates
                                    pos + (radius * cos(angle) @ (radius * sin(angle)))
                                });

                                // Move to first point
                                Pen.moveTo(points[0]);

                                // Draw lines to all other points
                                points.do { |point, i|
                                    if (i > 0) { Pen.lineTo(point) };
                                };

                                // Close the path by connecting back to the first point
                                Pen.lineTo(points[0]);
                                Pen.stroke;
                            },
                            \hexagon, { // Regular hexagon

                                // Create array of points for a regular hexagon
                                var points = Array.fill(6, { |i|
                                    var angle = (i * (2pi / 6)) + 0;

                                    // Return a Point with x @ y coordinates
                                    pos + ((size * cos(angle)) @ (size * sin(angle)))
                                });

                                // Start at the first vertex, not at the center
                                Pen.moveTo(points[0]);

                                // Draw lines to all other vertices
                                points[1..].do { |point|
                                    Pen.lineTo(point);
                                };

                                // Close the path by connecting back to the first point
                                Pen.lineTo(points[0]);
                                Pen.stroke;

                            },
                            \cross, { // Cross shape

                                // If thickness is not specified, default to size/3
                                var thickness = thickness ? (size / 3);
                                var halfThick = thickness / 2;

                                // Calculate the eight points of the cross (clockwise from top-left of vertical bar)
                                var points = [
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

                                // Draw the cross
                                Pen.moveTo(points[0]);
                                points[1..].do { |point|
                                    Pen.lineTo(point);
                                };
                                // Close the path
                                Pen.lineTo(points[0]);
                                Pen.fill;
                            },
                            \wave, { // Sine wave
                                var points = Array.fill(100, { |i|
                                    var x = pos.x + (i / 100 * size * 2) - size; // Map x across the size
                                    var y = pos.y + (sin(i / 100 * 2pi) * (size / 2));
                                    x @ y
                                });
                                Pen.moveTo(points[0]);
                                points.do { |point, i|
                                    if (i > 0) { Pen.lineTo(point) };
                                };
                                Pen.stroke;
                            },
                            \leaf,{
                                var numPoints = 10;
                                var width = width ? (size / 3);

                                var points = Array.fill(numPoints * 2 + 1, { |i|
                                    var t;
                                    var x, y;

                                    if (i <= numPoints) {
                                        // First half - going from base to tip along right edge
                                        t = i / numPoints;
                                        x = pos.x + (size * t);
                                        // Create curved edge - peak in the middle, tapering at ends
                                        y = pos.y + (width * sin(t * pi) * (0.5 + (sin(t * pi * 0.2) * 0.5)));
                                    } {
                                        // Second half - coming back from tip to base along left edge
                                        t = (numPoints * 2 - i) / numPoints;
                                        x = pos.x + (size * t);
                                        // Mirror the right edge, but with slight asymmetry
                                        y = pos.y - (width * sin(t * pi) * (0.4 + (sin(t * pi * 0.2) * 0.5)));
                                    };

                                    Point(x, y);
                                });

                                // Draw the leaf outline
                                Pen.moveTo(points[0]);
                                points[1..].do { |point|
                                    Pen.lineTo(point);
                                };
                                Pen.stroke;
                            },

                            \spiral, {
                                var maxRadius = size, turns=3, startRadius=0;
                                var numPoints = 100 * turns; // More points for smoother spiral
                                var points = Array.fill(numPoints, { |i|
                                    var progress = i / (numPoints - 1); // 0 to 1
                                    var angle = progress * turns * 2pi; // Angle increases with each point
                                    var radius = startRadius + ((maxRadius - startRadius) * progress); // Radius increases linearly

                                    // Convert polar coordinates to Cartesian
                                    var x = pos.x + (radius * cos(angle));
                                    var y = pos.y + (radius * sin(angle));

                                    x @ y
                                });

                                // Draw the spiral
                                Pen.moveTo(points[0]);
                                points[1..].do { |point|
                                    Pen.lineTo(point);
                                };
                                Pen.stroke;
                            }
                        );
        
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
                \alphaEnv: ~alphaEnv ? Env([0, 1, 0], [0.0, 1], \sin),
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
            \shape, \circle,
            \sx, 500,
            \sy, 200,
            \ex, 100,
            \ey, 200,
            \xEnv, Pfunc { ce.(\ripple) },
            \yEnv, Pfunc { ce.(\linear) },
            \startSize, 110,
            \endSize, 0,
            \sizeEnv, Pfunc { ce.(\spring) },
            \startColor, Color.red,
            \endColor, Color.blue,
            \rotation, Pseg(Pseq([-pi, pi], inf), 8, \linear, inf),
            \duration, 12,
            \dur, 0.4,
            \note, Pseg(Pseq([0, 12], inf), 8, \linear, inf),
        ).play;
        
        Pbind(
            \type, \customEvent,
            \viewName, \view2, // Specify the view
            \shape, \square,
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
            \duration, 3,
            \dur, 0.25,
            \degree, Pseg(Pseq([0, 24], inf), 8, \linear, inf),

        ).play;


       Pbind(
            \type, \customEvent,
            \viewName, \view3, // Specify the view
            \shape, \triangle,
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
            \duration, 8,
            \dur, 0.6,
            \octave, 3,
            \degree, Pseg(Pseq([0, 24], inf), 16, \linear, inf),


        ).play;

       Pbind(
            \type, \customEvent,
            \viewName, \view4, // Specify the view
            \shape, \cross,
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
            \duration, 1,
            \dur, 0.125,
            \release, 0.1,
            \decay, 0.1,
            \sustain, 0.1,
            \octave, 3,
            \degree, Pseg(Pseq([0, 8, 16, 32], inf) + 30, 8, \linear, inf),
            

        ).play;
        });
    )




        