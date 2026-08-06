//------------------------------------------------------------
// visualCore.scd
//
// Canvases as buses.
//
// A "canvas" is a named drawing surface. Devices are ROUTED to canvases
// via ~visuals.routes, so a personality never needs to know where its
// shapes end up. One event can fan out to several canvases.
//
//   ~visuals.canvases  \name -> (view:, events:, bg:)
//   ~visuals.routes    devicePort -> [\canvasName, ...]
//   ~visuals.vdefs     devicePort -> (\shapeName -> drawFunc)
//
// Scenes are just routing-table swaps:
//   \overlay : every device -> \main        (all animations composited)
//   \grid    : device N     -> \devN        (one canvas each)
//
// Back-compat: personalities already pass `viewID: d.port`. That used to
// mean "draw into device d's view"; it now means "I am device d" and the
// router resolves it. No personality edits required.
//------------------------------------------------------------

var shapeLib = Require("shapeLib.scd");
var defaultEnv = Env([0, 1], [1], \linear);
var spacing = 4;

var core = (
	canvases: Dictionary.new,
	routes: Dictionary.new,
	vdefs: Dictionary.new,
	scene: \overlay,
	stack: nil,
	gridView: nil,
	gridCount: 0,
	latency: 0.2	// visuals are stamped this far ahead so they land with the audio
);

//------------------------------------------------------------
// draw one canvas
//------------------------------------------------------------
var drawCanvas = { |name, view|
	var c = core.canvases[name];
	if(c.notNil, {
		var now = thisThread.seconds;
		var mx = view.bounds.width / 2.0;
		var my = view.bounds.height / 2.0;

		if(core.dbgOn == true, {
			core.dbgCount = (core.dbgCount ? 0) + 1;
			if((core.dbgCount % 60) == 0, {
				postf("[draw %] bounds=% events=% now=%\n",
					name, view.bounds, c[\events].size, now.round(0.01));
				c[\events].do { |e, i|
					if(i < 2, {
						postf("    ev% start=% elapsed=% dur=%\n",
							i, e[\startTime].round(0.01),
							(now - e[\startTime]).round(0.01), e[\duration]);
					});
				};
			});
		});

		c[\events].do { |event|
			var elapsed = now - event[\startTime];
			var duration = event[\duration] ? 1;
			var normTime = 0.0;

			if(duration != 0, { normTime = elapsed / duration });

			// normTime < 0 means the event is still latency-delayed
			if((normTime >= 0.0) and: { normTime < 1.0 }, {

				var x = (event[\sx].blend(event[\ex], event[\xEnv].at(normTime)) * mx) + mx;
				var y = (event[\sy].blend(event[\ey], event[\yEnv].at(normTime)) * my) + my;
				var pos = x @ y;
				var size = event[\startSize].blend(event[\endSize], event[\sizeEnv].at(normTime));
				var width = event[\startWidth].blend(event[\endWidth], event[\widthEnv].at(normTime));
				var col = event[\startColor].blend(event[\endColor], event[\colorEnv].at(normTime));
				var points, vdef, table;

				// vdef lookup: device-local shape, then global, then legacy shapeLib
				table = core.vdefs[event[\src]];
				if(table.notNil, { vdef = table[event[\shape]] });
				if(vdef.isNil, {
					table = core.vdefs[\global];
					if(table.notNil, { vdef = table[event[\shape]] });
				});

				Pen.width = width;
				Pen.rotate(event[\rotation], pos.x, pos.y);
				Pen.fillColor = col;
				Pen.strokeColor = col;

				if(vdef.notNil, {

					// personality-defined draw function
					vdef.(event, (
						pos: pos,
						size: size,
						width: width,
						color: col,
						rotation: event[\rotation],
						normTime: normTime,
						elapsed: elapsed,
						now: now,
						bounds: view.bounds,
						view: view
					));

				},{

					// built-in shapeLib path
					if(event[\points].notNil, {
						points = event[\points];
					},{
						var shapeFunc = shapeLib[event[\shape]] ? shapeLib[\circle];
						points = shapeFunc.(pos, size, event[\numPoints] ? 32);
					});

					if(event[\modulation].notNil, {

						var modFreq = event[\modulation][\freq] ? 0.5;
						var baseModAmp = event[\modulation][\amp] ? 5;
						var modPhase = event[\modulation][\phase] ? 0;
						var modType = event[\modulation][\type] ? \radial;
						var modAmp = baseModAmp * (1.0 - event[\sizeEnv].at(normTime));
						var lfoValue = sin(2pi * modFreq * now + modPhase) * modAmp;
						var harmonics = event[\modulation][\harmonics] ? 1;

						points = points.collect { |point, i|
							var t = i / (points.size - 1);
							var vec, len, angle, modVector;

							switch(modType,
								\radial, {
									var modFactor = lfoValue * sin(harmonics * t * 2pi).abs;
									vec = point - pos;
									len = vec.rho;
									angle = vec.theta;
									len = len + modFactor;
									pos + Polar(len, angle).asPoint;
								},
								\normal, {
									vec = point - pos;
									angle = vec.theta;
									modVector = Polar(lfoValue * sin(harmonics * t * 2pi), angle + 0.5pi).asPoint;
									point + modVector;
								},
								\noise, {
									var seed = (i * 1000) + (now.floor * 50);
									var rnd = {|s| sin(s * 12345.6789).abs };
									var jitterFactor = sin(t * harmonics * 2pi);
									var jitterX = lfoValue * jitterFactor * (rnd.(seed) * 2 - 1);
									var jitterY = lfoValue * jitterFactor * (rnd.(seed + 500) * 2 - 1);
									point + (jitterX @ jitterY);
								},
								\phase, {
									var idx = (i + (lfoValue * harmonics)).wrap(0, points.size - 1);
									var idealPoint = points[idx.floor];
									var nextPoint = points[idx.ceil % points.size];
									var blend = idx - idx.floor;
									idealPoint.blend(nextPoint, blend);
								},
								{ point }
							);
						};
					});

					Pen.moveTo(points[0]);
					points[1..].do { |point| Pen.lineTo(point) };
					if(event[\closed] ? true, { Pen.lineTo(points[0]) });

					if(event[\fill] ? false, {
						if(event[\shape] == \line, { Pen.stroke },{ Pen.fill });
					},{
						Pen.stroke;
					});
				});

				Pen.rotate(event[\rotation].neg, pos.x, pos.y);
			});
		};

	});
};

