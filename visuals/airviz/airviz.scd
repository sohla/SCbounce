//------------------------------------------------------------
// airviz.scd
//
// Ships \customVisualEvent to the GLES2 renderer over OSC.
//
// NON-INVASIVE BY DESIGN. This does not edit visualCore.scd. It captures
// the existing \customVisualEvent function and installs a wrapper that
// sends the OSC message and then calls the original, so the Qt canvases
// keep working exactly as before and both renderers see every event.
// That is what makes an A/B possible on the same performance, and it
// means a personality airviz cannot draw yet still draws under Qt.
//
// Load it AFTER main.sc:
//     Require("visuals/airviz/airviz.scd");
// and run the renderer alongside:
//     visuals/airviz/airviz -v
//
// It is idempotent - re-loading replaces the wrapper rather than stacking
// a second one on top of the first.
//
// WHAT IS NOT SENT, and why the C side falls back to a circle:
// a personality's own ~vdef is a SuperCollider closure. It cannot cross a
// socket. Marks built from library shapes go over intact; custom points
// funcs need the personality to also set \points; multi-subpath DRAW funcs
// (bongo1's \membrane, magicWand's \comb, metal1's \sheetFrame) have no
// representation on this wire at all. See README.md.
//------------------------------------------------------------

var addr = NetAddr("127.0.0.1", 57130);

// Read explicitly out of topEnvironment, NOT as ~visuals. An event type
// function body runs inside currentEnvironment.use, so in there `~visuals`
// would read the EVENT's key of that name - which is nil - rather than the
// global visualCore. Everything the wrapper needs is captured into a local
// var here, at load time, for exactly that reason.
var visuals = topEnvironment[\visuals];
var inner;

// must match the enums in airviz.h
var envCurve = 5;
var envKinds = (
	linear: 0, lin: 0, step: 0, hold: 0,
	sine: 1, sin: 1,
	welch: 2, wel: 2,
	squared: 3, sqr: 3,
	cubed: 4, cub: 4
);
var modKinds = (radial: 1, normal: 2, noise: 3, phase: 4);

var warnedDraw = IdentitySet.new;

// Env -> [kind, curve]. A bare Float here is the classic Pbind trap
// (Env embedInStream'd into a value) and resolves to linear rather than
// throwing, because a wrong envelope is better than a dead renderer.
var envSpec = { |e|
	var c, out = [0, 0.0];
	if(e.isKindOf(Env), {
		c = e.curves;
		if(c.isSequenceableCollection, { c = c.first });
		out = case
			{ c.isNumber }                          { [envCurve, c.asFloat] }
			{ (c == \exponential) or: { c == \exp } } { [envCurve, -4.0] }
			{ envKinds[c].notNil }                  { [envKinds[c], 0.0] }
			{ true }                                { [0, 0.0] };
	});
	out
};

var colSpec = { |c|
	var k = c ? Color.white;
	[k.red.asFloat, k.green.asFloat, k.blue.asFloat, k.alpha.asFloat]
};

// big-endian IEEE754 float32, which is what an OSC blob of points is
var f32be = { |f|
	var i = f.asFloat.as32Bits;
	[(i >> 24) bitAnd: 255, (i >> 16) bitAnd: 255, (i >> 8) bitAnd: 255, i bitAnd: 255]
};

// Absolute canvas points -> unit space, so the renderer can still scale
// them by the size envelope. Centring on the centroid and dividing by
// startSize is exact for any shape of the form pos + f(size) - which is
// every entry in vdefLib - and an approximation for anything else.
var unitPoints = { |pts, size|
	var n = pts.size, cx = 0.0, cy = 0.0, s = size.abs.max(0.0001);
	var bytes = List.new(n * 8);
	var push = { |f| f32be.(f).do { |b| bytes.add(if(b > 127, { b - 256 }, { b })) } };
	pts.do { |p| cx = cx + p.x; cy = cy + p.y };
	cx = cx / n; cy = cy / n;
	pts.do { |p| push.((p.x - cx) / s); push.((p.y - cy) / s) };
	Int8Array.newFrom(bytes.asArray)
};

