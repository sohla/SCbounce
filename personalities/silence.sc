var m = ~model;
m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.2;

//------------------------------------------------------------

//------------------------------------------------------------
~init = ~init <> {
};
//------------------------------------------------------------
~deinit = ~deinit <> {
};

//------------------------------------------------------------
~onEvent = {|e|
};

//------------------------------------------------------------
~next = {|d|
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// [yellow, magenta, cyan]

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;
	
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	// [m.accelMass, m.accelMassFiltered] * 0.2;

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].abs;
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	[m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];




};