//------------------------------------------------------------
// NOTE on style: core is an Event, and Dictionary:doesNotUnderstand
// AUTO-CALLS any Function stored in it, passing the Event as the first
// argument. So these are written as pseudo-methods - leading `self` arg,
// called as core.makeCanvas(...) NOT core.makeCanvas.(...).
// (Writing core.makeCanvas.(\main) calls makeCanvas(core) first, which
//  keyed a canvas by the self-referential core Event and sent the next
//  dictionary lookup into infinite recursion -> sclang segfault.)
//------------------------------------------------------------

//------------------------------------------------------------
// canvas factory
//------------------------------------------------------------
core.makeCanvas = { |self, name, frameRate = 60, action|
	var view = UserView()
		.animate_(true)
		.frameRate_(frameRate)
		.background_(Color.black);

	view.drawFunc_({ drawCanvas.(name, view) });

	if(action.notNil, {
		view.mouseUpAction_({ view.background_(Color.black) });
		view.mouseDownAction_({
			view.background_(Color.white.alpha_(0.1));
			action.();
		});
	},{
		view.mouseDownAction_({false});
		view.mouseUpAction_({false});
	});

	core.canvases[name] = (view: view, events: List.new(32));
	view
};

//------------------------------------------------------------
// scenes : routing-table swaps
//------------------------------------------------------------
core.applyScene = { |self, name|
	core.scene = name;
	core.routes.clear;

	switch(name,
		\overlay, {
			~devices.keysDo { |port| core.routes[port] = [\main] };
			if(core.stack.notNil, { core.stack.index = 0 });
		},
		\grid, {
			~devices.do { |d| core.routes[d.port] = [(\dev ++ d.index).asSymbol] };
			if(core.stack.notNil, { core.stack.index = 1 });
		},
		{ "unknown visual scene: %".format(name).warn }
	);
};

//------------------------------------------------------------
// per-personality draw functions (hot-reloaded with the p-file)
//------------------------------------------------------------
core.vdef = { |self, port, name, func|
	if(core.vdefs[port].isNil, { core.vdefs[port] = Dictionary.new });
	core.vdefs[port][name] = func;
	name
};

core.clearVdefs = { |self, port| core.vdefs[port] = nil };

