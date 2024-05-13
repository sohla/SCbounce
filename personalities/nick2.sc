var m = ~model;
var cr = [0,-2,3,-4,-5].stutter(5);
var dd = 1;
var pts = [Pseq([0.2],inf),Pseq([0.1],inf),Pseq([0.05],inf),Pseq([1],inf)];
m.midiChannel = 4;
//------------------------------------------------------------
// intial state
//------------------------------------------------------------

~init = ~init <> {

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([0,1,2,3,4,5,6,7,8,9,11,12],inf),
			\func, Pfunc({|e|
				Pbindef(m.ptn, \dur,pts[dd]);
			}),
			\args, #[],
		);
	);
	Pdef(m.ptn).set(\dur,0.2);
	Pdef(m.ptn).set(\octave,3);
	Pdef(m.ptn).set(\amp,0.2);
	Pdef(m.ptn).set(\strum,0.2);


	// Pdef(m.ptn,Pbind(
	// 	\note, Pseq([[-5,0,4],[-5,0,5],[-5,0,2]].stutter(8),inf),
	// 	//	\octave, Prand([5,6,7,8,9],inf),
	// ));

	Pdef(m.ptn).play();


};

//------------------------------------------------------------
// triggers
//------------------------------------------------------------
~onEvent = {|e|
};

~onHit = {|state|

	// var vel = 45;
	// var oct = [-48,-36];
	// var o = 0;
	// var note = 60 + m.com.root + o;
	// if(state == true,{
	//
	// 	cr = cr.rotate(-1);
	// 	m.com.root = cr[0];
	// 	o = oct.choose;
	//
	// 	note = 60 + m.com.root + o;
	// 	m.midiOut.noteOn(10, note, vel);
	// 	{m.midiOut.noteOff(10, note, vel);}.defer(1);
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

	dd = m.accelMassFiltered.floor;

	// Pdef(m.ptn).set(\root,m.com.root);
	Pdef(m.ptn).set(\amp,0.4);
	// Pdef(m.ptn).set(\dur,0.2 * dd.reciprocal );

	// Pdef(m.ptn).set(\strum, 0.4 - (m.rrateMassFiltered * 0.3));
	// Pdef(m.ptn).set(\octave, 2 + (m.rrateMassFiltered * 2).floor);
};

~nextMidiOut = {|d|
	// m.midiOut.control(m.midiChannel, 0, m.accelMassFiltered * 40 );
};

//------------------------------------------------------------
// plot with min and max
//------------------------------------------------------------

~plotMin = 0;
~plotMax = 1;

~plot = { |d,p|
	[m.accelMassFiltered];
};


