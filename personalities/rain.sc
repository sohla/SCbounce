var m = ~model;

var notes = [58,59,60,61,62,63];
var thunder = [57,60,63,59,62];

var slow =0;
m.midiChannel = 12;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

		m.midiOut.noteOn(m.midiChannel, notes[0] , 100);
	// Pdef(m.ptn,
	// 	Pbind(
	// 		\note, Pseq([0],inf),
	// 		// \root, Pseq([0,5,-2,3,-4,1,-5].stutter(16),inf),
	// 		// \func, Pfunc({|e| ~onEvent.(e)}),
	// 		\args, #[],
	// 	);
	// );

	// Pdef(m.ptn).set(\dur,0.5);
	// Pdef(m.ptn).set(\octave,5);
	// Pdef(m.ptn).set(\amp,0.8);
};


//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	

~onEvent = {|e|
	m.com.root = e.root;
	m.com.dur = e.dur;
};

~onHit = {|state|

	var vel = 60;

	if(state == true,{
		thunder = thunder.rotate(-1);
		m.midiOut.noteOn(m.midiChannel + 1 , thunder[0] , vel);
		{
			m.midiOut.noteOff(m.midiChannel + 1, thunder[0] , vel);
		}.defer(0.07)
	},{
	});
};

~onMoving = {|state|

	if(state == true,{
		notes = notes.rotate(-1);
		notes[0].postln;
		m.midiOut.noteOff(m.midiChannel, notes[0] , 0);
		m.midiOut.noteOn(m.midiChannel, notes[0] , 100);
		// Pdef(m.ptn).resume();
	},{
		m.midiOut.noteOff(m.midiChannel, notes[0] , 0);
		// Pdef(m.ptn).pause();
	});
};


//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 
	slow = ~tween.(m.accelMassFiltered * 0.2 * m.rrateMassThreshold.reciprocal, slow, 0.02) ;
	// var oct = ((0.2 + m.rrateMassFiltered.cubed) * 25).mod(3).floor;

	// Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 8).reciprocal);
	// Pdef(m.ptn).set(\amp, 0.9);
	// Pdef(m.ptn).set(\octave, 5 + oct);

};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 13, slow * 127 );
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 2.5;

~plot = { |d,p|
	[m.accelMassAmp,m.accelMassFiltered,slow];
};
//------------------------------------------------------------	
// midi control
//------------------------------------------------------------	
~midiControllerValue = {|num,val|

	//[num,val].postln;

	// if(num == 4,{ threshold = 0.01 + (val * 0.99)});

	// threshold = threshold * 2;
	// midiOut.control(m.midiChannel, num, val * 127 );

};





