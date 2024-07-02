var m = ~model;
var isOn = false;
// var bl = [0,-7,-3,-10,-8,-7,-3,-5].stutter(2);
var bl = [0,4,-2,7].stutter(2);
var cr = [0,5,0,5].stutter(8);

m.midiChannel = 11;

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
	Pdef(m.ptn).set(\octave,4);
	Pdef(m.ptn).set(\amp,0.8);

	// change notes
	Pdef(m.ptn,Pbind( 
		// \note, Pseq([0,1,2,3,4,5,6,7,8,7,6,5,4,3,2,1],inf)
		// \note, Pseq([0,0,0,0,7,7,9,10,10].mirror,inf),
		\note, Pseq([12,12,12,12,10,10,10,5,5,5,5,5,3,3,3,7,7,7].mirror,inf),
		// \root, Pseq([0,5].stutter(18*8),inf)
	));
	Pdef(m.ptn).play();

};
//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};

~onHit = {|state|

	var oo = [36,48];
	var note = 60-oo.choose + m.com.root + cr[0];

	if(state == true,{
	
		m.com.root = bl.[0];
		cr = cr.rotate(-1);

		note = 12 + 60-oo.choose + m.com.root + cr[0];
		m.midiOut.noteOn(10, note, 60);
		{m.midiOut.noteOff(10, note, 0)}.defer(0.05);
		bl = bl.rotate(-1);

	},{

	});
};



//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	var oct = ((0.2 + m.rrateMassFiltered.cubed) * 25).mod(3).floor;
	var div = ((0.2 + m.accelMassFiltered.cubed) * 25).mod(2).floor + 1;
	
	var aa = m.accelMassFiltered;
	var amp = (aa  * (3.pow(m.rrateMassThreshold * 4 + 1) + 1).reciprocal * 20);

	var id = m.rrateMassFiltered.linlin(0,2.5,0,3).floor.asInteger;
	var bs = [0.14*2, 0.14, 0.14 * 0.5];
	var dd = bs[m.rrateMassFiltered.linlin(0,2.5,0,3).floor.asInteger];

	Pdef(m.ptn).set(\dur,dd);

	if( amp < m.rrateMassThreshold, { amp = 0});
	Pdef(m.ptn).set(\amp, amp);
	// Pdef(m.ptn).set(\root,cr[0]);



	// if(m.rrateMassFiltered > =2,{
	// 	Pdef(m.ptn).set(\dur,0.14 * 0.5);
	// });

	// if(m.rrateMassFiltered > 1 && m.rrateMassFiltered < 2,{
	// 	Pdef(m.ptn).set(\dur,0.14 * 2);
	// });

	// if(m.rrateMassFiltered > 2,{
	// 	Pdef(m.ptn).set(\dur,0.14 * 0.5);
	// });


};
~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 0, m.accelMassFiltered * 127 );
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 4;

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

	var dd = m.rrateMassFiltered.linlin(0,2.5,0,3).floor.asInteger;
	[m.rrateMassFiltered, dd];
};


