MIDIClient.init;

m = MIDIOut(0).latency_(Server.default.latency);
m.noteOn(0, 60, 60);
m.noteOff(16, 60, 60);
m.allNotesOff(16);
