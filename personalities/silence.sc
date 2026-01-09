var m = ~model;

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.4;

//------------------------------------------------------------

//------------------------------------------------------------
~init = ~init <> {|d|


};
//------------------------------------------------------------
~deinit = ~deinit <> {
};

//------------------------------------------------------------
~onEvent = {|e|

};

//------------------------------------------------------------
~next = {|d|

	// Water droplet falling and creating ripples
	var xPos = rrand(-0.6, 0.6); // Random horizontal position
	var waterSurface = 0.2; // Where the water is
	var dropSize = 3;

	// Falling droplet
	var droplet = (
		type: \customVisualEvent,
		amp: 0,
		viewID: d.port,
		shape: \circle,
		fill: true,
		rotate: 0,
		startSize: dropSize,
		endSize: dropSize,
		duration: 1.2,
		startColor: Color(0.3, 0.6, 0.9).alpha_(0.8),
		endColor: Color(0.3, 0.6, 0.9).alpha_(0.8),
		startWidth: 1,
		endWidth: 1,
		sx: xPos,
		sy: -0.8, // Start at top
		ex: xPos,
		ey: waterSurface, // Fall to water surface
		rotation: 0,
	);
	droplet.play;

	// Expanding ripple circles at impact point
	2.do { |i|
		var ripple = (
			type: \customVisualEvent,
			amp: 0,
			viewID: d.port,
			shape: \circle,
			fill: false,
			rotate: 0,
			startSize: 5,
			endSize: 80,
			duration: 2.0 + (i * 0.3),
			startColor: Color(0.2, 0.5, 0.8).alpha_(0.5),
			endColor: Color(0.2, 0.5, 0.8).alpha_(0),
			startWidth: 2,
			endWidth: 0.5,
			sx: xPos,
			sy: waterSurface, // At water surface
			ex: xPos,
			ey: waterSurface,
			rotation: 0,
		);
		// Delay second ripple slightly
		SystemClock.sched(1.2 + (i * 0.2), { ripple.play; nil });
	};
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// [yellow, magenta, cyan]

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;
	
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.5;
	// [m.accelMass, m.accelMassFiltered];
	// [d.sensors.accelEvent.x.abs * d.sensors.accelEvent.y.abs, m.accelMassFiltered];
	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].abs;
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];

	[m.accelMassFiltered * 3, m.rrateMassFiltered * 10, (d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2];

	// Gyro
		// [(d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2];//left right
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [[(d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2, (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2].sum] / 3;

	
	// [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
	// [d.port,d.sensors.digiInEvent].postln;
	// [d.sensors.digiInEvent[0],m.gyroXFiltered, m.gyroYFiltered];


};




