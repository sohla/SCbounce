{
	Limiter.ar(
		GVerb.ar(
			sin(
				Ringz.ar(
					Impulse.ar(2**LFNoise0.ar(1!4).range(1,4).round/2 * 2 )
					,[40,200,234,889,1320,150]*MouseX.kr(0.001,18),
					0.1,3
					).sum).distort
			,1
			,
			2,
			MouseY.kr(0.9,0.2)
		)/2 * 0.3
	)
}.play
