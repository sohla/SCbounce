var m = ~model;
var isOn = false;
// var bl = [0,-7,-3,-10,-8,-7,-3,-5].stutter(2);
var bl = [0,4,-2,7].stutter(2);
var cr = [0,5,-2,3,-4,1,-5];

m.midiChannel = 12;
m.accelMassThreshold = 0.7;
m.rrateMassThreshold = 0.1;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([0],inf),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.14);
	Pdef(m.ptn).set(\octave,5);
	Pdef(m.ptn).set(\amp,0.8);

	// change notes
	Pdef(m.ptn,Pbind( 
		\note, Pseq([0,1,2,3,4,5,6,7,8,7,6,5,4,3,2,1],inf)
	));

};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};

~onHit = {|state|

	// var vel = 80;

	// if(state == true,{
	// 	m.midiOut.noteOn(m.midiChannel, 60 + m.com.root + 24 , vel);
	// },{
	// 	m.midiOut.noteOff(m.midiChannel, 60 + m.com.root + 24, vel);
	// });
};

~onMoving = {|state|

	// if(state == true,{
	// 	//Pdef(m.ptn).resume();
	// 	Pdef(m.ptn).set(\amp,0.8);
	// },{
	// 	//Pdef(m.ptn).pause();
	// 	Pdef(m.ptn).set(\amp,0.0);
	// });
};

~onAmp = {|v|
	// TODO
//	v.postln;
};

//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	var oct = ((0.2 + m.rrateMassFiltered.cubed) * 25).mod(3).floor;
	var oo = [24,36,12,0];
	var changeState = {|state|
		if(isOn != state,{
			isOn = state;
			if(isOn == true,{
				"Note ON".postln;
				m.com.root = bl.[0];
				cr = cr.rotate(-1);
				m.midiOut.noteOn(10, 60-oo.choose  + m.com.root, 40);
				{m.midiOut.noteOff(10, 60-oo.choose  + m.com.root, 0)}.defer(0.5);
				bl = bl.rotate(-1);
			},{
				"Note OFF".postln;
			});
		});
	};

	var ga = 0;
	var amp = d.ampValue * 10;

	var div = ((0.2 + m.accelMassFiltered.cubed) * 25).mod(2).floor + 1;
	
	//Pdef(m.ptn).set(\dur,0.16 * (1/2.pow((2-div))));
	var aa = m.accelMassFiltered;

	if( aa < 0.5, { aa = 0});
	Pdef(m.ptn).set(\amp,aa * 0.5);

	if( m.accelMass > 0.8,{
		if( d.ampValue > 0.13,{
			ga = m.accelMass * amp;
		},{
		});
	},{
	});

	if(ga > 0.6,{
		changeState.(true);
	},{
		changeState.(false);
	});


};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 0, m.accelMassFiltered * 127 );
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 1;

~plot = { |d,p|
	// [(m.rrateMassFiltered * 3).ceil.mod(3)];
	//[m.rrateMassFiltered, m.accelMassFiltered, m.com.rrateMass];
	// var changeState = {|state|
	// 	if(isOn != state,{
	// 		isOn = state;
	// 		if(isOn == true,{
	// 			"Note ON".postln;
	// 		},{
	// 			"Note OFF".postln;
	// 		});
	// 	});
	// };

	// var ga = 0;
	// var amp = d.ampValue * 10;
	// if( m.accelMass > 0.3,{
	// 	if( d.ampValue > 0.08	,{
	// 		ga = m.accelMass * amp;
	// 	},{
	// 	});
	// },{
	// });

	// if(ga > 0.2,{
	// 	changeState.(true);
	// },{
	// 	changeState.(false);
	// });

	[m.rrateMassFiltered];
};
//------------------------------------------------------------	
// midi control
//------------------------------------------------------------	
~midiControllerValue = {|num,val|

	//[num,val].postln;

	// if(num == 4,{ threshold = 0.01 + (val * 0.99)});

	// threshold = threshold * 2;
	//m.midiOut.control(m.midiChannel, 65, val * 127 );

};

