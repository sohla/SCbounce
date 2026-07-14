var renderRate = 30;
var spacing = 4;
var contentView;
var renderer = Require("renderer.scd");
var addTitleView = Require("titleView.scd");
var addPrevButton = Require("prevButton.scd");
var addNextButton = Require("nextButton.scd");

var visualView = {
	var osc = OSCFunc({ |msg, time, addr, recvPort|
		var d = ~devices.at(msg[1].asInteger);
		if(d != nil,{
			{addDeviceView.(d)}.defer;
		});
	},'/airkit/addDevice', NetAddr("127.0.0.1", NetAddr.langPort));
	contentView = View()
		.onClose_({osc.free})
		.layout_(GridLayout.new().hSpacing_(spacing).vSpacing_(spacing).margins_([spacing,spacing,spacing,spacing]));
		// .mouseDownAction_({false});
	contentView
};

var addDeviceView = {|d|

	var gl;
	var numDevices = contentView.children.size;

	var view = View()
		.background_(Color.black)
		.layout_(gl = GridLayout.rows(
			[				
				[renderer.(d), columns:2]
			],[
				[addTitleView.(d), columns:2],
			],[
				addPrevButton.(d),
				addNextButton.(d),
			]
		).hSpacing_(spacing).vSpacing_(spacing).margins_([0,0,0,0]))

		// .mouseDownAction_({false})
		// .mouseUpAction_({false});

		.mouseUpAction_({|but|
			view.background = Color.black;
		})
		.mouseDownAction_({|but|
			view.background = Color.white.alpha_(0.1);
			d.personality = d.personality + 1;
			NetAddr.new("127.0.0.1", NetAddr.langPort).sendMsg("/airkit/loadPersonality", d.port, d.personality);
		});

	// add device view
	// contentView.layout.add(view, (numDevices/2).floor, numDevices%2);	
	contentView.layout.add(view, 0, numDevices);	

};

visualView;


