(
{
	var strike, env, noise, pitch, delayTime, detune, sig;
	strike = Impulse.ar(0.01);
	pitch = (36 + 24.rand);
	env = Decay2.ar(strike, 0.001, 0.02, BrownNoise.ar(3).tanh);


	sig = Pan2.ar(
			// array of 3 strings per note
			Mix.ar(Array.fill(3, { arg i;
				// detune strings, calculate delay time :
				detune = #[-0.06, 0, 0.04].at(i);
				delayTime = 1 / (pitch + detune).midicps;
				// each string gets own exciter :
			noise = LFNoise2.ar((pitch+48).midicps, env); // 3000 Hz was chosen by ear..
				CombL.ar(noise,		// used as a string resonator
					delayTime, 		// max delay time
					delayTime,			// actual delay time
					2,
				1,
				SinOsc.ar(pitch.midicps*0.5,0, Decay2.ar(strike,0.03,pitch.linexp(36,100,1,0.1))*0.5);
			) 				// decay time of string
			})),
			(pitch - 23)/24 - 1 // pan position: lo notes left, hi notes right
		,0.2
		);
	DetectSilence.ar(sig, doneAction: Done.freeSelf);
	sig

}.play

)
