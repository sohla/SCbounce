
// unique name for pattern 
var ptn = Array.fill(16,{|i|i=90.rrand(65).asAscii});

var smooth = 0;
var synth;
//------------------------------------------------------------	
// SYNTH DEF
//------------------------------------------------------------	

(
	SynthDef(\gSynth, { |out=0, freq=240, gate=1, amp=0.3, pan=0.0, attack=0.01, sustain=0.5, release=1.3|
	var env = EnvGen.kr(Env.adsr(attack, sustain, sustain, release), gate, doneAction:2);
	var sig = Saw.ar(freq,1.0)!2;
	//var verb = FreeVerb2.ar(sig[0],sig[1],0.3 ,500);
	Out.ar(out, Pan2.ar(sig, pan, env * amp));
}).add;
);

// use to hear this synth onces
//x = Synth(\adamSynth);s.sendBundle(0.5,[\n_set,x.nodeID,\gate,0]);

//------------------------------------------------------------	
// PATTERN DEF
//------------------------------------------------------------	

Pdef(ptn,
	Pbind(
//        \degree, Pseq([0,2,4,6,8,7,5,3,1], inf),
        //\degree, Pseq([0,1,2,0,2,0,2,1,2,3,3,2,1,3,2,3,4,2,4,2,4,3,4,5,5,4,3,5,4,0,1,2,3,4,5,5,1,2,3,4,5,6,6,2,3,4,5,6,7,6,5,5,3,6,4,7,4,3,1], inf),
		\note, Prand([0,2,7],inf),
		\args, #[],
		\amp, Pexprand(0.1,0.4,inf),
		\pan, Pwhite(-0.8,0.8,inf)
));

// use this to test patter/synth with default gui
// Pdef(ptn).play.gui;
// Pdef(ptn).set(\instrument,\adamSynth);
// Pdef(ptn).set(\dur,0.2);
// Pdef(ptn).set(\octave,4);

// Pdef(ptn).set(\attack,0.001);
// Pdef(ptn).set(\sustain,0.27);
// Pdef(ptn).set(\release,0.92);



(

	//------------------------------------------------------------	
	// how ofter does ~next() get called from engine
	//------------------------------------------------------------	
	~secs = 0.03;

	//------------------------------------------------------------	
	// intial state
	//------------------------------------------------------------	
	~init = { |mo|

		"init ADAM".postln;

		Pdef(ptn).set(\instrument,\gSynth);
		Pdef(ptn).set(\dur,0.5);
		Pdef(ptn).set(\octave,5);

		Pdef(ptn).set(\attack,0.001);
		Pdef(ptn).set(\sustain,0.27);
		Pdef(ptn).set(\release,0.92);

		// Pdef(ptn).set(\type,\midi);
		// Pdef(ptn).set(\midiout,mo);
		// Pdef(ptn).set(\chan,2);

		//Pdef(ptn).play;

		synth = Synth(\gSynth);
	};

	//------------------------------------------------------------	
	// do all the work(logic) taking data in and playing pattern/synth
	//------------------------------------------------------------	
	~next = {|d| 
		
//		d.rrateMass = ~tween.(d.rrateEvent.sumabs.half.half.half,d.rrateMass,0.2);

		d.rrateMass = (2.pow(d.rrateEvent.sumabs.div(2.0)).reciprocal).max(0.125);
		smooth = ~tween.(d.rrateEvent.sumabs * 0.1,smooth,0.05);

		// if(smooth < 0.05,{
		// 	Pdef(ptn).stop;
		// 	//Pdef(ptn).set(\dur,0.125*0.5);
		// },{
		// 	if(Pdef(ptn).isPlaying.not,{

		// 		// Pdef(ptn).asStream.next().play();
		// 		Pdef(ptn).play();

		// 	});
		// });

	  	if(Pdef(ptn).isPlaying, {

		 });
				Pdef(ptn).set(\octave,5 + (smooth * 3).floor);
				Pdef(ptn).set(\dur,d.rrateMass);

				synth.set(\freq,200 + (smooth*5000));
				synth.set(\amp,smooth*0.5);

		//Pdef(ptn).set(\attack,(1.0 + d.rrateEvent.sumabs).pow(4).reciprocal);

	 	//Pdef(ptn).set(\dur, Array.geom(5, 1, 2).at( (d.rrateEvent.sumabs.sqrt).floor).reciprocal );

	};

	//------------------------------------------------------------	

	~plot = { |d,p|
		//(smooth * 5).floor.postln;
		//Pdef(ptn).asStream.postln;
		[d.rrateMass,smooth];
	};

	//------------------------------------------------------------	
	// cleanup
	//------------------------------------------------------------	
	~deinit = {

		"deinit ADAM".postln;
		Pdef(ptn).stop;
		synth.free;
		//s.freeAll;
	};

	//------------------------------------------------------------	
	// min and max of plotters output
	//------------------------------------------------------------	

	~plotMin = 0;
	~plotMax = 1;

	

)




// var ptn =Array.fill(16,{|i|i=90.rrand(65).asAscii});

// //------------------------------------------------------------	
// // SYNTH DEF
// //------------------------------------------------------------	

