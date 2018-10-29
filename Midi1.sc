MIDIClient.init;

m = MIDIOut(0).latency_(Server.default.latency);
m.noteOn(0, 60, 60);
m.noteOff(16, 60, 60);
m.allNotesOff(16);



(
	var midiController, midiOut;
MIDIClient.init;

				MIDIClient.destinations;

				try{
						midiOut = MIDIOut.newByName("IAC Driver","Bus 2",true);
					}{|err|
						midiOut = MIDIOut(0);
					};

					midiOut.latency_(0.01);

				try{
					midiController = MIDIOut.newByName("LPD8","LPD7",true);
				}{|err|
					"Error : LPD8 midi controller not connected".postln;
				};

				midiController!?{midiController.latency_(0.01)};

				MIDIIn.connectAll;

				midiController.postln;
	)