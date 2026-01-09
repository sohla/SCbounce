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

// var ev = (
// 		type: \customVisualEvent,
// 		amp: 0,
// 		viewID: d.port,
// 		shape: \cirlce,
// 		fill: false,
// 		rotate: 0,
// 		startSize: 100,
// 		endSize: 200,
// 		duration: 1,
// 		startColor: Color.red,
// 		endColor: Color.blue.alpha_(0),
// 		startWidth: 10,
// 		endWidth: 1,
// 		sx: 0,
// 		sy: 0,
// 		ex: 0,
// 		ey: 0,
// 		rotation: 0,//TempoClock.beats.mod(pi) * 0.25,
// 		modulation: (
// 			type: \radial,
// 			freq: 0.02,
// 			amp: 0,
// 			harmonics: 1
// 		),
// 	);
// 	ev.play;
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




