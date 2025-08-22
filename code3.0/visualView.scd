var renderRate = 30;
var spacing = 4;
var contentView;
var renderer = Require("renderer.scd");
var addTitleView = Require("titleView.scd");
var addReloadButton = Require("reloadButton.scd");
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

var threeDeeView = { |data|
	var graph1;
	var cube;
	var accelX, accelY, accelZ;
	
	graph1 = Canvas3D()
		// .mouseDownAction_({false})
		.scale_(80)
		.background_(Color.black)
		.perspective_(0)
		.transforms_([Canvas3D.mRotateZ(0),Canvas3D.mTranslate(0,0,0)])
		.distance_(3.5);
	graph1.add(cube = Canvas3DItem.cube()
		.color_(data.color.alpha_(1.0))
		.width_(2)
		.transform(Canvas3D.mScale(0.4,0.5,1))
	);

	graph1.add(accelX = Canvas3DItem.regPrism()
		.color_(Color.yellow(0.9))
		.width_(2)
		.fill_(true)
	);
	graph1.add(accelY = Canvas3DItem.regPrism()
		.color_(Color.magenta(0.9))
		.width_(2)
		.fill_(true)
	);
	graph1.add(accelZ = Canvas3DItem.regPrism()
		.color_(Color.cyan(0.9))
		.width_(2)
		.fill_(true)
	);

	// animate
	graph1.animate(renderRate) {|t|
		var sz = 0.1;
		var tr = [
			data.sensors.gyroEvent.y,//0
			data.sensors.gyroEvent.z,//1
			data.sensors.gyroEvent.x//2
		];

		cube.transforms = [
			Canvas3D.mRotateX(tr[0]),
			Canvas3D.mRotateY(tr[1]),
			Canvas3D.mRotateZ(tr[2]),
		];

		accelY.transforms = [
			Canvas3D.mScale(0.01,(data.sensors.accelEvent.y),sz),
			Canvas3D.mRotateX(tr[0]),
			Canvas3D.mRotateY(tr[1]),
			Canvas3D.mRotateZ(tr[2]),
		];

		accelX.transforms = [
			Canvas3D.mScale((data.sensors.accelEvent.z),sz,sz),
			Canvas3D.mRotateX(tr[0]),
			Canvas3D.mRotateY(tr[1]),
			Canvas3D.mRotateZ(tr[2]),
		];
		accelZ.transforms = [
			Canvas3D.mScale(sz,sz,(data.sensors.accelEvent.x)),
			Canvas3D.mRotateX(tr[0]),
			Canvas3D.mRotateY(tr[1]),
			Canvas3D.mRotateZ(tr[2]),
		];
	};
};

var addDeviceView = {|d|

	var gl;
	var tv = addTitleView.(d);
	var view = View()
		.background_(Color.black)
		.layout_(gl = GridLayout.rows(
			[				
				addReloadButton.(d),
				[UserView(), columns:2]
			],[
				[renderer.(d), columns:3]
			],[
				addPrevButton.(d),
				tv,
				addNextButton.(d),
				// addReloadButton.(d),
				// [tv, columns:2]
			]
		))



		// .mouseDownAction_({false})
		// .mouseUpAction_({false});

		.mouseUpAction_({|but|
			view.background = Color.black;
		})
		.mouseDownAction_({|but|
			view.background = Color.white.alpha_(0.1);
			d.personality = d.personality + 1;
			NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/loadPersonality", d.port, d.personality);
		});

	// layout rows
	contentView.layout.add(view, ((d.index-1)/2).floor, (d.index-1)%2);	
	
	// layout cols
	// contentView.layout.add(view, (d.index-1)%2, ((d.index-1)/2).floor);	
};

visualView;