var send = {
	var dur = ~duration ? 0.5;
	var size0 = (~startSize ? 50).asFloat;
	var mod = ~modulation;
	var modType = 0, modFreq = 0.5, modAmp = 5.0, modPhase = 0.0, modHarm = 1.0;
	var shape = (~shape ? \circle).asSymbol;
	var xe = envSpec.(~xEnv), ye = envSpec.(~yEnv);
	var se = envSpec.(~sizeEnv), we = envSpec.(~widthEnv), ce = envSpec.(~colorEnv);
	var c0 = colSpec.(~startColor), c1 = colSpec.(~endColor ? Color.red.alpha_(0.2));
	var p0 = 0.0, p1 = 0.0;
	var msg;

	if(mod.notNil, {
		modType  = modKinds[mod[\type] ? \radial] ? 1;
		modFreq  = (mod[\freq] ? 0.5).asFloat;
		modAmp   = (mod[\amp] ? 5).asFloat;
		modPhase = (mod[\phase] ? 0).asFloat;
		modHarm  = (mod[\harmonics] ? 1).asFloat;
		p0 = (mod[\arcSpan] ? 0).asFloat;
		p1 = (mod[\arcStart] ? 0).asFloat;
	});

	msg = [
		"/av/ev",
		(~viewID ? ~src ? 0).asInteger,
		shape.asString,
		(~vLatency ? (visuals !? { visuals.latency }) ? 0.2).asFloat,
		if(dur == inf, { -1.0 }, { dur.asFloat }),
		(~sx ? 0).asFloat, (~sy ? 0).asFloat, (~ex ? 0).asFloat, (~ey ? 0).asFloat,
		xe[0], xe[1], ye[0], ye[1],
		size0, (~endSize ? 50).asFloat, se[0], se[1],
		(~startWidth ? 1).asFloat, (~endWidth ? 1).asFloat, we[0], we[1],
		c0[0], c0[1], c0[2], c0[3],
		c1[0], c1[1], c1[2], c1[3],
		ce[0], ce[1],
		(~rotation ? 0).asFloat,
		if(~fill ? false, 1, 0),
		if(~closed ? true, 1, 0),
		(~numPoints ? 0).asInteger,
		modType, modFreq, modAmp, modPhase, modHarm,
		p0, p1
	];

	if(~points.notNil and: { ~points.size > 1 }, {
		msg = msg.add(unitPoints.(~points, size0));
	});

	addr.sendMsg(*msg);

	// a shape that is neither a library name nor an explicit point list is
	// a personality vdef, which cannot cross the wire. Say so once.
	if(~points.isNil and: {
		#[\circle,\arc,\blobby,\square,\triangle,\hexagon,\star,\cross,
		  \line,\wave,\spiral,\leaf].includes(shape).not
	} and: { warnedDraw.includes(shape).not }, {
		warnedDraw.add(shape);
		"[airviz] % is a personality vdef - not sendable, airviz draws a circle"
			.format(shape).warn;
	});
};

//------------------------------------------------------------
// Wrap, once.
//
// The original is stashed in topEnvironment the FIRST time only, so
// re-running this file replaces the wrapper rather than nesting one inside
// the last - which would send every event twice, then four times, then
// eight, and look like a renderer bug rather than a reload bug.
//------------------------------------------------------------
if(topEnvironment[\airvizInner].isNil, {
	topEnvironment[\airvizInner] = Event.eventTypes[\customVisualEvent];
});
inner = topEnvironment[\airvizInner];

// The original is what re-types the event to \note and plays it
// (visualCore.scd:531). Wrapping a nil would therefore silence the
// instrument, which is the one failure mode not worth risking - so refuse
// to install rather than install something broken.
if(inner.isNil, {
	"[airviz] \\customVisualEvent is not defined - load main.sc first. NOT installed.".error;
},{
	Event.addEventType(\customVisualEvent, { |server|
		// A malformed event must never take the audio down with it: the
		// original is called whatever happens in here.
		try { send.() } { |err| "[airviz] send failed: %".format(err.errorString).warn };
		inner.value(server);
	});
	"[airviz] wrapper installed -> %:%".format(addr.hostname, addr.port).postln;
});

addr;
