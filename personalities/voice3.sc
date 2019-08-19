var m = ~model;
var isOn = false;

m.midiChannel = 2;
m.accelMassThreshold = 0.9;
m.rrateMassThreshold = 0.05;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	
~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			//\note, Pseq([0].stutter(4),inf),
			 \note, Pseq([0,1,2,3,4,5,6,7,8].mirror,inf),
			// \root, Pseq([0,3,-4,1].stutter(16),inf),
			// \func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.25);
	Pdef(m.ptn).set(\octave,3);
	Pdef(m.ptn).set(\amp,0.8);
	Pdef(m.ptn).set(\root,0);

	// change notes
	// Pdef(m.ptn,Pbind( 
	// 	\note, Pseq([[0,2,-3,-5],[-1,4,1,7]].stutter(4),inf),
		
Pdef(m.ptn).resume();
	// ));

};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
	// m.com.root.postln;
	// m.com.root = e.root;
	// m.com.dur = e.dur;
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
		//Pdef(m.ptn).resume();
		Pdef(m.ptn).set(\amp,0.8);
	},{
		//Pdef(m.ptn).pause();
		Pdef(m.ptn).set(\amp,0.0);
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

	var oct = ((0.2 + m.rrateMassFiltered.cubed) * 25).mod(3).floor + 1;
(4-oct).postln;
	
	Pdef(m.ptn).set(\dur,0.5 * (1/2.pow((4-oct))));
	// Pdef(m.ptn).set(\dur,(m.rrateMassFiltered * 16).reciprocal);
	// Pdef(m.ptn).set(\octave, [5 + oct]);

	//midiOut.noteOn(7, 60 + note, 90);
};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 3, m.rrateMassFiltered * 127 );
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
