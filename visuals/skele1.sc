// Rotates a 2D image on X, Y, Z axes.
// Math: build R = Rz(rz) * Ry(ry) * Rx(rx), apply to the image quad (z=0),
// orthographic project. The screen mapping of (x, y, 0) is a 2x2 linear
// map A = [a bb; c d]. Pen only exposes translate/rotate/scale/skew, so
// decompose A = R(angle) * S(sx, sy) * K(kx, 0) with
//   angle = atan2(c, a),
//   sx = sqrt(a^2 + c^2),
//   sy = (a*d - bb*c) / sx,
//   kx = (a*bb + c*d) / sx^2.
// project3d() below encapsulates all of that and returns the Pen values.
//
// Glow: pre-tinted silhouette (sourceIn once at load) is layered over the
// base with 'plusLighter' additive blending; alpha driven by a Gaussian.

(
var w = 600, h = 600;
var window = Window("skele1", Rect(200, 200, w, h)).front;
var view = UserView(window, window.view.bounds);
var img = Image.open("/Users/soha0008/Develop/SuperCollider/Projects/AirKit/visuals/skele.png");
var tintColor = Color(0.9, 0.5, 1.0);
var tintImg = Image.color(64, 64, tintColor);
var glowImg = img.copy;
var scaleFactor = 0.55;
var iw = img.width * scaleFactor;
var ih = img.height * scaleFactor;
var fps = 40;
var period = fps * 0.3;
var scaleNoiseStddev = 0.009;

// Box-Muller: one draw from N(0, stddev^2).
var randn = {|stddev = 1|
	sqrt(-2 * log(1.0.rand.max(1e-10))) * cos(2pi * 1.0.rand) * stddev
};

// Returns an Event with the four Pen operations needed to draw a z=0
// quad under a Rz*Ry*Rx rotation, centered at `center` (a Point).
var project3d = {|rx, ry, rz, center|
	var cxr = cos(rx), sxr = sin(rx);
	var cyr = cos(ry), syr = sin(ry);
	var czr = cos(rz), szr = sin(rz);
	var a  = czr * cyr;
	var bb = (czr * syr * sxr) - (szr * cxr);
	var c  = szr * cyr;
	var d  = (szr * syr * sxr) + (czr * cxr);
	var sx = ((a*a) + (c*c)).sqrt.max(1e-6);
	(
		translate: center,
		angle: atan2(c, a),
		scale: sx @ (((a*d) - (bb*c)) / sx),
		skew: ((a*bb) + (c*d)) / (sx*sx)
	)
};

glowImg.draw({
	Pen.drawImage(glowImg.bounds, tintImg, tintImg.bounds, 'sourceIn');
});

window.background = Color.black;
view.background = Color.black;
view.clearOnRefresh = true;
view.frameRate = fps;
view.animate = true;

view.drawFunc = {|v|
	var b = v.bounds;
	var t = v.frame;
	var rect = Rect(iw.neg * 0.5, ih.neg * 0.5, iw, ih);
	var tMod = t % period;
	var sigma = period * 0.2;
	var gauss = exp(-1 * ((tMod - (period * 0.5)) / sigma).squared);
	var xform = project3d.(t * 0.013, t * 0.023, t * 0.007, (b.width * 0.5) @ (b.height * 0.5));

	Pen.push;
	Pen.translate(xform[\translate].x, xform[\translate].y);
	Pen.rotate(xform[\angle]);
	Pen.scale(
		xform[\scale].x * (1 + randn.(scaleNoiseStddev)),
		xform[\scale].y * (1 + randn.(scaleNoiseStddev))
	);
	Pen.skew(xform[\skew], 0);
	img.drawInRect(rect);
	glowImg.drawInRect(rect, glowImg.bounds, 'plusLighter', gauss);
	Pen.pop;
};

window.onClose = {
	view.animate = false;
	img.free;
	tintImg.free;
	glowImg.free;
};
)
