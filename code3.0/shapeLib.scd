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
    // triangle: { |pos, size, numPoints=3|
    //     var height = size * sqrt(3) / 2;
    //     [
    //         pos + (0 @ (height.neg / 1)), // Top point
    //         pos + ((size.neg / 1) @ (height / 2)), // Bottom-left
    //         pos + ((size / 1) @ (height / 2)) // Bottom-right
    //     ];
    // },

    triangle: { |pos, size, numPoints=3|
        var points = Array.newClear(numPoints);
        var height = size * sqrt(3) / 2;
        var i = 0;
        
        
        // Define the three vertices of the equilateral triangle
        var vertices = [
            pos + (0 @ (height.neg)),               // Top vertex
            pos + (size @ (height / 2)),            // Bottom-right vertex
            pos + (size.neg @ (height / 2))         // Bottom-left vertex
        ];
        // Ensure numPoints is at least 3
        numPoints = numPoints.max(3);
        
        // Distribute points around the triangle perimeter
        numPoints.do { |pointIndex|
            var t = pointIndex / numPoints;  // 0 to 1 (excluding 1)
            var scaledT = t * 3;  // Scale to 0-3 range
            var edgeIndex = scaledT.floor;  // Which edge (0, 1, or 2)
            var edgeT = scaledT - edgeIndex;  // Position along that edge (0 to 1)
            
            var startVertex = vertices[edgeIndex];
            var endVertex = vertices[(edgeIndex + 1) % 3];  // Wrap around to vertex 0 after vertex 2
            
            points[i] = startVertex.blend(endVertex, edgeT);
            i = i + 1;
        };
        
        points;
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

shapeLib