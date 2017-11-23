MIDIClient.init;

m = MIDIOut(0);
m.noteOn(16, 60, 60);
m.noteOn(16, 61, 60);
m.noteOff(16, 60, 60);
m.allNotesOff(16);
