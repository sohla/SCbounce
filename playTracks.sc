	(
	var window;
	var tracksPlayed = Dictionary.new;

	var fadeIn = Task { 

		var vol = 0;

		loop{ 
			
			("tell application \"iTunes\"
			set the sound volume to "++vol.asString++"
			end tell").asString.asAppleScriptCmd.unixCmdGetStdOut;

			vol = vol + 5;

			if(vol > 100,{ fadeIn.stop;});
			0.1.wait;
		}
	}; 

	var fadeOut = Task { 

		var vol = 100;

		loop{ 
			
			("tell application \"iTunes\"
			set the sound volume to "++vol.asString++"
			end tell").asString.asAppleScriptCmd.unixCmdGetStdOut;

			vol = vol - 5;

			if(vol < 0,{ fadeOut.stop;});
			0.1.wait;
		}
	}; 

	var rout = Routine { 
				loop{ 
					
					e = tracksPlayed.invert;
					
					f = e.order;
					f!?{
						g = f.reverse;
						e.atAll(g).postln;

					};
					82.0.yield;
					fadeOut.start;
					2.0.yield;
					sendFunc.();
					2.0.yield;
					fadeIn.start;

				}
			};
	var noteOnFunc = MIDIFunc.noteOn({sendFunc.()});

	var sendFunc = ({
		var nextTrack;
		{
			nextTrack ="tell application \"iTunes\"
			set prevname to current track
			set secs to player position
			next track
			end tell
			return [name of prevname,secs]".asString.asAppleScriptCmd.unixCmdGetStdOut;
			//nextTrack.postln;
			a = nextTrack.split($,);
			v = tracksPlayed.at(a[0]);
			n = a[1].asFloat;
			v!?{ n = n + v.asFloat;};
			tracksPlayed = tracksPlayed.put(a[0],n);
		}.defer;
	});

	MIDIClient.init;
	MIDIIn.connectAll;


	window = Window("")
		.bounds_(Rect(
			0,0,
			Window.screenBounds.width/4,
			Window.screenBounds.height/8)
			.center_(Window.availableBounds.center)
		)
		.front;

	
	window.layout = VLayout([
		Button()
			.states_([["next"]])
			.maxWidth_(50)
			.action_({ sendFunc.() });
	, align:\bottom]);

	window.onClose = ({

		noteOnFunc.free;
		MIDIIn.disconnectAll;

		rout.stop;

		Buffer.freeAll;
		s.freeAll;

	});
	CmdPeriod.doOnce({window.close});

	rout.play;

	)
//[ Not Pretty Enough, Shed, Applause, Heroes, Baltimore, Killing In the Name, Make Me Feel, Round We Go, Lucky Girl, Dancing On My Own, A Roller Skating Jam Named 'Saturdays, Hopelessness ]

