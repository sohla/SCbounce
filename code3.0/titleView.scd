var addTitleView = { |d|
	var button = Button()
		.minWidth_(100)
		.minHeight_(120)
		.font_(Font(size:48))
		.states_([
			[d.name,Color.black,Color.white.alpha_(0.8)],
			[d.name,Color.black,Color.white.alpha_(0.1)],
		])
		.action_({|but|
			// d.enabled = but.value.asBoolean.not;
			// d.enabled.postln;
			if(but.value == 0, {
				//  NetAddr("127.0.0.1", 57120).sendMsg("/airkit/calibrate", d.port);
				NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/reLoadPersonality", d.port, d.personality);
			},{
				NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/unLoadPersonality", d.port, d.personality);

			});
		});

	OSCFunc({ |msg, time, addr, recvPort|
		if(msg[1].asInteger == d.port, {
			{button.states = [
				[msg[2].asString, Color.black, Color.white.alpha_(0.8)],
				[msg[2].asString, Color.black,Color.white.alpha_(0.1)],
			];
			// button.valueAction = d.enabled.not;
			}.defer;
		});
	},'/airkit/personalityName', NetAddr("127.0.0.1", NetAddr.langPort));
	
	button
};
addTitleView

