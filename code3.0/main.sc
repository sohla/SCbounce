(

var staker;
var personalityController = Require("personalityController.scd");
var oscController = Require("oscController.scd");
var visualCore = Require("visualCore.scd");
var hdmiRect = Require("screens.scd").();
var specsView;

var midiCC;
var labels;
var z;

// With a second screen the visuals get their own window on it and there
// is no visual tab. Without one, the visual tab holds the same surface.
var stack = {
	var deviceView = Require("deviceView.scd");
	var controlView = Require("controlView.scd");
	var systemView = Require("systemView.scd");
	var pages = [deviceView.()];

	labels = ["device"];

	if(hdmiRect.isNil, {
		var visualView = Require("visualView.scd");
		pages = pages.add(visualView.());
		labels = labels.add("visual");
	});

	pages = pages ++ [controlView.(), systemView.()];
	labels = labels ++ ["control", "system"];

	View().layout_(staker = StackLayout(*pages));
};

var tabButton = {|i|
	UserView()
	.background_( if(i==0,Color.black.lighten(0.25),Color.black))
	.mouseDownAction_({|but|
		but.parent.children.do({|ab,i|
			if(but != ab, {
				ab.backColor_(Color.black);
			},{
				ab.backColor_(Color.black.lighten(0.25));
				staker.index = i;
			});
		},{});
	})
	.drawFunc_({|v|Pen.stringAtPoint(labels[i], (v.bounds.width-45/2)-40@25, Font(size:30), Color.white.darken(0.75))})
	.animate_(false)
};

var stackView = stack.();

var tabs = {
	View().layout_(HLayout(*labels.size.collect(tabButton)).spacing_(2).margins_(0)).maxHeight_(100);
};

var mainView = VLayout(
		tabs.(),
		stackView
).spacing_(4).margins_(0);

var shutdown = {
	midiCC.free;
	s.quit({
		0.exit;
	});
};

var initGUI = {

	QtGUI.palette = QPalette.dark;
	w = Window("AirKit", border: true)
		.bounds_(Rect(0,0,1280,800))
		.layout_(mainView)
		.front
		// .fullScreen
		.background_(Color.black.lighten(0.25));

	w.onClose = {
		shutdown.();
	};

	// second screen : visuals only, borderless, exact geometry.
	// setTopLeftBounds bypasses Window.flipY (which measures against the
	// PRIMARY screen height and would drop the window in the wrong place).
	if(hdmiRect.notNil, {
		z = Window("AirKit Visuals", border: false)
			.layout_(VLayout(visualCore.makeSurface()).margins_(0).spacing_(0))
			.background_(Color.black);
		z.setTopLeftBounds(hdmiRect, 0);
		z.userCanClose = false;
		z.front;
		w.front;	// keep the control panel focused on the DSI panel
	});

	CmdPeriod.doOnce({
		w.close;
		if(z.notNil, { z.close });
	});
};

s.volume = -2; // in db
s.options.blockSize = 128;
s.options.numBuffers = 2048;  // more buffers
s.options.memSize = 65536;    // more memory
s.options.numOutputBusChannels = 2; // for quad output

MIDIIn.connectAll;

s.waitForBoot({

	midiCC = MIDIFunc.cc({|...args|
		// args[1].postln;
		NetAddr.new("127.0.0.1", 57120).sendMsg(format("/airkit/cc/%",args[1]), args[0]);
	});

	visualCore.latency = s.latency;

	["local port:", NetAddr.localAddr.port].postln;
	NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/startOSCListening", 57120);

	initGUI.();

	ShutDown.add({"shut down...".postln});

});


)