// (
// SynthDef(\help_dwgplucked, { |out=0, freq=440, amp=0.5, gate=1, c3=20, pan=0, position = 0.5 attack = 0.001|
//     var env = Env.new([0,1, 1, 0],[attack,0.006, 0.005],[5,-5, -8]);
//     var inp = amp * LFClipNoise.ar(2000) * EnvGen.ar(env,gate);
//     var son = DWGPlucked.ar(freq, amp, gate,position,1,c3,inp,0.1);
// 	var sig = 0, verb = 0;
//     //Out.ar(out, Pan2.ar(son * 0.1, pan));
// 	sig = Pan2.ar(son * 0.1, pan);
// 	//verb = FreeVerb2.ar(sig[0],sig[1],0.3,200);
//     //DetectSilence.ar(sig, 0.001, doneAction:2);

// 	Out.ar(out,sig);	
// }).add;
// );

// //x = Synth(\help_dwgplucked);s.sendBundle(0.5,[\n_set,x.nodeID,\gate,0]);

// //------------------------------------------------------------	
// // PATTERN DEF
// //------------------------------------------------------------	

// Pdef(ptn,
// 	Pbind(
// //        \degree, Pseq([7,8,2,4,3,1,2,2], inf),
//         //\degree, Pseq([0,1,2,3,4,5,6,7], inf),
// 		\note, Prand([0,2,5,7,9],inf),
// //        \degree, Pseq([0,4,2,7,1,2,3,5], inf),
// 		\args, #[],
// 		\amp, Pexprand(0.1,0.4,inf),
// 		\pan, Pwhite(-0.8,0.8,inf)
// ));

// //x = Synth(\help_dwgplucked);s.sendBundle(0.5,[\n_set,x.nodeID,\gate,0]);


// // Pdef(ptn).set(\instrument,\help_dwgplucked);
// // Pdef(ptn).play;
// // Pdef(ptn).set(\attack,0.01);
// // Pdef(ptn).set(\release,4.0);

// // Pdef(ptn).set(\octave,7);
// // Pdef(ptn).set(\dur,0.2);

// //------------------------------------------------------------	
// // PERSONALITY
// //------------------------------------------------------------	
// (

// 	~secs = 0.03;

// 	//------------------------------------------------------------	
// 	~init = { |mo|

// 		"init GEORGE".postln;

// 		Pdef(ptn).set(\octave,5);
// 		Pdef(ptn).set(\dur,0.5);
// 		Pdef(ptn).set(\attack,1.5);
// 		Pdef(ptn).set(\c3,50);
// 		Pdef(ptn).set(\legato,10);
// 		Pdef(ptn).set(\instrument,\help_dwgplucked);

// 		Pdef(ptn).set(\type,\midi);
// 		Pdef(ptn).set(\midiout,mo);
// 		Pdef(ptn).set(\chan,2);

// 		Pdef(ptn).play;
// 	};

// 	//------------------------------------------------------------	
// 	~next = {|d| 
		

// 		// ["running GEORGE...",f%7].postln;
// 		// d.postln;
// 		//Pdef(ptn).set(\octave,1 + (f%7));

// 			d.accelMass = ~tween.(d.accelEvent.sumabs.half,d.accelMass,0.08);

// 			d.rrateMass = ~tween.(d.rrateEvent.sumabs.half / 3.0,d.rrateMass,0.9);

// 			if(d.rrateMass < 0.09,{
// 				Pdef(ptn).pause;
// 			},{
// 				if(Pdef(ptn).isPlaying.not,{Pdef(ptn).resume});
// 			});

// 		 // 	// set pattern
// 		  	if(Pdef(ptn).isPlaying, {

// 				//Pdef(ptn).set(\patch,((gyroEvent.pitch + pi).div(pi.twice/4.0)).floor);
				
// 				// Pdef(ptn).set(\gtranspose,-3 + [0,12,24].at(((d.gyroEvent.roll + pi).div(pi.twice/3.0)).floor));

// 				Pdef(ptn).set(\c3,(12 + ((d.gyroEvent.roll + pi)/(pi.twice) * 500)));
				
// 				Pdef(ptn).set(\legato,(0.1 + ((d.gyroEvent.yaw + pi)/(pi.twice) * 3)));
				
// 				Pdef(ptn).set(\position,(0.0 + ((d.gyroEvent.yaw + pi)/(pi.twice) * 1.0)));

// 			 });
			
// 			 if(Pdef(ptn).isPlaying, {

// 				Pdef(ptn).set(\attack,(1.0 + d.rrateEvent.sumabs).pow(4).reciprocal);

// 			 	Pdef(ptn).set(\dur,Array.geom(8, 1, 2).at((d.rrateEvent.sumabs.sqrt).floor).half.reciprocal);
			
// 			 });

// 	};

// 	//------------------------------------------------------------	
// 	~deinit = {

// 		"deinit GEORGE".postln;
		
// 		Pdef(ptn).stop;
// 	};
// 	//------------------------------------------------------------	
// 	// min and max of plotters output
// 	//------------------------------------------------------------	

// 	~plotMin = 0;
// 	~plotMax = 1;

// 	//------------------------------------------------------------	
// 	// utility for output to a plotter : returns a value that
// 	// that will be put at the end of plotters data array
// 	//------------------------------------------------------------	

// 	~plot = { |d,p|
// 		//[0,12,24].at(((d.gyroEvent.roll + pi).div(pi.twice/3.0)).floor);
// 		//(10 + ((d.gyroEvent.roll + pi)/(pi.twice) * 100));
// 		//(0.1 + ((d.gyroEvent.yaw + pi)/(pi.twice) * 10));
// 		//Array.geom(8, 1, 2).at((d.rrateEvent.sumabs.sqrt.half).floor).twice.reciprocal;
// 		//-3 + [0,12,24].at(((d.gyroEvent.roll + pi).div(pi.twice/3.0)).floor);
// 		0.2;
// 	};

// );
