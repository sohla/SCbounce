(

var staker;
var personalityController = Require("personalityController.scd");
var oscController = Require("oscController.scd");
var specsView;

var stack = {
	var deviceView = Require("deviceView.scd");
	var visualView = Require("visualView.scd");
	var systemView = Require("systemView.scd");
	var view = View().layout_(staker =StackLayout(
		deviceView.(),
		visualView.(),
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
		stack.()
).spacing_(4).margins_(0);

var shutdown = {
	s.quit;
};

var initGUI = {
	QtGUI.palette = QPalette.dark;
	w = Window()
		.bounds_(Rect(100,100,1000,700))
		.layout_(mainView)
		.front
		.fullScreen
		.background_(Color.black.lighten(0.25));
	w.onClose = {
		shutdown.();
	};
	CmdPeriod.doOnce({w.close});
};

s.volume = -10;
s.options.blockSize = 128; 
s.options.numBuffers = 2048;  // more buffers
s.options.memSize = 65536;    // more memory

s.waitForBoot({

	NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/startOSCListening", 57120);

	initGUI.();

});


)


