var renderRate = 30;
var spacing = 2;
var contentView;


var devices = {
	contentView = View()
		.layout_(GridLayout.new().hSpacing_(spacing).vSpacing_(spacing).margins_([spacing,spacing,spacing,spacing]))
		.mouseDownAction_({false});
	contentView
};


var threeDeeView = { |view, data|
	var graph1;
	var cube;
	var accelX, accelY, accelZ;

	graph1 = Canvas3D()
		.mouseDownAction_({false})
		.scale_(160)
		.background_(Color.gray(0.25))
		.perspective_(0)
		.transforms_([Canvas3D.mRotateZ(pi/2),Canvas3D.mTranslate(-0.6,0,0)])
		.distance_(3.5)
		.postDrawFunc = {
			Pen.color_(Color.white);
			("Title").drawAtPoint(10@10, Font(size:32));
			Pen.color_(Color.white.alpha_(0.4));
			("ID: "+data.did+" OSC: ["++data.ip+", "+data.port++"]").drawAtPoint(10@280, Font(size:12));
		};

	graph1.add(cube = Canvas3DItem.cube()
		.color_(data.color.alpha_(1))
		.width_(2)
		.transform(Canvas3D.mScale(0.4,0.5,1))
	);

	graph1.add(accelX = Canvas3DItem.regPrism()
		.color_(Color.yellow(0.9))
		.width_(2)
	);
	graph1.add(accelY = Canvas3DItem.regPrism()
		.color_(Color.magenta(0.9))
		.width_(2)
	);
	graph1.add(accelZ = Canvas3DItem.regPrism()
		.color_(Color.cyan(0.9))
		.width_(2)
	);

	// animate
	graph1.animate(renderRate) {|t|
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
			Canvas3D.mScale(0.01,(data.sensors.accelEvent.y),0.01),
			Canvas3D.mRotateX(tr[0]),
			Canvas3D.mRotateY(tr[1]),
			Canvas3D.mRotateZ(tr[2]),
		];

		accelX.transforms = [
			Canvas3D.mScale((data.sensors.accelEvent.z),0.01,0.01),
			Canvas3D.mRotateX(tr[0]),
			Canvas3D.mRotateY(tr[1]),
			Canvas3D.mRotateZ(tr[2]),
		];
		accelZ.transforms = [
			Canvas3D.mScale(0.01,0.01,(data.sensors.accelEvent.x)),
			Canvas3D.mRotateX(tr[0]),
			Canvas3D.mRotateY(tr[1]),
			Canvas3D.mRotateZ(tr[2]),
		];
	};
};

~addDeviceView = {|d,i|

	var view = View()
		.background_(Color.black)
		.layout_(HLayout(
			threeDeeView.(view, d)
		))
		.mouseDownAction_({|but|
			"Device % clicked".format(i).postln;
			view.background = Color.rand
		});

	contentView.layout.add(view, ((i-1)/2).floor, (i-1)%2);	



};
devices;


