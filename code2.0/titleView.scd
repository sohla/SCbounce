var addTitleView = { |d|
	var st = StaticText()
        .stringColor_(d.color)
        .font_(Font(size:48))
				.background_(Color.white.alpha_(0.1))
        .minWidth_(100)
        .minHeight_(120)
				.align_(\center)
        .string_(d.name);

	OSCFunc({ |msg, time, addr, recvPort|
		if(msg[1].asInteger == d.port, {
			{st.string = msg[2].asString}.defer;
		});
	},'/airkit/personalityName', NetAddr("127.0.0.1", NetAddr.langPort));
	
	st
};
addTitleView

