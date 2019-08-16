var m = ~model;
var isOn = false;

~rh = 0.25;

m.midiChannel = 0;
m.accelMassThreshold = 0.9;
m.rrateMassThreshold = 0.05;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([0,2,7,9,5,4], inf),
			\root, Pseq([0,5].stutter(6), inf),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur, 1);
	Pdef(m.ptn).set(\octave,[[2,3].choose,[4,5].choose]);
	Pdef(m.ptn).set(\amp,0.8);
		Pdef(m.ptn).resume();

	// change notes
	// Pdef(m.ptn,Pbind( 
	// 	\note, Pseq([[0,2,-3,-5],[-1,4,1,7]].stutter(4),inf),
		

	// ));

};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
	//e.postln;
	m.com.root = e.root;
	m.com.dur = e.dur;
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

	 //var oct = ((0.2 + m.rrateMassFiltered.cubed) * 15).mod(2).floor + 1;
	 var step =  ((0.1 + m.rrateMassFiltered.cubed) * 5).mod(3).floor + 1;

	Pdef(m.ptn).set(\dur, (1/2.pow(step)));
 	Pdef(m.ptn).set(\octave,[[2,3].choose,[4,5].choose]);
 	// Pdef(m.ptn).set(\octave,4);

 // 	Pdef(m.ptn,Pbind( 
 // 		// \note, Pseq([0,2,7,9,5,4].stutter(1),inf),
 // 		\note, Pseq(n,inf),
 // 		// \note, Pseq([10,10,9,7,5,9,7,2,3,5,7,7].stutter(1),inf),
 // 		\dur, Pfunc(rh, inf),
	// 	\octave,3,
	// 	\amp, 0.4
	// ));

};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 1, (m.rrateMassFiltered * 127) );
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
