var m = ~model;
var d = ~device;

var isOn = false;
var cr = [0,2,4,5,7,9];
var slow =0;

var oldVar = 0;
var rate = 0;	
var rateFiltered = 0;

m.midiChannel = 0;
m.accelMassAmpThreshold = 0.2;
m.rrateMassThreshold = 0.01;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,Pseq([0.18],inf));
	Pdef(m.ptn).set(\octave,3);
	Pdef(m.ptn).set(\amp,0.9);

	// change notes
	Pdef(m.ptn,Pbind( 
		\func, Pfunc({|e| ~onEvent.(e)}),
		\note, Pseq([0,7,10,0,7,12,4,7,10].stutter(4),inf),
		\dur, Pseq([0.18], inf)
	));
};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|

	var width = d.blob.rect.width;


	e.postln;


Pdef(m.ptn).set(\amp,rateFiltered.linlin(0,1.0,0.1,1.0));
	// if( d.blob.area > 52,{
	// 	m.midiOut.control(m.midiChannel, 0, 1 );
	// },{
	// 	m.midiOut.control(m.midiChannel, 0, 0 );
	// });
	// m.midiOut.control(m.midiChannel, 1, d.blob.rect.width.linlin(100,220,0,127) );
	// m.midiOut.control(m.midiChannel, 2, d.blob.rect.top.linexp(0,100,127,80) );
	// m.midiOut.control(m.midiChannel, 3, d.blob.rect.top.linlin(0,80,127,0) );
	// m.midiOut.control(m.midiChannel, 4, d.blob.rect.width.linexp(100,220,20,127) );


	
};

~onHit = {|state|

	if(state == true,{
		Pdef(m.ptn).resume();
	},{
		Pdef(m.ptn).pause();

	});

};

~onMoving = {|state|

	// if(state == true,{
	// 	Pdef(m.ptn).set(\amp, 0.9);
	// },{
	// 	Pdef(m.ptn).set(\amp, 0.0);
	// });
};


//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 




	// data[0].postln;

	// var data = d.blob.data.reshape(d.blob.data.size,2);
	// var nd;
	// data = data.sort({|a,b| a[1] > b[1]});


	// nd = data.copyRange(0, (d.blob.data.size/4).asInteger);

	// nd.postln;




	//center.x * 1.6 : 1.6 = 640 (pixels from cam) / 400 (pixels of view)
	m.accelMassAmp = d.blob.rect.width + (d.blob.center.x * 1.66) + d.blob.perimeter;
	// if(m.isHit,{



		// m.accelMassAmp = d.blob.area + d.blob.perimeter + (d.blob.center.x * 1.66) + (d.blob.center.y * 2.3);

		// m.accelMassAmp = m.accelMassAmp * 0.33;

		rate = ((m.accelMassAmp - oldVar) * 10.0);

		if(rate < 0){rate = 0};

		rateFiltered = ~tween.(rate, rateFiltered, 0.05) ;

		oldVar = m.accelMassAmp;






	// },{

	// 	rateFiltered = ~tween.(rate, rateFiltered, 0.04) ;

	// });

};

~nextMidiOut = {|d|

	// if(m.isHit,{
	// 	d.blob.rect.top.postln;
	// 	m.midiOut.control(m.midiChannel, 1, d.blob.rect.top.linlin(80,25,0,127) );
	// });

};
/*
		var blobProto = (
			\index: 0,
			\dataSize: 3,
			\area: 0,
			\perimeter: 0,	
			\center: Point(0,0),
			\rect: Rect(0,0,20,20),
			\label: 0,
			\data: [[0,0]],
		);
*/			
//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	
~plotMin = 0;
~plotMax = 2.0;

~plot = { |d,p|

	// [d.blob.area /  -10000000, d.blob.perimeter / 500000, d.blob.center.x /  (280 * 1000), d.blob.center.y / (200 * 1000)];
	
	// [d.blob.area , d.blob.perimeter, d.blob.center.x * 1.66 , d.blob.center.y * 2.3];
	// [d.blob.rect.width, (d.blob.center.x * 1.66), m.accelMassAmp, rate];
	[rate, rateFiltered ];

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





