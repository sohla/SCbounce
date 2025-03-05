(
Event.addEventType(\customEvent, {|e|
	~note.postln;
	100.do{|i|i.post};
	// ~instrument = \stereoSampler;
    ~type = \note;
    currentEnvironment.play;
});

)
(
Pdef(\tester,
	Pbind(
		\type, \customEvent,
		\dur, 0.1,
		\octave, Pwhite(3,6),
		\note, Pxrand([0,4,7,11], inf),
		)
	).play;
)
