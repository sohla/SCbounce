(
{
	var freq = MouseX.kr(36,38, \exponential, 3);
	var sig = Splay.ar( {Gendy1.ar(1,00, 0.001, 1, freq, freq + (freq * MouseY.kr(0.1,0.14)), 0, 0.0, mul: 0.1)}!20).softclip;
	sig = GVerb.ar(sig, 1);
	BLowShelf.ar(sig,400, db:-8)
}.play

)


(

{
	GVerb.ar(
		PitchShift.ar(
			Splay.ar({Dust.ar(MouseX.kr(30,0.2))}!100)
			,0.2
			,0.01
			,mul:4
			),
		MouseX.kr(1.4,1.4, lag:1.2),
		1,
		MouseY.kr(0.51,0.99, lag:1.2)

	)}.play

)

