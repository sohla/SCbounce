var m = ~model;
var isOn = false;
var cr = [0,2,4,5,7,9];
var slow =0;

m.midiChannel = 0;
m.accelMassAmpThreshold = 0.2;
m.rrateMassThreshold = 0.01;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([0],inf),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.18);
	Pdef(m.ptn).set(\octave,3);
	Pdef(m.ptn).set(\amp,0.9);

	// change notes
	Pdef(m.ptn,Pbind( 
		\note, Pseq([0,1,2,3,4,5,6,7],inf)
	));
	Pdef(m.ptn).play();
};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
	m.com.dur = e.dur;
};

~onHit = {|state|

};

~onMoving = {|state|

	if(state == true,{
		Pdef(m.ptn).set(\amp, 0.9);
	},{
		Pdef(m.ptn).set(\amp, 0.0);
	});
};


//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	var val;
	slow = ~tween.(m.accelMassFiltered * 0.8, slow, 0.1) ;

	val  = ((slow) * 2).floor;

	if(val == 0,{val = 1});
//2.pow(val).reciprocal.postln;
	//oct.postln;
	// Pdef(m.ptn).set(\dur,0.36 * 2.pow(val).reciprocal);
	// Pdef(m.ptn).set(\root,m.com.root);
	//Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 6).reciprocal);
	//Pdef(m.ptn).set(\octave, 3 + oct);

};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 2	, m.accelMassFiltered * 127 );
};			
//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	
~plotMin = 0;
~plotMax = 10;

~plot = { |d,p|

	[ ((slow) * 4).floor];
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
