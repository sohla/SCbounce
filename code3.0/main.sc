(

var staker;
var personalityController = Require("personalityController.scd");
var oscController = Require("oscController.scd");
var specsView;

var midiCC;

var stack = {
	var deviceView = Require("deviceView.scd");
	var visualView = Require("visualView.scd");
	var controlView = Require("controlView.scd");
	var systemView = Require("systemView.scd");
	var view = View().layout_(staker =StackLayout(
		deviceView.(),
		visualView.(),
		controlView.(),
		systemView.()
	));
	
	view
};

var tabButton = {|i|
	var d = ["device","visual","control","system"];
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
	.drawFunc_({|v|Pen.stringAtPoint(d[i], (v.bounds.width-45/2)-40@25, Font(size:30), Color.white.darken(0.75))})
	.animate_(false)

}!4;

var tabs = {|t|
	View().layout_(HLayout(*tabButton.()).spacing_(2).margins_(0)).maxHeight_(100);
};

var mainView = VLayout(
		tabs.(),
		stack.()
).spacing_(4).margins_(0);

var shutdown = {
	midiCC.free;
	s.quit({
		0.exit;
	});
};

var initGUI = {

	// var visualView = Require("visualView.scd");

	QtGUI.palette = QPalette.dark;
	w = Window("AirKit", border: false)
		.bounds_(Rect(0,0,1280,800))
		.layout_(mainView)
		.front
		.fullScreen
		.background_(Color.black.lighten(0.25));

	w.onClose = {
		shutdown.();
	};

	// z = Window("Visual", border: false)
	// 	.bounds_(Rect(1280,-40,1024,768))
	// 	.layout_(VLayout(visualView.()))
	// 	// .front
	// 	// .fullScreen
	// 	.background_(Color.black);

	// w.front;
	// z.front;

	CmdPeriod.doOnce({
		w.close;
		// z.close;
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

	["local port:", NetAddr.localAddr.port].postln;
	NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/startOSCListening", 57120);

	initGUI.();
	
	ShutDown.add({"shut down...".postln});

	// {
	// 	var a = "/1/IMUFusedData";
	// 	var n = NetAddr("127.0.0.1", 57120);
	// 	n.sendMsg(a, 0,0,0,0,0,0,0,0,0,0,0);
	// }.defer(2);

});


)


