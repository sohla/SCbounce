var m = ~model;
var isOn = false;
// var bl = [0,-7,-3,-10,-8,-7,-3,-5].stutter(2);
var bl = [0,-7,-5].stutter(2);
m.midiChannel = 6;
m.accelMassThreshold = 0.9;
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

	Pdef(m.ptn).set(\dur,0.5);
	Pdef(m.ptn).set(\octave,5);
	Pdef(m.ptn).set(\amp,0.8);

	// change notes
	Pdef(m.ptn,Pbind( 
		\note, Pseq([-5,0,5,7],inf)
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

	if(state == true,{
		Pdef(m.ptn).resume();
	},{
		Pdef(m.ptn).pause();
	});
};

~onAmp = {|v|
	// TODO
//	v.postln;
};

//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	var oct = ((0.2 + m.rrateMassFiltered.cubed) * 25).mod(2).floor;
	var changeState = {|state|
		if(isOn != state,{
			isOn = state;
			if(isOn == true,{
				"Note ON".postln;
				m.midiOut.noteOn(m.midiChannel, 60-24 + bl[0] + m.com.root, 60);
				{m.midiOut.noteOff(m.midiChannel, 60-24 + bl[0] + m.com.root, 0)}.defer(1);
				bl = bl.rotate(-1);
			},{
				"Note OFF".postln;
			});
		});
	};

	var ga = 0;
	var amp = d.ampValue * 10;


	Pdef(m.ptn).set(\root,m.com.root);

	// Pdef(m.ptn).set(\dur,m.com.dur);
	Pdef(m.ptn).set(\dur,(m.rrateMassFiltered * 7).reciprocal);
	Pdef(m.ptn).set(\amp, 0.4);
	Pdef(m.ptn).set(\octave, 5 + oct);

	//midiOut.noteOn(7, 60 + note, 90);



	if( m.accelMass > 0.3,{
		if( d.ampValue > 0.08	,{
			ga = m.accelMass * amp;
		},{
		});
	},{
	});

	if(ga > 0.2,{
		changeState.(true);
	},{
		changeState.(false);
	});
};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 0, m.rrateMassFiltered * 127 );
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
