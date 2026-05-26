var m = ~model;
var params = (
    \volume: 0.8,
    \sensitivity: 0.6,
	\effectAmount: 0.2,
);
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
	var xPos = [-0.8,-0.6,-0.3,0.0,0.2,0.5,0.7,1.0].choose; // Random x position
	var baseY = 4.7 - (sin(time * 0.5) * 0.2); // Bottom half of screen
	var squareSize = rrand(800,1000);
	var duration = rrand(1.0, 4.0);
	var wavePhase = rrand(0, 2pi); // Random phase offset for wave

	// Oscillating vertical motion - sine wave
	var waveHeight = 0.1;
	var yEnv = Env(
		[0, waveHeight, 0, waveHeight.neg, 0],
		[0.25, 0.25, 0.25, 0.25],
		\sine
	);

	// Oscillating rotation
	var rotationAmount = rrand(0.2, 0.5);

	// Ocean colors - blues and teals
	var oceanColor = [
		Color(0.1, 0.3, 0.6), // deep blue
		Color(0.2, 0.5, 0.7), // ocean blue
		Color(0.1, 0.4, 0.7), // medium blue
		Color(0.3, 0.6, 0.8), // light blue
		Color(0.2, 0.5, 0.6), // teal blue
	].choose;

	var ev = (
		type: \customVisualEvent,
		amp: 0,
		viewID: d.port,
		shape: \star,
		fill: false,
		rotate: 0,
		startSize: squareSize * 0.001,
		endSize: squareSize * 0.01,
		duration: duration,
		startColor: d.color.alpha_(0.8),
		endColor: oceanColor.lighten(0.2).alpha_(0.1),
		startWidth: 1,
		endWidth: 1,
		sx: xPos * 0.1,
		sy: baseY - 4.5,
		ex: xPos, // Stay in same x position
		ey: baseY.neg, // Stay in same y, oscillation via envelope
		yEnv: yEnv, // Vertical oscillation
		rotation: sin(time) * 0.1,
	);
	// ev.play;
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

//------------------------------------------------------------

~controlView = {|d|

	var sliderView = {|lt="default", rt="", dd|
  var st;
  View().layout_(HLayout(
    UserView()
      .background_(Color.black.lighten(0.1))
      // .maxHeight_(80)
      .frameRate_(30)
      .animate_(true)
      .drawFunc_({|v|
        var vw = v.bounds.width;
        var vh = v.bounds.height;
        var tw = 140;
				var pos = (vw * params.at(lt.asSymbol)) - (tw/2);
        // lt.drawLeftJustIn(Rect(10,0,vw/2,vh), Font(size:48), Color.black());
        // rt.drawRightJustIn(Rect(vw/2,0,(vw/2)-10,vh), Font(size:48), Color.black());
        Pen.fillColor = d.color.alpha_(0.5);
        Pen.addRect(Rect(pos, 2, tw, vh - 4));
        Pen.fill;
        lt.drawCenteredIn(Rect(pos,0,tw,40), Font(size:24), Color.black());
      })
      .mouseMoveAction_({|v,x,y|
        var vw = v.bounds.width;
        var vh = v.bounds.height;
        if(x<0,{x=0});
				params.put(lt.asSymbol, x/vw);
      })
      .mouseUpAction_({|v,x,y|
      })
      .mouseDownAction_({|v,x,y|
        var vw = v.bounds.width;
        var vh = v.bounds.height;
        if(x<0,{x=0});
				params.put(lt.asSymbol, x/vw);
      });
  ));
  
};

	var spacing = 4;
  var sliders = params.collect({|v,k| 
      [sliderView.(k.asString, "", d)]
  });
	View()
		// .onClose_({osc.free})
		.layout_(GridLayout.columns(sliders).hSpacing_(spacing).vSpacing_(spacing))
};


