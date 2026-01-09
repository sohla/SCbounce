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

	// Create multiple raindrops
	5.do {
		var xPos = 1.0.rand2; // Random horizontal position between -1 and 1
		var dropSize = rrand(0.2, 3); // Small variation in size
		var dropDuration = rrand(1.5, 2.5); // Slightly different fall speeds
		var colorChoice = [
			Color.green,
			Color(0.0, 0.8, 0.3), // bright green
			Color(0.2, 0.7, 0.5), // cyan-green
			Color.blue,
			Color(0.0, 0.5, 0.8), // light blue
			Color.cyan
		].choose;

		var ev = (
			type: \customVisualEvent,
			amp: 0,
			viewID: d.port,
			shape: \circle,
			fill: true,
			rotate: 0,
			startSize: dropSize,
			endSize: dropSize * 0.8, // Slightly shrink as it falls
			duration: dropDuration,
			startColor: colorChoice.alpha_(1).lighten(Color.cyan),	
			endColor: colorChoice.alpha_(0.2),
			startWidth: 1,
			endWidth: 1,
			sx: xPos,
			sy: -0.9, // Start at top
			ex: xPos + rrand(-0.05, 0.05), // Slight horizontal drift
			ey: 0.9, // Fall to bottom
			rotation: 0,
		);
		ev.play;
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




