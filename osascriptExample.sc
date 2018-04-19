	(
	var window;
	var tracksPlayed = Dictionary.new;

	var rout = Routine { 
				loop{ 
					tracksPlayed.postln;
					0.1.yield;
				}
			};


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
			.action_({
				var nextTrack;
				{
					nextTrack ="tell application \"iTunes\"
					set prevname to current track
					set secs to player position
					next track
					end tell
					return [name of prevname,secs]".asString.asAppleScriptCmd.unixCmdGetStdOut;
				}.defer;
			
				a = nextTrack.split($,);
				v = tracksPlayed.at(a[0]);
				n = a[1].asFloat;
				v!?{ n = n + v.asFloat;};
				tracksPlayed = tracksPlayed.put(a[0],n);
			});
	, align:\bottom]);

	window.onClose = ({

		rout.stop;
		
		Buffer.freeAll;
		s.freeAll;

	});
	CmdPeriod.doOnce({window.close});

	rout.play;

	)