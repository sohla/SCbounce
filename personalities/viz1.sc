var m = ~model;
var lastTime = 0;

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

	var time = TempoClock.beats;

	if(TempoClock.beats > (lastTime + 0.1),{

		var xPos = rrand(-1.0,1.0); 
		var baseY = 0.0;//4.5 - (sin(time * 0.5) * 0.5); 
		var size = 30;
		var duration = 1;//rrand(3.0, 4.0) * 2;
		var wavePhase = rrand(0, 2pi); 

		var colEnv = Env([0, 1], [2], \linear);
		var colMod = ((time.mod(10)/10)*0.1);


		var ev = (
			type: \customVisualEvent,
			amp: 0,
			viewID: d.port,
			numPoints: 32,
			shape: \square,
			fill: true,
			bgColor: Color.white, 
			rotate: 0,
			startSize: size,
			endSize: size/8,
			// sizeEnv: Env.sine(dur: 1.0, level: 1.0),
			duration: duration,
			startColor: Color.blue.alpha_(0.0),
			endColor: Color.green.alpha_(1.0),
			colorEnv: Env.xyc([[0, 0, \sin], [0.01, 1, \sin], [1.0, 0, \sin]]),
			startWidth: 1,
			endWidth: 1,
			sx: xPos,
			sy: baseY,
			ex: xPos, 
			ey: baseY,
			// yEnv: yEnv, 
			rotation: pi/4,
			// modulation: (
			// 	type: \normal,
			// 	freq: rrand(0.1,0.3),
			// 	amp:rrand(80,100),
			// 	harmonics: 10,
			// )
		);
		ev.play;
		lastTime = time;

	});

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




