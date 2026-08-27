//------------------------------------------------------------
// screens.scd
//
// Second-screen detection. One-shot, on demand - there is no timer.
//
// Two independent signals, because neither alone is enough:
//
//  1. /sys/class/drm/card*-HDMI-A-*/status -> is a cable physically in?
//     (kernel truth, works with no X/Wayland at all)
//  2. xrandr --listmonitors -> where did the compositor actually PUT it?
//     Under XWayland outputs are named XWAYLAND0/1, so the name tells us
//     nothing - the non-zero x offset is what identifies the second screen.
//
// Returns a top-left-origin Rect for the second screen, or nil.
// NOTE: feed that Rect to Window:setTopLeftBounds(rect, 0), NOT to
// .bounds_ - the latter runs Window.flipY against the PRIMARY screen
// height and lands the window in the wrong place.
//
// Called at boot and again whenever the HDMI button is pressed - that
// press IS the re-detect. Nothing here runs on a timer: xrandr is a
// subprocess and unixCmdGetStdOut BLOCKS the language thread, the same
// thread running ~next at 33Hz, so it only ever runs when asked.
//
// Returns the probe function.
//------------------------------------------------------------

var parseMonitorLine = { |line|
	var words = line.stripWhiteSpace.split($ );
	var tok = nil;
	var result = nil;

	// the geometry token looks like: 1024/340x768/270+1280+0
	// (the output NAME is +XWAYLAND1 - upper case X, so it never matches)
	words.do { |w|
		if(tok.isNil and: { w.find("x").notNil } and: { w.find("+").notNil }, { tok = w });
	};

	if(tok.notNil, {
		var plus = tok.split($+);
		if(plus.size >= 3, {
			var wh = plus[0].split($x);
			if(wh.size >= 2, {
				result = Rect(
					plus[1].asInteger,
					plus[2].asInteger,
					wh[0].split($/)[0].asInteger,
					wh[1].split($/)[0].asInteger
				);
			});
		});
	});
	result
};

// in-process : glob + file read, no shell.
var hdmiConnected = {
	var paths = "/sys/class/drm/card*-HDMI-A-*/status".pathMatch;
	var found = false;
	paths.do { |p|
		var txt = try { File.readAllString(p) } { nil };
		if(txt.notNil and: { txt.stripWhiteSpace == "connected" }, { found = true });
	};
	found
};

// subprocess : only ever called when a cable is actually present.
var secondScreen = {
	var out = "DISPLAY=${DISPLAY:-:0} xrandr --listmonitors 2>/dev/null".unixCmdGetStdOut;
	var found = nil;
	out.split($\n).do { |line|
		var r = parseMonitorLine.(line);
		if(found.isNil and: { r.notNil } and: { r.left > 0 }, { found = r });
	};
	found
};

var probe = {
	var rect = nil;
	if(hdmiConnected.(), {
		rect = secondScreen.();
		if(rect.notNil, {
			"[screens] HDMI connected, second screen at %".format(rect).postln;
		},{
			"[screens] HDMI connected but no extended screen found (mirrored?)".postln;
		});
	},{
		"[screens] no HDMI connected".postln;
	});
	rect
};

//------------------------------------------------------------
probe;
