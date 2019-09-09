(
var window;
var channel = 0;
var voice = 0;
Speech.init(2);
Speech.setSpeechVoice(channel, 0);
Speech.setSpeechRate(channel, 100);
Speech.wordAction_({|a|
	"word".postln;
});
window = Window("")
	.bounds_(Rect(
		0,0,
		Window.screenBounds.width/2,
		Window.screenBounds.height/2)
		.center_(Window.availableBounds.center)
	)
	.front;

window.layout = VLayout(
		TextField()
			.string_("")
			.align_(\center)
			.font_(Font(size:48))
			.stringColor_(Color.red)
			.focusGainedAction_({|a|a.string ="";})//always clear
			.action_({|a|
				Speech.setSpeechVoice(channel, voice);
				Speech.channels[channel].speak(a.string);
				a.string = "";
			})
);


window.onClose = ({
	Speech.stop(channel);
	Buffer.freeAll;
	s.freeAll;

});
CmdPeriod.doOnce({window.close});


)


