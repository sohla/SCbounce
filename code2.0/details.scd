var renderRate = 30;
var spacing = 2;
var contentView;
var addTitleView = Require("titleView.scd");

var details = {

	var osc = OSCFunc({ |msg, time, addr, recvPort|
		var d = ~devices.at(msg[1].asInteger);
		if(d != nil,{
			{addDetailView.(d)}.defer;
			d.index.postln;
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


// var titleView = { |data|
// 	var titleText;
// 	var view = VLayout(
// 		titleText = StaticText()
// 			.stringColor_(data.color)
// 			.font_(Font(size:64))
// 			.minWidth_(100)
// 			.maxHeight_(80)
// 			.string_(data.name),
// 		StaticText()	
// 			.stringColor_(data.color.alpha_(0.5))
// 			.font_(Font(size:12))
// 			.minWidth_(100)
// 			.maxHeight_(20)
// 			.string_("IP:" ++ data.ip + "Port:" ++ data.port.asString)
// 	);
// 	[view, titleText]
// };



var threeDeeView = { |data|
	var graph1;
	var cube;
	var accelX, accelY, accelZ;
	
	graph1 = Canvas3D()
		.mouseDownAction_({false})
		.mouseUpAction_({false})
		.scale_(220)
		.background_(Color.clear)
		.perspective_(0)
		.transforms_([Canvas3D.mRotateZ(0),Canvas3D.mTranslate(0,0,0)])
		.distance_(3.5);
	graph1.add(cube = Canvas3DItem.cube()
		.color_(data.color.alpha_(1))
		.width_(2)
		.transform(Canvas3D.mScale(0.4,0.5,1))
	);

	graph1.add(accelX = Canvas3DItem.regPrism()
		.color_(Color.yellow(0.9))
		.width_(1)
		// .fill_(true)
	);
	graph1.add(accelY = Canvas3DItem.regPrism()
		.color_(Color.magenta(0.9))
		.width_(1)
		// .fill_(true)
	);
	graph1.add(accelZ = Canvas3DItem.regPrism()
		.color_(Color.cyan(0.9))
		.width_(1)
		// .fill_(true)
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


var plotterView = {|data, parentView|
	// var pv = UserView(parentView, parentView.bounds).backColor_(data.color.alpha_(0.5)).animate_(true).frameRate_(12);
	var plotter = Plotter("plotter", parentView.bounds, parentView)
		.value_((0..50))
		.minval_(-1.0)
		.maxval_(1.0)
		.refresh;
	var pd = [];
	var cols = [Color.yellow,Color.magenta,Color.cyan,Color.red,Color.green,Color.blue];
	var plotData = { data.env.use{ ~plot.(data)} };

	plotter.setProperties(
		\gridColorX, Color.grey,
		\gridColorY, Color.grey,
		\plotColor, cols,
		\fontColor, data.color,
		\backgroundColor, Color.black
	);


	Routine{
		loop {
			{
			// pd = pd.addFirst([sin(Clock.beats),cos(Clock.beats),tan(Clock.beats).wrap2(1.0)]);
			pd = pd.addFirst(plotData.());

			if(pd.size > 50, {pd.pop()});
			plotter.superpose = true;
			plotter.setValue(pd, false, true, false);
			plotter.value = plotter.value.keep(50).flop;
			}.defer;
			0.03.yield; //this has a big impact on CPU use
		}
	}.play;

	plotter.interactionView.mouseDownAction_({false});
	plotter.interactionView.mouseUpAction_({false});

	plotter.interactionView
};

var addDetailView = {|d|

	var gl;
	var tv = addTitleView.(d);

	var view = View()
		.background_(Color.black)
		.layout_(gl = GridLayout.rows(
			[
				tv,
			],[
				threeDeeView.(d),
			]
		))
		// .mouseDownAction_({false})
		// .mouseUpAction_({false});

		.mouseUpAction_({|but|
			view.background = Color.black;
		})
		.mouseDownAction_({|but|
			// "Device % clicked".format(d.index).postln;
			// view.background = Color.white.alpha_(0.1);
			// d.personality = d.personality + 1;
			// tv.string = ~reloadPersonality.(d, d.personality).name;
		});

	// gl.setMinColumnWidth(1, 100);
	// gl.setColumnStretch(0, 1);
	contentView.layout.add(view, ((d.index-1)/2).floor, (d.index-1)%2);	

	// gl.add(plotterView.(d,view), 1, 1);

};
details;


