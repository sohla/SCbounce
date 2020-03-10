var m = ~model;
var bl = [0,5,-2,3,7].stutter(22);
var cr = [0,0,0,2,2,2,-3,-3,-5,-5,-5,-5];
var isOn = false;

m.midiChannel = 7;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
	        \degree, Pseq([0,2,5,4,7,7,9,12,11], inf),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.5);
	Pdef(m.ptn).set(\amp,0.2);
};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};
~onHit = {|state|
	
	var vel = (40..60).choose;
	var oct = [12,24,17,36,48];
	var ch = 4;
	var note = 60 - oct[0] + cr[0]  + bl[0];

	if(state == true,{

		//m.com.root = bl.[0];
		cr = cr.rotate(-1);
		bl = bl.rotate(-1);
		oct = oct.rotate(-1);
		oct.postln;

		note = 60 - oct.choose + cr[0]  + bl[0];
		m.midiOut.noteOn(ch, note, vel);
		{m.midiOut.noteOff(ch, note, 0)}.defer(0.08);

	},{
	});
};

~onMoving = {|state|

	if(state == true,{
		Pdef(m.ptn).resume();
	},{
		Pdef(m.ptn).pause();
	});
};

//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 


	Pdef(m.ptn).set(\root,bl[0]);
	Pdef(m.ptn).set(\octave, 3 + (m.rrateMassFiltered * 2).floor);
	Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 2 * m.rrateMassThreshold.reciprocal).reciprocal);

};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 7, m.accelMassFiltered * 40 );
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 1;

~plot = { |d,p|

	[m.rrateMassFiltered.pow(3)*2, m.accelMassFiltered, m.com.rrateMass];
};
//------------------------------------------------------------	
// midi control
//------------------------------------------------------------	
~midiControllerValue = {|num,val|

};


