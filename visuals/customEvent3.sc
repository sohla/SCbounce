    (
        // Dictionary to store visual events for each view
        var views = Dictionary.new;
        var defaultEnv = Env.asr(0.01, 1, 1);
        
        // Function to create a new UserView with its own visualEvents list
        var makeView = { |name, rect|
            var visualEvents = List.new(20);
        
            // Define the updateView function for this specific view
            var updateView = {
                var now = thisThread.seconds;
        
                visualEvents.do { |event|
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
                        col = Color.hsv(
                            event[\startColor].hue.blend(event[\endColor].hue, event[\colorEnv].at(normTime)),
                            1, 1
                        );
                        col = col.alpha_(event[\alphaEnv].at(normTime));
        
                        // Draw the shape
                        Pen.width = 1;
                        Pen.rotate(event[\rotation], pos.x, pos.y);
        
                        switch (event[\shape],
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
                                Pen.width = max(1, size.squared.lincurve(1, 100000, 1, 20, 0.1));
                                Pen.line(pos - (size @ 0), pos + (size @ 0));
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
            .front
            .background_(Color.white.alpha_(1))
            .layout_(
                GridLayout.rows(
                    [makeView.(\view1, Rect(0, 0, 600, 400)), makeView.(\view2, Rect(600, 0, 600, 400))],
                    [makeView.(\view3, Rect(0, 400, 600, 400)), makeView.(\view4, Rect(600, 400, 600, 400))]
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
                \sx: ~sx ? 10,
                \sy: ~sy ? 10,
                \ex: ~ex ? 200,
                \ey: ~ey ? 200,
                \xEnv: ~xEnv ? defaultEnv,
                \yEnv: ~yEnv ? defaultEnv,
                \startSize: ~startSize ? 50,
                \endSize: ~endSize ? 100,
                \sizeEnv: ~sizeEnv ? defaultEnv,
                \startColor: ~startColor ? Color.white,
                \endColor: ~endColor ? Color.red,
                \colorEnv: ~colorEnv ? defaultEnv,
                \rotation: ~rotation ? 0,
                \duration: ~duration ? 5,
                \envelope: ~envelope ? defaultEnv,
                \alphaEnv: ~alphaEnv ? Env([0, 1, 0], [0.0, 1], \sin),
                \startTime: thisThread.seconds
            );
        
            visualEvents.add(event);
        });
        
        // Example patterns to add visual events to different views
        Pbind(
            \type, \customEvent,
            \viewName, \view4, // Specify the view
            \shape, \circle,
            \sx, 100,
            \sy, 100,
            \ex, 300,
            \ey, 300,
            \xEnv, Pfunc { ce.(\ripple) },
            \yEnv, Pfunc { ce.(\linear) },
            \startSize, 30,
            \endSize, 0,
            \sizeEnv, Pfunc { ce.(\spring) },
            \startColor, Color.red,
            \endColor, Color.blue,
            \rotation, Pseg(Pseq([-pi, pi], inf), 8, \linear, inf),
            \duration, 5,
            \dur, 0.5
        ).play;
        
        Pbind(
            \type, \customEvent,
            \viewName, \view2, // Specify the view
            \shape, \square,
            \sx, 200,
            \sy, 200,
            \ex, 400,
            \ey, 400,
            \xEnv, Pfunc { ce.(\anticipate) },
            \yEnv, Pfunc { ce.(\bounce) },
            \startSize, 40,
            \endSize, 10,
            \sizeEnv, Pfunc { ce.(\linear) },
            \startColor, Color.green,
            \endColor, Color.yellow,
            \rotation, Pseg(Pseq([-pi, pi], inf), 8, \linear, inf),
            \duration, 6,
            \dur, 0.75
        ).play;
    )




        