//------------------------------------------------------------
// the surface : one StackLayout, page 0 = overlay, page 1 = grid.
// Lives either in the HDMI window or in the visual tab - same thing.
//------------------------------------------------------------
core.makeSurface = { |self|
	var overlay = core.makeCanvas(\main);
	var grid = View().layout_(GridLayout.new()
		.hSpacing_(spacing).vSpacing_(spacing).margins_([0,0,0,0]));
	var stack;
	var view;

	core.gridView = grid;

	view = View()
		.background_(Color.black)
		.layout_(stack = StackLayout(overlay, grid).margins_(0).spacing_(0));

	core.stack = stack;
	stack.index = if(core.scene == \grid, 1, 0);

	// the surface may be built after devices have already been detected
	// (HDMI window is created inside initGUI, i.e. after startOSCListening)
	if(~devices.notNil, { ~devices.do { |d| core.addDevice(d) } });

	core.startCull();
	view
};

//------------------------------------------------------------
// a device arrived : give it a grid canvas, re-resolve routing
//------------------------------------------------------------
core.addDevice = { |self, d|
	var name = (\dev ++ d.index).asSymbol;
	if(core.canvases[name].isNil and: { core.gridView.notNil }, {
		var canvas = core.makeCanvas(name, 60, {
			// tap a device's canvas to advance its personality (as before)
			d.personality = d.personality + 1;
			NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/loadPersonality", d.port, d.personality);
		});
		core.gridView.layout.add(canvas, 0, core.gridCount);
		core.gridCount = core.gridCount + 1;
	});
	core.applyScene(core.scene);
};

//------------------------------------------------------------
// expire events on their own clock, NOT inside drawFunc.
// (a hidden StackLayout page gets no paint events, so culling there
//  let the event lists grow without bound)
//------------------------------------------------------------
core.startCull = { |self|
	if(core.cullRout.isNil, {
		core.cullRout = Routine {
			loop {
				var now = thisThread.seconds;
				// iterate a snapshot - makeCanvas may insert keys
				core.canvases.values.do { |c|
					c[\events] = c[\events].select { |e|
						(now - e[\startTime]) < (e[\duration] ? 1)
					};
				};
				0.1.yield;
			}
		}.play(AppClock);
	});
};

//------------------------------------------------------------
// the event type
//------------------------------------------------------------
Event.addEventType(\customVisualEvent, {

	var src = ~viewID ? ~src;
	var targets = ~canvas;

	var event = (
		\src: src,
		// NOTE: ~bgColor is deliberately NOT carried. Canvas backgrounds are
		// fixed black, set once in makeCanvas. Personalities may still pass
		// bgColor: - it is silently ignored, not an error.
		\shape: ~shape ? \circle,
		\points: ~points,
		\numPoints: ~numPoints,
		\sx: ~sx ? 0.0,
		\sy: ~sy ? 0.0,
		\ex: ~ex ? 0.0,
		\ey: ~ey ? 0.0,
		\xEnv: ~xEnv ? defaultEnv,
		\yEnv: ~yEnv ? defaultEnv,
		\startWidth: ~startWidth ? 1,
		\endWidth: ~endWidth ? 1,
		\widthEnv: ~widthEnv ? defaultEnv,
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
		\fill: ~fill ? false,
		\closed: ~closed ? true,
		\modulation: ~modulation,
		\startTime: thisThread.seconds + (~vLatency ? core.latency)
	);

	// explicit \canvas overrides the router; otherwise resolve by source device
	if(targets.isNil, { targets = core.routes[src] ? [] });

	targets.asArray.do { |cn|
		var c = core.canvases[cn];
		if(c.notNil, { c[\events] = c[\events].add(event) });
	};

	// audio ALWAYS plays, even when nothing is listening visually.
	// (previously this sat inside the canvas check, so a bad viewID
	//  silently killed the note as well as the shape)
	~type = \note;
	currentEnvironment.play;
});

//------------------------------------------------------------
// OSC
//------------------------------------------------------------
OSCFunc({ |msg|
	{ core.applyScene(msg[1].asSymbol) }.defer;
},'/airkit/visualScene');

OSCFunc({ |msg|
	var d = ~devices.at(msg[1].asInteger);
	if(d.notNil, { { core.addDevice(d) }.defer });
},'/airkit/addDevice', NetAddr("127.0.0.1", NetAddr.langPort));

//------------------------------------------------------------
~visuals = core;
core;
