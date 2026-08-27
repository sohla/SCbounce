//------------------------------------------------------------
// vdefLib.scd
//
// The shared shape library. Registered by visualCore into
// core.vdefs[\global], so every entry here is reachable from any
// personality as   shape: \name
//
// Every entry has the same signature as a personality's own ~vdef:
//
//     { |ev, c| ... }
//
//   ev : the raw event   (numPoints, fill, modulation, start*/end* ...)
//   c  : resolved values (pos, size, width, color, rotation, normTime,
//                         elapsed, now, bounds, view, render)
//
// Returning an Array of Points makes it a POINTS FUNC, so the core
// applies \modulation, \closed and \fill to it. That is what everything
// here does - none of these touch Pen.
//
// A personality can shadow any of these by registering the same name
// with ~vdef : device-local is looked up before \global.
//------------------------------------------------------------

var lib = ();

//------------------------------------------------------------
// closed round forms
//------------------------------------------------------------
lib[\circle] = { |ev, c|
	var n = ev[\numPoints] ? 32;
	var pos = c[\pos];
	var size = c[\size];
	Array.fill(n, { |i| pos + Polar(size, i / n * 2pi).asPoint })
};

// arc : circle with a start and a span. span 2pi is a circle, which is
// why this subsumes \circle rather than duplicating it.
lib[\arc] = { |ev, c|
	var n = ev[\numPoints] ? 32;
	var mod = ev[\modulation] ? ();
	var span = mod[\arcSpan] ? 2pi;
	// centred on +x by default, so \rotation swings it to where it belongs
	var start = mod[\arcStart] ? (span.neg * 0.5);
	var pos = c[\pos];
	var size = c[\size];
	Array.fill(n, { |i| pos + Polar(size, start + (i / (n - 1) * span)).asPoint })
};

lib[\blobby] = { |ev, c|
	var n = ev[\numPoints] ? 64;
	var pos = c[\pos];
	var size = c[\size];
	Array.fill(n, { |i|
		var angle = i / n * 2pi;
		var radius = size * (0.8 + (0.3 * sin(angle * 3)) + (0.15 * cos(angle * 5)));
		pos + (radius * cos(angle) @ (radius * sin(angle)))
	})
};

//------------------------------------------------------------
// polygons
//------------------------------------------------------------
lib[\square] = { |ev, c|
	var numPoints = ev[\numPoints] ? 32;
	var pos = c[\pos];
	var size = c[\size];
	var points, pointsPerSide, i = 0;
	var sidePoints = { |start, end, count|
		Array.fill(count, { |j| start.blend(end, j / (count - 1)) })
	};
	var topLeft = pos + (size.neg @ size.neg);
	var topRight = pos + (size @ size.neg);
	var bottomRight = pos + (size @ size);
	var bottomLeft = pos + (size.neg @ size);

	// max(2), not max(1) : sidePoints divides by (count - 1), so a single
	// point per side put a nan through every corner
	pointsPerSide = (numPoints / 4).floor.max(2);
	points = Array.newClear(pointsPerSide * 4);

	[
		sidePoints.(topLeft, topRight, pointsPerSide),
		sidePoints.(topRight, bottomRight, pointsPerSide),
		sidePoints.(bottomRight, bottomLeft, pointsPerSide),
		sidePoints.(bottomLeft, topLeft, pointsPerSide)
	].flat.do { |pt| points[i] = pt; i = i + 1 };

	points
};

lib[\triangle] = { |ev, c|
	var numPoints = (ev[\numPoints] ? 32).max(3);
	var pos = c[\pos];
	var size = c[\size];
	var height = size * sqrt(3) / 2;
	var vertices = [
		pos + (0 @ height.neg),
		pos + (size @ (height / 2)),
		pos + (size.neg @ (height / 2))
	];
	Array.fill(numPoints, { |i|
		var t = i / numPoints * 3;
		var edge = t.floor;
		vertices[edge].blend(vertices[(edge + 1) % 3], t - edge)
	})
};

lib[\hexagon] = { |ev, c|
	var n = ev[\numPoints] ? 6;
	var pos = c[\pos];
	var size = c[\size];
	Array.fill(n, { |i|
		var angle = i * (2pi / n);
		pos + ((size * cos(angle)) @ (size * sin(angle)))
	})
};

// default 32, not 10 : drawCanvas always passed 32 regardless of a shape's
// own default, so 32 is what visual1.sc has always drawn. At 32 the i*pi/5
// angle wraps several times, which is the look, not a bug.
lib[\star] = { |ev, c|
	var n = ev[\numPoints] ? 32;
	var innerRatio = 0.5;
	var pos = c[\pos];
	var size = c[\size];
	Array.fill(n, { |i|
		var angle = i * pi / 5;
		var radius = if(i % 2 == 0, { size }, { size * innerRatio });
		pos + ((radius * cos(angle)) @ (radius * sin(angle)))
	})
};

lib[\cross] = { |ev, c|
	var pos = c[\pos];
	var size = c[\size];
	var halfThick = size / 6;
	[
		Point(pos.x - halfThick, pos.y - size),
		Point(pos.x + halfThick, pos.y - size),
		Point(pos.x + halfThick, pos.y - halfThick),
		Point(pos.x + size, pos.y - halfThick),
		Point(pos.x + size, pos.y + halfThick),
		Point(pos.x + halfThick, pos.y + halfThick),
		Point(pos.x + halfThick, pos.y + size),
		Point(pos.x - halfThick, pos.y + size),
		Point(pos.x - halfThick, pos.y + halfThick),
		Point(pos.x - size, pos.y + halfThick),
		Point(pos.x - size, pos.y - halfThick),
		Point(pos.x - halfThick, pos.y - halfThick)
	]
};

//------------------------------------------------------------
// open forms
//------------------------------------------------------------
lib[\line] = { |ev, c|
	var n = (ev[\numPoints] ? 32).max(2);
	var pos = c[\pos];
	var size = c[\size];
	var a = pos - (size @ 0);
	var b = pos + (size @ 0);
	Array.fill(n, { |i| a.blend(b, i / (n - 1)) })
};

lib[\wave] = { |ev, c|
	var n = (ev[\numPoints] ? 64).max(2);
	var pos = c[\pos];
	var size = c[\size];
	Array.fill(n, { |i|
		var t = i / (n - 1);
		(pos.x + (t * size * 2) - size) @ (pos.y + (sin(t * 2pi) * (size / 2)))
	})
};

lib[\spiral] = { |ev, c|
	var n = (ev[\numPoints] ? 100).max(2);
	var turns = 3;
	var pos = c[\pos];
	var size = c[\size];
	Array.fill(n, { |i|
		var progress = i / (n - 1);
		var angle = progress * turns * 2pi;
		pos + Polar(size * progress, angle).asPoint
	})
};

lib[\leaf] = { |ev, c|
	var n = ((ev[\numPoints] ? 20) / 2).floor.max(2);
	var pos = c[\pos];
	var size = c[\size];
	var width = size / 3;
	var points = Array.new((n * 2) + 1);

	(n + 1).do { |i|
		var t = i / n;
		points = points.add(
			(pos.x + (size * t))
			@ (pos.y + (width * sin(t * pi) * (0.5 + (sin(t * pi * 0.2) * 0.5))))
		);
	};
	n.do { |i|
		var t = (n - i - 1) / n;
		points = points.add(
			(pos.x + (size * t))
			@ (pos.y - (width * sin(t * pi) * (0.4 + (sin(t * pi * 0.2) * 0.5))))
		);
	};
	points
};

lib
