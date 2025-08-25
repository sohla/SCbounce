var renderRate = 30;

var addThreeDeeView = { |data|
	var graph1;
	var cube;
	var accelX, accelY, accelZ;
	
	graph1 = Canvas3D()
		.mouseDownAction_({false})
		.mouseUpAction_({false})
		.scale_(220)
		.background_(Color.black)
		.perspective_(0.75)
		.transforms_([Canvas3D.mRotateZ(0),Canvas3D.mTranslate(0,0,0)])
		.distance_(3.5);
	graph1.add(cube = Canvas3DItem.cube()
		.color_(data.color.alpha_(1.0))
		.width_(2)
		.transform(Canvas3D.mScale(0.4,0.5,1))
	);

	graph1.add(accelX = Canvas3DItem.cube()
		.color_(Color.yellow(0.9))
		.width_(1)
		.transform(Canvas3D.mScale(0.1,0.1,0.1))
		// .fill_(true)
	);
	graph1.add(accelY = Canvas3DItem.cube()
		.color_(Color.magenta(0.9))
		.width_(1)
		.transform(Canvas3D.mScale(0.1,0.1,0.1))
		// .fill_(true)
	);
	graph1.add(accelZ = Canvas3DItem.cube()
		.color_(Color.cyan(0.9))
		.width_(1)
		.transform(Canvas3D.mScale(0.1,0.1,0.1))
		// .fill_(true)
	);

	// animate
	graph1.animate(renderRate) {|t|
		var sz = 0.1;
		var tr = [
			data.sensors.gyroEvent.x,
			data.sensors.gyroEvent.y,
			data.sensors.gyroEvent.z
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
addThreeDeeView