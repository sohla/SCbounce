(

var staker;
var personalityController = Require("personalityController.scd");
var oscController = Require("oscController.scd");
var conductorController = Require("conductorController.scd");
var specsView;
var conductorView = Require("conductorView.scd");

var midiCC;

var stack = {
	var deviceView = Require("deviceView.scd");
	var visualView = Require("visualView.scd");
	// var controlView = Require("controlView.scd");
	var systemView = Require("systemView.scd");
	var view = View().layout_(staker =StackLayout(
		deviceView.(),
		visualView.(),
		// controlView.(),
		systemView.()
	));
	
	view
};

var tabButton = {|i|
	var d = ["device","visual","system"];
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

}!3;

var tabs = {|t|
	View().layout_(HLayout(*tabButton.()).spacing_(2).margins_(0)).maxHeight_(100);
};

var mainView = VLayout(
		tabs.(),
		stack.(),
		conductorView.()		
).spacing_(4).margins_(0);

var shutdown = {
	midiCC.free;
	s.quit({
		0.exit;
	});
};

var initGUI = {

	QtGUI.palette = QPalette.dark;
	w = Window("AirKit")
		.bounds_(Rect(100,100,1000,1200))
		.layout_(mainView)
		.front
		// .fullScreen
		.background_(Color.black.lighten(0.25));
	w.onClose = {
		shutdown.();
	};
	CmdPeriod.doOnce({w.close});
};

s.volume = -2; // in db
s.options.blockSize = 128; 
s.options.numBuffers = 2048;  // more buffers
s.options.memSize = 65536;    // more memory
s.options.numOutputBusChannels = 2; // for quad output
s.latency = 0.2;

MIDIIn.connectAll;

s.waitForBoot({

	"🎧 Welcome to AirConcert 🎧".postln;

	midiCC = MIDIFunc.cc({|...args|
		// args[1].postln;
		NetAddr.new("127.0.0.1", NetAddr.langPort).sendMsg(format("/airkit/cc/%",args[1]), args[0]);
	});

	["local port:", NetAddr.localAddr.port].postln;
	NetAddr.new("127.0.0.1", NetAddr.langPort).sendMsg("/airkit/startOSCListening", NetAddr.langPort);

	initGUI.();
	
	// add global params to topEnvironment
	// Routine {
    //     loop{
	// 		~globalTempoClock = TempoClock.beats;
	// 		0.1.yield;
	// 	};
	// }.play;
	
	ShutDown.add({"shut down...".postln})
});


)


