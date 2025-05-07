(

var staker;
var personalityController = Require("personalityController.scd");
var oscController = Require("oscController.scd");

var stack = {
	var devices = Require("devices.scd");
	var details = Require("details.scd");
	View().layout_(staker =StackLayout(
		details.(),
		devices.(),
	));
};

var tabButton = {|i|
	var d = ["🁪","※"];
	UserView()
	.background_( if(i==0,Color.new255(0, 139, 69),Color.black.alpha_(0.8)))
	.mouseDownAction_({|but|
		but.backColor_(Color.new255(0, 139, 69));
		but.parent.children.do({|ab,i|
			if(but != ab, {
				ab.backColor_(Color.black);
			},{
				staker.index = i;
			});
		},{});
	})
	// .mouseUpAction_({|m|m.backColor_(Color.black.alpha_(0.8))})
	.drawFunc_({|v|Pen.stringAtPoint(d[i], (v.bounds.width-15/2)@25, Font(size:30), Color.white)})
	.animate_(false)

}!2;

var tabs = {|t|
	View().layout_(HLayout(*tabButton.()).spacing_(2).margins_(0)).maxHeight_(100);
};

var mainView = VLayout(
		tabs.(),
		stack.()
);

var shutdown = {
	s.quit;
};

var initGUI = {
	QtGUI.palette = QPalette.dark;
	w = Window().bounds_(Rect(100,100,1000,700)).layout_(mainView).front.fullScreen;
	w.onClose = {
		// stopOSCListening.();
		shutdown.();
	};
	CmdPeriod.doOnce({w.close});
};


s.waitForBoot({

	NetAddr.localAddr.postln;
	initGUI.();
	~startOSCListening.();
	
});


)
