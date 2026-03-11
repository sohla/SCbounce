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

	// Create oscillating squares in bottom half - ocean effect
	var time = TempoClock.beats;

	if(TempoClock.beats > (lastTime + rrand(0.3, 0.5)),{

		var xPos = rrand(-0.1,0.1); // Random x position
		var baseY = 3-(sin(time * 0.5) * 0.5); // Bottom half of screen
		var squareSize = 1100;//rrand(1300,1700);
		var duration = rrand(3.0, 4.0) * 2;
		var wavePhase = rrand(0, 2pi); // Random phase offset for wave

		// Oscillating vertical motion - sine wave
		var waveHeight = 0.1;
		var yEnv = Env(
			[0, waveHeight, 0, waveHeight.neg, 0],
			[0.25, 0.25, 0.25, 0.25],
			\sine
		);
		var colEnv = Env([0, 1], [2], \linear);
		var colMod = ((time.mod(10)/10)*0.1);

		// Oscillating rotation
		var rotationAmount = rrand(0.2, 0.5);

		// Ocean colors - blues and teals
		var oceanColor = [
			Color(0.1, 0.3, 0.6), // deep blue
			Color(0.2, 0.5, 0.7), // ocean blue
			Color(0.1, 0.4, 0.7), // medium blue
			Color(0.3, 0.4, 0.9), // light blue
			Color(0.7, 0.8, 1.0),
			Color(0.2, 0.5, 0.6), // teal blue
		].choose;

		var ev = (
			type: \customVisualEvent,
			amp: 0,
			viewID: d.port,
			numPoints: 128,
			shape: \square,
			fill: false,
			bgColor: Color(1.0, 1.0, 0.5).alpha_(0.7+colMod), 
			rotate: 0,
			startSize: squareSize,
			endSize: squareSize*1.1,
			sizeEnv: Env.sine(dur: 1.0, level: 1.0),
			duration: duration,
			startColor: oceanColor.alpha_(0.5),
			endColor: oceanColor.lighten(0.2).alpha_(0.0),
			startWidth: 1,
			endWidth: 1,
			sx: xPos,
			sy: baseY,
			ex: xPos, 
			ey: baseY,
			// yEnv: yEnv, 
			rotation: sin(time) * 0.02,
			modulation: (
				type: \normal,
				freq: rrand(0.1,0.3),
				amp:rrand(80,100),
				harmonics: 10,
			)
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




