var renderRate = 30;
var spacing = 2;
var contentView;


var devices = {
	contentView = View()
		.layout_(GridLayout.new().hSpacing_(spacing).vSpacing_(spacing).margins_([spacing,spacing,spacing,spacing]))
		.mouseDownAction_({false});
	contentView
};


var titleView = { |data|
	var view = VLayout(
		StaticText()
			.stringColor_(data.color)
			.font_(Font(size:44))
			.minWidth_(100)
			.maxHeight_(60)
			.string_(data.name),
		// StaticText()
		// 	.stringColor_(data.color.alpha_(0.5))
		// 	.font_(Font(size:12))
		// 	.minWidth_(100)
		// 	.maxHeight_(35)
		// 	.string_("IP:" ++ data.ip + "Port:" ++ data.port.asString)
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


var func = {|v|
	Pen.fillColor = Color.grey(0.0,  0.02);
	Pen.addRect(Window.screenBounds);
	Pen.fill;

    Pen.use{
        10.do{
            Color.hsv(rrand(0.0, 0.999), rrand(0.2, 0.7), 1, 1).set;
			Pen.addArc((Window.screenBounds.width.rand)@(Window.screenBounds.height.rand), 5.exprand(150), 2pi.rand, pi);
            Pen.perform([\stroke, \fill].choose);
        }
    }
};

var visual = {
	UserView()
	.background_(Color.black.alpha_(0.8))
	.drawFunc_(func)
	.clearOnRefresh_(false)
	.animate_(true)
	.frameRate_(60)
};

~addDeviceView = {|d,i|

	var gl;
	var view = View()
		.background_(Color.black)
		.layout_(gl = GridLayout.rows(
			[
				titleView.(d),
				threeDeeView.(d),
				
			],[
				[visual.(d), columns:2]
			]
		))
		.mouseDownAction_({|but|
			"Device % clicked".format(i).postln;
			view.background = Color.rand.alpha_(0.3);
		});
	gl.setMinColumnWidth(1, 100);
	gl.setColumnStretch(0, 1);
	contentView.layout.add(view, ((i-1)/2).floor, (i-1)%2);	

};
devices;


