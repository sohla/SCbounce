var renderRate = 30;

var addThreeDeeView = { |d|
	var graph1;
	var cube;
	var accelX, accelY, accelZ;
	var as = 0.2;

	graph1 = Canvas3D()
		.scale_(220)
		.background_(Color.black)
		.perspective_(0.75)
		.transforms_([Canvas3D.mRotateZ(0),Canvas3D.mTranslate(0,0,0)])
		.distance_(3.5)
		.mouseUpAction_({|v|
			v.background = Color.black;
		})
		.mouseDownAction_({|v|
			v.background = Color.white.alpha_(0.1);
			NetAddr("127.0.0.1", 57120).sendMsg("/airkit/calibrate", d.port);
		});

	graph1.add(cube = Canvas3DItem.cube()
		.color_(d.color.alpha_(1))
		.width_(2)
		.transform(Canvas3D.mScale(1.0,0.4,0.5))
		// .transform(Canvas3D.mScale(1.0,1.0,1.0))
	);

	graph1.add(accelX = Canvas3DItem.cube()
		.color_(Color.yellow(0.9))
		.width_(1)
		.transform(Canvas3D.mScale(as,as,as))
	);

	graph1.add(accelY = Canvas3DItem.cube()
		.color_(Color.magenta(0.9))
		.width_(1)
		.transform(Canvas3D.mScale(as,as,as))
	);
	graph1.add(accelZ = Canvas3DItem.cube()
		.color_(Color.cyan(0.9))
		.width_(1)
		.transform(Canvas3D.mScale(as,as,as))
	);

	// animate
	graph1.animate(renderRate) {|t|
		var sz = 0.1;
		var tr = [
			(d.sensors.gyroEvent.x + pi.half).wrap(-pi,pi),
			(d.sensors.gyroEvent.y).wrap(-pi.half,pi.half) * 1.neg,
			(d.sensors.gyroEvent.z - pi.half).wrap(-pi,pi) * 1.neg
		];

		cube.transforms = [
			Canvas3D.mRotateX(tr[0]),
			Canvas3D.mRotateY(tr[1]),
			Canvas3D.mRotateZ(tr[2]),
		];

		accelY.transforms = [
			Canvas3D.mScale(0.01,(d.sensors.accelEvent.y),sz),
			Canvas3D.mRotateX(tr[0]),
			Canvas3D.mRotateY(tr[1]),
			Canvas3D.mRotateZ(tr[2]),
		];

		accelX.transforms = [
			Canvas3D.mScale((d.sensors.accelEvent.z),sz,sz),
			Canvas3D.mRotateX(tr[0]),
			Canvas3D.mRotateY(tr[1]),
			Canvas3D.mRotateZ(tr[2]),
		];
		accelZ.transforms = [
			Canvas3D.mScale(sz,sz,(d.sensors.accelEvent.x)),
			Canvas3D.mRotateX(tr[0]),
			Canvas3D.mRotateY(tr[1]),
			Canvas3D.mRotateZ(tr[2]),
		];
	};
};
addThreeDeeView