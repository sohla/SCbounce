var m = ~model;
var bl = [0,5,-2,3,7].stutter(24);
var cr = [0,0,0,2,2,2,-3,-3,-5,-5,-5,-5];
var isOn = false;

m.midiChannel = 5;

//------------------------------------------------------------
// intial state
//------------------------------------------------------------

~init = ~init <> {

	Pdef(m.ptn,
		Pbind(
			\degree, Pseq([0,2,5,4,7,7,9,12,11], inf),
			\root, Pseq([0,4,-2,3].stutter(32),inf),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.5);
	Pdef(m.ptn).set(\amp,0.2);
	Pdef(m.ptn).play();
};

//------------------------------------------------------------
// triggers
//------------------------------------------------------------
~onEvent = {|e|
};
~onHit = {|state|

	// var vel = (90..110).choose;
	// var oct = [48,60];
	// var ch = 9;
	// var note = 60 - oct[0] + cr[0]  + bl[0];
	//
	// if(state == true,{
	//
	// 	//m.com.root = bl.[0];
	// 	cr = cr.rotate(-1);
	// 	bl = bl.rotate(-1);
	// 	oct = oct.rotate(-1);
	//
	// 	note = oct.choose + cr[0]  + bl[0];
	// 	m.midiOut.noteOn(ch, note, vel);
	// 	{m.midiOut.noteOff(ch, note, 0)}.defer(0.08);
	//
	// 	},{
	// });
};

// ~onMoving = {|state|

// 	if(state == true,{
// 		Pdef(m.ptn).resume();
// 	},{
// 		Pdef(m.ptn).pause();
// 	});
// };

//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|


	// Pdef(m.ptn).set(\root,bl[0]);
	Pdef(m.ptn).set(\octave, 2 + (m.rrateMassFiltered * 2).floor);
	Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 2 * m.rrateMassThreshold.reciprocal).reciprocal);

};

~nextMidiOut = {|d|
	// m.midiOut.control(m.midiChannel, 1, m.accelMassFiltered * 40 );
};

//------------------------------------------------------------
// plot with min and max
//------------------------------------------------------------

~plotMin = 0;
~plotMax = 1;

~plot = { |d,p|

	[m.rrateMassFiltered.pow(3)*2, m.accelMassFiltered, m.com.rrateMass];
};


