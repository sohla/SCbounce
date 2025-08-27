var spacing = 4;
var contentView;
var addTitleView = Require("titleView.scd");
var addReloadButton = Require("reloadButton.scd");
var addPrevButton = Require("prevButton.scd");
var addNextButton = Require("nextButton.scd");
var addThreeDeeView = Require("threeDeeView.scd");
var addPlotterView = Require("plotterView.scd");

var deviceView = {
	var osc = OSCFunc({ |msg, time, addr, recvPort|
		var d = ~devices.at(msg[1].asInteger);
		if(d != nil,{
			{addDetailView.(d)}.defer;
		});
	},'/airkit/addDevice', NetAddr("127.0.0.1", NetAddr.langPort));
	contentView = View()
		.onClose_({osc.free})
		.layout_(GridLayout.new()
			.hSpacing_(spacing)
			.vSpacing_(spacing)
			.margins_([spacing,spacing,spacing,spacing])
		);
	contentView
};

var addDeviceView = { |d|
	var batteryView = UserView()
		.animate_(true)
		.frameRate_(60)
		.clearOnRefresh_(true)
		.drawFunc_({
				Pen.stringAtPoint("Battery: " ++ d.charge.asInteger ++ "%", Rect(0,0,50,20), Font(size:16), d.color);
		});

	View()
		.maxHeight_(30)
		.layout_(HLayout(
			UserView(),
			StaticText()	
					.stringColor_(d.color)
					.align_(\left)
					.font_(Font(size:16))
					.maxWidth_(360)
					.maxHeight_(30)
					.string_("DEVICE:" ++ d.index + "   IP:" ++ d.ip + "   Port:" ++ d.port.asString),

			batteryView

		));

};

var addDetailView = {|d|

	var gl;

	var view = View()
		.background_(Color.black)
		.layout_(gl = GridLayout.rows(
			[
				View().maxHeight_(140)
			],
			[
				[addThreeDeeView.(d), columns:3],
			],
			[
				[addDeviceView.(d), columns:3]
			],
			[
				addPrevButton.(d),
				addTitleView.(d),
				addNextButton.(d),
			]
		));
		// .mouseDownAction_({false})
		// .mouseUpAction_({false});

		// .mouseUpAction_({|but|
		// 	view.background = Color.black;
		// })
		// .mouseDownAction_({|but|
		// 	view.background = Color.white.alpha_(0.1);
		// 	NetAddr("127.0.0.1", 57120).sendMsg("/airkit/calibrate", d.port);
		// });

	gl.addSpanning(addPlotterView.(d,view), 0, 0, 1, 3);

	// layout rows
	contentView.layout.add(view, ((d.index-1)/2).floor, (d.index-1)%2);	

	// layout cols
	// contentView.layout.add(view, (d.index-1)%2, ((d.index-1)/2).floor);	

};
deviceView;



