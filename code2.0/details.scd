var renderRate = 30;
var spacing = 2;
var contentView;

var details = {
	contentView = View()
		.layout_(GridLayout.new().hSpacing_(spacing).vSpacing_(spacing).margins_([spacing,spacing,spacing,spacing]))
		.mouseDownAction_({false});
	contentView
};


var titleView = { |data|
	var view = VLayout(
		StaticText()
			.stringColor_(data.color)
			.font_(Font(size:32))
			.minWidth_(100)
			.maxHeight_(50)
			.string_(data.name),
		StaticText()
			.stringColor_(data.color.alpha_(0.5))
			.font_(Font(size:12))
			.minWidth_(100)
			.maxHeight_(20)
			.string_("IP:" ++ data.ip + "Port:" ++ data.port.asString)
	);
	view
};



var threeDeeView = { |data|
	var graph1;
	var cube;
	var accelX, accelY, accelZ;
	
	graph1 = Canvas3D()
		.mouseDownAction_({false})
		.scale_(80)
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


var visual = {|data|
	var plotterView = UserView().backColor_(data.color.alpha_(0.5));
	plotterView
};

~addDetailView = {|d,i|

	var gl;
	var view = View()
		.background_(Color.black)
		.layout_(gl = GridLayout.rows(
			[
				titleView.(d),
				threeDeeView.(d),
			],[
			]
		))
		.mouseDownAction_({|but|
			"Device % clicked".format(i).postln;
			view.background = Color.rand.alpha_(0.3);
		});

	var plotter = Plotter("plotter", view.bounds, view)
		.value_((0..100))
		.refresh;

	gl.setMinColumnWidth(1, 100);
	gl.setColumnStretch(0, 1);
	contentView.layout.add(view, ((i-1)/2).floor, (i-1)%2);	

	plotter.setProperties(
		\gridColorX, Color.grey,
		\gridColorY, Color.grey,
		\plotColor, d.color,
		\fontColor, d.color,
		\backgroundColor, Color.black
	);

};

details;


