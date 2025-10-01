var m = ~model;
m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.99;
m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay = 0.99;

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
	// [0.2,0.4,0.6];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.5;
	// [m.accelMass.abs - m.accelMass, m.accelMass - m.accelMass.abs];
	// [d.sensors.velocity.sum.abs * 30 ,m.accelMass];// compare these values we can get direction?

	// [d.sensors.velocity.sum * 30, d.sensors.velocity.sum.abs * 30];


	// // [((m.accelMassFiltered - m.accelMassFiltered.abs)-(m.accelMassFiltered.abs - m.accelMassFiltered)).abs, m.accelMassFiltered.abs];
	// [m.rrateMassFiltered];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	[(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [d.sensors.rotateEvent.x, d.sensors.rotateEvent.y, d.sensors.rotateEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z] * 4;
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;


};




