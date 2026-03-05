(
// SYNTHESIS CONTROL UI - SuperCollider GUI Implementation
// Recreates the HTML blob visualization with UserView and sliders

var win, canvasView, sliders, valueLabels;
var params, blobSystem;
var width = 800, height = 500;
var centerX, centerY;
var lastTime, currentTime;
var makeBlob, makeBlobSystem, makeSliderGroup;

// Initialize parameters
params = Dictionary[
	\brightness -> 1.0,
	\texture -> 0.0,
	\thickness -> 0.0,
	\motion -> 0.0
];

centerX = width / 2;
centerY = height / 2;

// Blob factory - generates animated blob shapes using control points
makeBlob = { |seed|
	var obj, time = 0, noiseOffset, basePoints = 8;

	noiseOffset = seed * 1000;

	obj = (
		seed: seed,
		time: time,

		// Get control points for blob shape
		getControlPoints: {
			var points, numPoints, rotationOffset, motionAmount;

			points = List.new;

			// More texture = more control points (8 to 48)
			numPoints = basePoints + (params[\texture] * 40).floor;

			// Motion-based rotation offset for the entire shape
			motionAmount = 1 - params[\motion]; // 0=motion, 1=still
			rotationOffset = time * (1 - motionAmount) * 1.0;

			numPoints.do { |i|
				var angle, baseRadius, baseMotion, roughRandom;
				var seedVal1, seedVal2, seedVal3, randomDisplacement;
				var radius, x, y;

				angle = (i / numPoints) * 2pi + rotationOffset;

				// Base radius - constant size
				baseRadius = 70;

				// Base motion - smoothly fades in with texture
				motionAmount = params[\texture].pow(0.5) ; // Square root for smooth fade-in
				baseMotion = motionAmount * (
					sin(angle * 2 + (time * 2)) * 15 +
					cos(angle * 3 + (time * 3.5)) * 10
				);

				// Texture creates random outward displacement - ROUGH variation
				seedVal1 = sin(i * 42.9898 + (seed * 48.233)) * 0.5 + 0.5;
				seedVal2 = sin(i * 65.164 + (seed * 32.234)) * 0.5 + 0.5;
				seedVal3 = sin(i * 81.327 + (seed * 15.719)) * 0.5 + 0.5;

				// Rough combination
				roughRandom = (seedVal1 + (seedVal2 * 0.5) + (seedVal3 * 0.3)) / 1.8;
				randomDisplacement = 100 - (roughRandom * params[\texture] * 208);

				// radius = baseRadius + baseMotion + randomDisplacement;
				radius = baseRadius + randomDisplacement;

				points.add(
					(
						x: centerX + (cos(angle) * radius),
						y: centerY + (sin(angle) * radius)
					)
				);
			};

			points;
		},

		// Update animation time
		update: { |deltaTime|
			var motionAmount;
			motionAmount = 1 - params[\motion]; // Invert: 0=motion, 1=still
			time = time + (deltaTime * (1 - motionAmount) * 12.0);
		},

		// Draw blob path
		drawPath: {
			var points, startX, startY;

			points = obj.getControlPoints.();

			if (points.size < 3) {
				^nil;
			};

			// Texture controls whether we use smooth curves or straight lines
			if (params[\texture] < 0.3) {
				// Smooth - use quadratic curves
				startX = (points[0].x + points[1].x) / 2;
				startY = (points[0].y + points[1].y) / 2;
				Pen.moveTo(startX @ startY);

				points.size.do { |i|
					var next, nextNext, cpX, cpY, endX, endY;
					next = points[(i + 1) % points.size];
					nextNext = points[(i + 2) % points.size];

					cpX = next.x;
					cpY = next.y;
					endX = (next.x + nextNext.x) / 2;
					endY = (next.y + nextNext.y) / 2;

					// Pen.quadCurveTo(cpX @ cpY, endX @ endY);
					Pen.quadCurveTo(endX @ endY, cpX @ cpY);
				};
			} {
				// Textured - use straight lines
				Pen.moveTo(points[0].x @ points[0].y);
				(1..(points.size - 1)).do { |i|
					Pen.lineTo(points[i].x @ points[i].y);
				};
				Pen.lineTo(points[0].x @ points[0].y); // Close path
			};
		}
	);

	obj;
};

// BlobSystem factory - manages multiple blob instances
makeBlobSystem = {
	var obj, blobs, maxBlobs;

	maxBlobs = 20;
	blobs = Array.fill(maxBlobs, { |i| makeBlob.(i * 0.1) });

	obj = (
		blobs: blobs,

		// Get grayscale color from brightness parameter
		getColor: {
			var brightness, value;
			brightness = params[\brightness];
			value = (brightness * 255).floor / 255; // Normalize 0-1
			Color.gray(value);
		},

		// Render all blob instances
		renderer: {
			var color, numInstances, thicknessAmount;

			// Calculate how many instances to show based on thickness
			thicknessAmount = 1 - params[\thickness]; // Invert: 0=thick, 1=thin
			numInstances = max(1, (1 + ((1 - thicknessAmount) * (maxBlobs - 1))).floor);

			color = obj.getColor.();

			Pen.translate(centerX, centerY);
			Pen.rotate(currentTime * params[\motion] * 4);
			Pen.translate(centerX.neg, centerY.neg);


			// Draw instances from back to front
			numInstances.do { |i|
				var blob, normalizedIndex, spreadAmount, spreadFactor, spread;
				var alphaThickness, baseAlpha, seedVariation, alphaVariation;
				var depthFalloff, alpha, scale, rotationAngle;
				var motionAmount, motionRotation, spreadX, spreadY;

				blob = blobs[i];

				// Normalize position in instance array (0 to 1)
				normalizedIndex = i / max(1, numInstances - 1);

				// Spread: THICK instances are more spread out
				spreadAmount = params[\thickness];
				spreadFactor = spreadAmount * 2.0;
				spread = (normalizedIndex.pow(1.5)) * spreadFactor * 33.25;

				// Alpha calculation
				alphaThickness = 1 - params[\thickness];
				baseAlpha = alphaThickness * 0.5 + 0.15; // 0.15 to 0.65

				// Unique alpha per instance
				seedVariation = sin(blob.seed * 12.9898 + (blob.seed * 78.233)) * 0.5 + 0.5;
				alphaVariation = seedVariation * 0.4 + 0.6; // 0.6 to 1.0

				// Depth falloff
				depthFalloff = 1 - (normalizedIndex * 0.3);

				alpha = baseAlpha * alphaVariation * depthFalloff;

				// Scale and rotation
				scale = 1 - (normalizedIndex * 0.05);
				rotationAngle = normalizedIndex * 2pi * spreadFactor * (i+1);

				// Motion rotation
				motionAmount = 1 - params[\motion];
				motionRotation = currentTime * (1 - motionAmount) * 0.5;

				// Calculate spread offset
				spreadX = cos(rotationAngle) * spread;
				spreadY = sin(rotationAngle) * spread;

				// Apply transformations and draw
				Pen.push;

				// Translate to spread position
				Pen.translate(centerX + spreadX, centerY + spreadY);

				// Apply scale
				Pen.scale(scale, scale);

				// Apply motion rotation
				Pen.rotate(motionRotation);


				// Translate back so shape draws centered
				Pen.translate(centerX.neg, centerY.neg);

				// Draw the blob stroke
				blob.drawPath.();
				Pen.strokeColor = color.alpha_(alpha);
				Pen.width = 1;
				Pen.stroke;

				Pen.pop;
			};

		},

		// Update all blobs
		update: { |deltaTime|
			blobs.do { |blob|
				blob.update.(deltaTime);
			};
		}
	);

	obj;
};

// Helper function to create slider group
makeSliderGroup = { |parent, bounds, paramKey, labelText, minLabel, maxLabel, defaultVal|
	var container, label, valueLabel, slider;

	container = CompositeView(parent, bounds)
		.background_(Color.new(0.04, 0.04, 0.04));

	// Label and value display
	label = StaticText(container, Rect(0, 0, bounds.width - 100, 20))
		.string_(labelText)
		.stringColor_(Color.new(0.53, 0.53, 0.53))
		.font_(Font("Helvetica", 12));

	valueLabel = StaticText(container, Rect(bounds.width - 100, 0, 100, 20))
		.string_(defaultVal.asString.keep(4))
		.stringColor_(Color.white)
		.font_(Font("Helvetica", 12, true))
		.align_(\right);

	// Slider
	slider = Slider(container, Rect(0, 25, bounds.width, 30))
		.value_(defaultVal)
		.action_({ |sl|
			params[paramKey] = sl.value;
			valueLabel.string = sl.value.asString.keep(4);
		});

	// Extremes labels
	StaticText(container, Rect(0, 60, bounds.width / 2, 15))
		.string_(minLabel)
		.stringColor_(Color.new(0.33, 0.33, 0.33))
		.font_(Font("Helvetica", 10));

	StaticText(container, Rect(bounds.width / 2, 60, bounds.width / 2, 15))
		.string_(maxLabel)
		.stringColor_(Color.new(0.33, 0.33, 0.33))
		.font_(Font("Helvetica", 10))
		.align_(\right);

	sliders[paramKey] = slider;
	valueLabels[paramKey] = valueLabel;
};

// Create blob system
blobSystem = makeBlobSystem.();
lastTime = Main.elapsedTime;

// Create window
win = Window("SYNTHESIS CONTROL", Rect(100, 100, width, height + 340))
	.background_(Color.new(0.04, 0.04, 0.04))
	.front;

// Create canvas view
canvasView = UserView(win, Rect(0, 0, width, height))
	.background_(Color.black)
	.animate_(true)
	.frameRate_(60)
	.drawFunc_({
		var deltaTime;
		currentTime = Main.elapsedTime;
		deltaTime = currentTime - lastTime;
		lastTime = currentTime;

		// Update and render blob system
	blobSystem.update.(deltaTime);
	blobSystem.renderer.();
	});

// Create sliders with labels
sliders = Dictionary.new;
valueLabels = Dictionary.new;

// Create all four sliders
makeSliderGroup.(win, Rect(50, height + 20, 700, 80), \brightness,
	"DARKNESS / BRIGHTNESS", "Dark", "Bright", 1.0);

makeSliderGroup.(win, Rect(50, height + 100, 700, 80), \texture,
	"SMOOTH / TEXTURE", "Textured", "Smooth", 0.0);

makeSliderGroup.(win, Rect(50, height + 180, 700, 80), \thickness,
	"THINNESS / THICKNESS", "Thick", "Thin", 0.0);

makeSliderGroup.(win, Rect(50, height + 260, 700, 80), \motion,
	"STILLNESS / MOTION", "Motion", "Still", 0.0);

// Cleanup on window close
win.onClose_({
	canvasView.animate_(false);
	"Synthesis UI closed".postln;
});

"Synthesis Control UI loaded. Window opened.".postln;
)
