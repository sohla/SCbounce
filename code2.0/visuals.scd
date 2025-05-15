var spacing = 2;

var func = {|v|
	Pen.fillColor = Color.grey(0.0,  0.02);
	Pen.addRect(Window.screenBounds);
	Pen.fill;

    Pen.use{
        10.do{
            Color.hsv(rrand(0.0, 0.999), rrand(0.2, 0.7), 1, 1).set;
			Pen.addArc((Window.screenBounds.width.rand)@(Window.screenBounds.height.rand), 5.exprand(150), 2pi.rand, pi);
            Pen.perform([\stroke, \fill].choose);
        }
    }
};


var views = Dictionary.new;

// var visualEvents = [];

var xxvisuals = {|d|
	UserView()
	.mouseDownAction_({false})
	.mouseUpAction_({false})
	.background_(Color.black.alpha_(0.8))
	.drawFunc_(func)
	.clearOnRefresh_(false)
	.animate_(true)
	.frameRate_(60)
	.mouseDownAction_({false});
};


//////////////////////


    // Function to generate shape points
    var shapeLib = (
        circle: { |pos, size, numPoints=64|
            Array.fill(numPoints, { |i|
                var angle = i / numPoints * 2pi;
                pos + (Polar(size, angle).asPoint);
            });
        },
        square: { |pos, size, numPoints=4|
            var points = Array.newClear(numPoints);
            var pointsPerSide, i = 0;
            
            // Function to create points along one side
            var sidePoints = { |start, end, count|
                var pts = Array.newClear(count);
                count.do { |j|
                    var t = j / (count - 1);  // Interpolation factor (0 to 1)
                    pts[j] = start.blend(end, t);
                };
                pts;
            };
            
            // Define the four corners
            var topLeft = pos + (size.neg @ size.neg);
            var topRight = pos + (size @ size.neg);
            var bottomRight = pos + (size @ size);
            var bottomLeft = pos + (size.neg @ size);
            
            // Ensure numPoints is at least 4 and a multiple of 4
            pointsPerSide = (numPoints / 4).floor.max(1);
            numPoints = pointsPerSide * 4;
            
            // Create points for each side
            [
                sidePoints.(topLeft, topRight, pointsPerSide),      // Top side
                sidePoints.(topRight, bottomRight, pointsPerSide),  // Right side
                sidePoints.(bottomRight, bottomLeft, pointsPerSide), // Bottom side
                sidePoints.(bottomLeft, topLeft, pointsPerSide)     // Left side
            ].flat.do { |pt|
                points[i] = pt;
                i = i + 1;
            };
            
            points;
        },   

        line: { |pos, size, numPoints=2|
            var points = Array.newClear(numPoints);
            
            // Ensure numPoints is at least 2
            numPoints = numPoints.max(2);
            
            // Create evenly spaced points along the line
            numPoints.do { |i|
                var t = i / (numPoints - 1);  // Interpolation factor (0 to 1)
                var startPoint = pos - (size @ 0);  // Left endpoint
                var endPoint = pos + (size @ 0);    // Right endpoint
                
                // Linear interpolation between start and end points
                points[i] = startPoint.blend(endPoint, t);
            };
            
            points;
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


////////////////////


   // Function to create a new UserView with its own visualEvents list
    var visuals = { |d|
    // var makeView = { |name, rect|
        var visualEvents = List.new(20);

        // Define the updateView function for this specific view
        var updateView = {|view|
            var now = thisThread.seconds;

            // visualEvents.do { |event|
            views[d.port][\visualEvents].do { |event|
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
                    Pen.width = 2;//max(1, size.squared.lincurve(1, 50000, 1, 20, 0.1));
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

        views[d.port] = (view: view, visualEvents: visualEvents);
		// d.ip.postln;
        // view.bounds = rect;
        view;
    };









// var visuals = {|d|
// 	View().layout_(GridLayout.rows(
// 		[
// 			visual.(),
// 			visual.(),
// 		],
// 		[
// 			visual.(),
// 			visual.(),
// 		],
// ).hSpacing_(spacing).vSpacing_(spacing).margins_([spacing,spacing,spacing,spacing]))
// };

// var defaultEnv = Env.asr(0.01, 1, 1);
var defaultEnv = Env([0, 1], [1], \linear);

Event.addEventType(\customEvent, {
        var viewID = ~viewID ? 8888; // Default to view1 if not specified
        var visualEvents = views[viewID][\visualEvents];

        var event = (
            \shape: ~shape ? \circle,
            \points: ~points,  // Optional custom points array
            \numPoints: ~numPoints, // Optional number of points to generate
            \sx: ~sx ? 300,
            \sy: ~sy ? 450,
            \ex: ~ex ? 300,
            \ey: ~ey ? 100,
            \xEnv: ~xEnv ? defaultEnv,
            \yEnv: ~yEnv ? defaultEnv,
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
		// event.postln;
        visualEvents.add(event);
        ~type = \note;
        currentEnvironment.play;
    });


visuals;