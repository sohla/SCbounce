(

	//------------------------------------------------------
	// GLOBALS
	//------------------------------------------------------

	var vlPath = PathName.new("~/Music/VoiceLab");
	var image = Image.new("~/Music/VoiceLab/polyglot.png".asAbsolutePath);

	var sessionData = Dictionary.new;
	var sessionTitle;
	
	// contect locally so we can send msgs
	var broadcaster = NetAddr("127.0.0.1", NetAddr.langPort);

	var childRecordSynth, childRecordBuffer;
	var opMicSynth, opRecorderSynth, opRecordBuffer;
	var listenSynth;
	var pluginSynth;

	var sessionRecorderSynth, sessionRecorderBuffer;
	var backgroundSynth, backgroundBuffer;


	var playbackGroup;
	var backgroundGroup;
	//var childMicGroup;
	var opMicGroup;
	var listenGroup;

	var levelIndicator;
	var gainSpec = ControlSpec(0.ampdb, 10.ampdb, \db, step: 0.5, default: 3, units: " dB");
	var monitorSpec = ControlSpec(0.ampdb, 4.ampdb, \db, step: 0.5, default: 6, units: " dB");
	var childBGMusicLevel = 0.35;
	var opBGMusicLevel = 0.25;

	var arduino;
	var say;


	var pluginIndex = 0;
	var pluginNames = #["OFF","pitchme","shiftme","ringme"];


	var lxDepth = 0.7;
	var lxSpeed = 0.5;
	var lxBrightness = 1.0;
	var lxAuto = true;
	var diameter = 380;
	var radius = diameter / 2;
	var fix = 0.785;
	var origin = Point(radius,radius);
	var numOfPixels = diameter * diameter;
	var point = Point(radius,radius);
	var pointDelta = Point(radius,radius);
	var size = 30;
	var angle = 0;
	var colorView = UserView().resize_(5).bounds_(Rect(0,0,diameter,diameter));
	var slider2DView = UserView().resize_(5);

	// var lxX=0,lxY=0;
	// var tx=0,ty=0;
	//------------------------------------------------------
	// FUNCTIONS
	//------------------------------------------------------
	var createSession, loadTemplate;
	var toConsole;
	var playQuestion;
	var calculateColorForPoint,tween;

	var startRecordingSession, stopRecordingSession;

	var stopChildRecorder,startChildRecorder;
	var initGUI, refreshGUI;
	var startRecordingQuestion,stopRecordingQuestion;
	var connectArduino, disconnectArduino;

	var bgMusicStart, bgMusicStop, bgRoutine;

	var fps = 1.0/30.0;


	QtGUI.palette = QPalette.dark;
	// setup dummy datastore
	sessionData.put("keyPaths",Dictionary.new);


	//--------------------------------------------------------------------------------
	// ARDUINO
	//--------------------------------------------------------------------------------

	connectArduino = {
		try{
			arduino = RduinoDMX(SerialPort.devices.last,115200);
		}{|error|
			[\Connecting:error.errorString].postln;
		};
	};

	disconnectArduino = {
				arduino!?{
					3.do({|i|arduino.dmxc_(i+1,0,0,0)});
					arduino.close;
				};

	};

	//--------------------------------------------------------------------------------
	// SESSION
	//--------------------------------------------------------------------------------

	//------------------------------------------------------
	loadTemplate = { |path,completefunc,errorfunc|

		// load a template (json)
		var jsonFile;
		var templateData;
		var fullPath = vlPath.asAbsolutePath+/+"template.json";

         if( File.exists(fullPath) , {
			jsonFile = File(fullPath,"r");
			templateData = jsonFile.readAllString.parseYAML;
			jsonFile.close;
			templateData.writeArchive(path);
			//get list of template sounds
			SoundFile.collect(vlPath.asAbsolutePath+/+"templateQuestions/*").do{ |f,i|
				var newPath = vlPath.asAbsolutePath+/+path.asRelativePath(vlPath.absolutePath).dirname+/+"Questions";
				SoundFile.normalize(f.path,newPath+/+f.path.basename,threaded:false);
			};
			// load  session data
			sessionData = Object.readArchive(path);
			completefunc.();
			// return
			path;
		},{
			errorfunc.();
		});
	};

	//------------------------------------------------------

	//------------------------------------------------------
	createSession = {

		var date = Date.getDate;
		var genTitle= date.format("%A_%d:%m:%Y-%H_%M_%S");
			// create all the dirs
		var sessionDir = File.mkdir(vlPath.asAbsolutePath+/+genTitle);
		var quesiotnsDir = File.mkdir(vlPath.asAbsolutePath+/+genTitle+/+"Questions");
		var answersDir = File.mkdir(vlPath.asAbsolutePath+/+genTitle+/+"Answers");
		var sessionArchivePath = vlPath.asAbsolutePath+/+genTitle+/+"session.vls";

		// create empty datastore
		var templateData = Dictionary.new;
		templateData.put("keyPaths",Dictionary.new);

		// save data as achive inside session dir
		templateData.writeArchive(sessionArchivePath);
		sessionTitle = sessionArchivePath.asRelativePath(vlPath.absolutePath).dirname;

		// load  session data
		sessionData = Object.readArchive(sessionArchivePath);

		// build groups
		//childMicGroup = Group.new;
		opMicGroup  = Group.new;
		playbackGroup = Group.new;

		toConsole.("session created");

		// return path
		sessionArchivePath;
	};


	//------------------------------------------------------
	startRecordingSession = {

		// add all stereo recording from operators buses on default_group
		sessionRecorderBuffer = Buffer.alloc(s,65536,2);
		sessionRecorderSynth = Synth.after(1,\recordSession,["bufnum", sessionRecorderBuffer.bufnum]);

		g = Date.getDate.format("%A_%d:%m:%Y-%H_%M_%S");
		t = "VL_Session["++g++"].wav";
		p = vlPath.asAbsolutePath+/+sessionTitle;

		sessionRecorderBuffer.write(p+/+t,"wav","int16", 0, 0, true);

		toConsole.("Recording Session"+t);

	};

	//------------------------------------------------------
	stopRecordingSession = {

		if(sessionRecorderBuffer != nil,{
			sessionRecorderBuffer.close;
			sessionRecorderBuffer.free;
			sessionRecorderBuffer = nil;
			sessionRecorderSynth.free;
		});

	};

	//------------------------------------------------------
	// UTILS
	//------------------------------------------------------

	say = ({|s|
		if(false,{s.speak});
	});

	toConsole = ({ |str|
		
		("----------------------------------").postln;
		(">>"+str).postln;
		("----------------------------------").postln;
		s.queryAllNodes;
	});

	//--------------------------------------------------------------------------------
	// RECORD and PLAYBACK
	//--------------------------------------------------------------------------------

	//------------------------------------------------------
	stopChildRecorder = ({

		if(childRecordBuffer != nil,{
			childRecordBuffer.close;
			childRecordBuffer.free;
			childRecordBuffer = nil;
			childRecordSynth.free;
		});

		toConsole.("Stop recording answer");
	});

	//------------------------------------------------------
	startChildRecorder = ({ |title|

		// record kid
		childRecordBuffer = Buffer.alloc(s,65536,1);
		childRecordSynth = Synth.after(listenGroup,\recordChild,["bufnum", childRecordBuffer.bufnum]);

		g = Date.getDate.format("%H_%M_%S");
		t = title;
		t = "AnswerTo["++t++"]_At_["++g++"].wav";
		p = vlPath.asAbsolutePath+/+sessionTitle+/+"Answers";

		childRecordBuffer.write(p+/+t,"wav","int16", 0, 0, true);

		toConsole.("Recording "++t);

	});

	//------------------------------------------------------
	playQuestion = ({ |path, completionFunc|

		b = Buffer.read(s, path, action:{

			a = Synth.head(playbackGroup,\playBuffer,[\buffer,b,\amp,0.8]);

			if( pluginIndex > 0, {
				pluginSynth = Synth.after(a,pluginNames[pluginIndex]);
				toConsole.("Plugin"+pluginNames[pluginIndex]+"added");
			},{
			});

			stopChildRecorder.();
			toConsole.("Play Question");

			a.onFree({

				if( pluginSynth != nil, {
					pluginSynth.free;
				},{
				});

				{
					completionFunc.();
					toConsole.("on /playBuffer synth free");

				}.defer;
			});
		});
	});
	//------------------------------------------------------
	startRecordingQuestion = ({ |title|

		opMicSynth = Synth.head(opMicGroup,\opMicInput,["channel",0]);

		if( pluginIndex > 0, {
			pluginSynth = Synth.after(opMicSynth,pluginNames[pluginIndex]);
			toConsole.("Plugin"+pluginNames[pluginIndex]+"added");
		},{
		});

		opRecordBuffer = Buffer.alloc(s,65536,1);

		p = vlPath.asAbsolutePath+/+sessionTitle+/+"Questions";

		opRecordBuffer.write(p+/+title,"wav","int16", 0, 0, true);
		opRecorderSynth = Synth.after(opMicSynth,\diskOut, ["bufnum", opRecordBuffer.bufnum]);
		toConsole.("Recording"+title);

	});

	//------------------------------------------------------
	stopRecordingQuestion = ({

		if( pluginIndex > 0, {
			pluginSynth.free;
		},{
		});


		if(opRecordBuffer != nil,{

			 opRecordBuffer.close;
			 opRecordBuffer.free;
			 opRecordBuffer = nil;
			
			opRecorderSynth.free;
			opMicSynth.free;
		
		});

		toConsole.("Recorded qustion free");
	});

	//--------------------------------------------------------------------------------
	// BG MUSIC
	//--------------------------------------------------------------------------------


	//------------------------------------------------------
	bgMusicStart = ({

		var path = "~/Music/VoiceLab/Backing Atmos - complex.aif".standardizePath;

		backgroundBuffer = Buffer.read(s, path, action:{
			backgroundGroup = Group.new;
			backgroundSynth = Synth.head(backgroundGroup,\playBG,[\buffer,backgroundBuffer]);
		});

		 toConsole.("Start background music");

	 });

	//------------------------------------------------------
	bgMusicStop = ({


		if(backgroundBuffer != nil,{

			 backgroundBuffer.close;
			 backgroundBuffer.free;
			 backgroundBuffer = nil;
			
			backgroundSynth.free;
			backgroundGroup.free;
		
		});

		 toConsole.("Stop background music");

	});

	//--------------------------------------------------------------------------------
	//--------------------------------------------------------------------------------



	//--------------------------------------------------------------------------------
	// GUI
	//--------------------------------------------------------------------------------

	pointDelta.addUniqueMethod(\tween,{|self,point|
		var friction = 0.05;
		self.x = (friction * point.x) + ((1 - friction) * self.x);
		self.y = (friction * point.y) + ((1 - friction) * self.y);
	});

	//--------------------------------------------------------------------------------
	// COLOUR WHEEL
	//--------------------------------------------------------------------------------

	slider2DView.mouseDownAction = ({|v,x,y|
		var dist = origin.dist(Point(x,y));
		if((dist <= (radius * fix)),{point.set(x,y)},{});

	});

	slider2DView.mouseMoveAction = slider2DView.mouseDownAction;
	slider2DView.background = Color.clear;

	slider2DView.drawFunc = {

		Pen.strokeColor = Color.white;
		Pen.fillColor = calculateColorForPoint.(pointDelta.x,pointDelta.y,lxBrightness);

		Pen.fillColor = Color.gray(0.0, 1.0-lxBrightness);

		Pen.translate(pointDelta.x,pointDelta.y);

		Pen.addOval(Rect(size.half.neg,size.half.neg,size,size));
		Pen.fillStroke;
		//Pen.translate(pointDelta.x.neg,pointDelta.y.neg);

	};

	//--------------------------------------------------------------------------------
	//
	//--------------------------------------------------------------------------------
	colorView.background_(Color.clear);
	colorView.drawFunc = ({
		
		numOfPixels.do{|i|
			var j = i % diameter;
			var k = floor(i / diameter);
			Pen.strokeColor = calculateColorForPoint.(j,k,1.0);
			Pen.moveTo(j@k);
			Pen.lineTo(j+1@k);
			Pen.stroke;

		};
	});

	//--------------------------------------------------------------------------------
	//
	//--------------------------------------------------------------------------------


	calculateColorForPoint = ({|x,y,z|

		var ang = atan2(x - radius, y - radius);
		var hue = ((ang + pi) / 2pi);
		var sat = origin.dist(Point(x,y)) / (radius-40);
		var b = 1.0;

		if( sat > 1.25, {hue = 1.0;z=0;},{});
		if( sat > 1.25, {sat = 1.0;},{});

		if( hue > 0.999, {hue = 0.0},{});
		if( sat > 0.999, {sat = 1.0;b=0;},{});

		Color.hsv(hue,sat,z,b);
	});

	//--------------------------------------------------------------------------------
	//
	//--------------------------------------------------------------------------------
	initGUI = ({

		var window, mainView;
		var loadView, sessionView, errorView, errorText;
		var onPlayLevel;

		var scale = 1.0;
		var listView;
		var pluginView;
		var autoBtn;

		var newSessionButton, templateSessionButton;

		//------------------------------------------------------
		newSessionButton = Button()
					.states_([["New Session"]])
					.minWidth_(300)
					.minHeight_(50)
					.focusGainedAction_({|b|
						b.states = [["Empty Session"]];
						b.refresh;
					})
					.action_({|b|
						b.states = [["creating new session files...",Color.new255(226, 49, 140)]];
						b.refresh;
						say.("New, Voice Lab Session");
						pluginIndex = 0;
						pluginView.value = pluginIndex;
						mainView.index = 1;
						{createSession.();}.defer(1);
					});

		//------------------------------------------------------
		templateSessionButton = Button()
					.maxHeight_(100)
					.states_([["Template Session"]])
					.minWidth_(300)
					.minHeight_(50)
					.focusGainedAction_({|b|
						b.states = [["Template Session"]];
						b.refresh;
					})
					.action_({|b|
						b.states = [["processing session files...",Color.new255(226, 49, 140)]];
						b.refresh;
						say.("Creating Voice Lab Session from Template");
						pluginIndex = 0;
						pluginView.value = pluginIndex;
						{
						loadTemplate.(createSession.(),{
							mainView.index = 1;
							refreshGUI.();
							//bgMusicStart.();
							startRecordingSession.();
						},{
								errorText.string = "Can't seem to find template files.";
								mainView.index = 2;

						})
						}.defer(1)

					});
		//------------------------------------------------------
		errorView = ({
			View().layout_( VLayout(

				StaticText().string_("Oops").align_(\center).font_(Font(size:48)),
				errorText = StaticText().string_("-").align_(\center).font_(Font(size:24)),

				[Button()
					.states_([["Ok"]])
					.action_({|b|
						mainView.index = 0;
					})
					.minWidth_(400)
					.minHeight_(70)
					, align:\center]
			))
		});


		//------------------------------------------------------
		loadView = ({

			View().layout_( VLayout(
				StaticText().string_("Welcome to VoiceLab").align_(\center).font_(Font(name:"Avenir", bold:true ,size:48)),
				[newSessionButton, align:\center],
				[templateSessionButton, align:\center],
				StaticText().string_("Polyglot 2017 : v3.0").align_(\center).font_(Font(name:"Avenir",size:12)),

				)
			);

		});

		//------------------------------------------------------
		sessionView = ({

			var btnHeight = 80,levelStack;
			var recordedQuestionTitle,spokenQuestionTitle;
			var prevButton, playButton, nextButton;
			var lxControls, recordingControl;
			var speakButton, pluginMenu, exitButton;

			var gainSlider, monitorSlider;
			var gainLabel, monitorLabel;

			//------------------------------------------------------
			prevButton = Button()
						.minHeight_(btnHeight)
						.states_([["▲"]])
						.action_({|v|
							if(listView.value > 0,{listView.value = listView.value - 1});
						});

			//------------------------------------------------------
			playButton = Button()
						.minHeight_(btnHeight)
						.states_([["PLAY"],["playing..", Color.white,Color.green]])
				        .action_({ arg butt;

							if(listView.items.size > 0 && butt.value == 1,{
								//••window.view.enabled = false;

					            listView.value.postln;
								t = listView.items.at(listView.value);
								p = vlPath.asAbsolutePath+/+sessionTitle+/+"Questions"+/+t;

								playQuestion.(p,{

									// check if there is another question
									// if so, move to next and start recording kid
									if(listView.value + 1 < listView.items.size,{
										listView.value = listView.value + 1;
										startChildRecorder.(p.basename.splitext[0]);
									},{
										"LAST".postln;

									});
									//••window.view.enabled = true;
									butt.valueAction = 0;

								});
							});
						});

			//------------------------------------------------------
			nextButton = Button()
						.minHeight_(btnHeight)
						.states_([["▼"]])
						.action_({|v|
							listView.value.postln;
							listView.items.size.postln;
							if(listView.value + 1 < listView.items.size,{listView.value = listView.value + 1});
						});


			//------------------------------------------------------
			lxControls = VLayout(
						Knob()
							.maxHeight_(btnHeight-20)
							.value_(lxDepth)
							.action_({|sld|
								lxDepth = sld.value.asFloat;
							}),
						StaticText().maxHeight_(24).string_("lx depth").align_(\center).font_(Font(size:11)),
						Knob()
							.maxHeight_(btnHeight-20)
							.value_(lxSpeed)
							.action_({|sld|
								lxSpeed = sld.value.asFloat;
							}),
						StaticText().maxHeight_(24).string_("lx speed").align_(\center).font_(Font(size:11)),
						Knob()
							.maxHeight_(btnHeight-20)
							.value_(lxBrightness)
							.action_({|sld|
								lxBrightness = sld.value.asFloat;

							}),
						StaticText().maxHeight_(24).string_("lx brightness").align_(\center).font_(Font(size:11))

					);
			

			//------------------------------------------------------
			// recordingControl = UserView()
			// 			.drawFunc_({
			// 				if(childRecordBuffer.class == Buffer,{
			// 					Pen.fillColor_( Color.red(0.7,1.0));
			// 				},{
			// 					Pen.fillColor_( Color.grey( 0.0, 0.01 ));
			// 				});
			// 				Pen.fillRect( Rect( 0, 10, 120, 120));
			// 				Pen.fillColor= Color.new255(226, 49, 140);
			// 				Pen.strokeColor= Color.new255(226, 49, 140);
			// 				Pen.fillOval(Rect.aboutPoint(Point(60, 55+10), 20*scale, 20*scale));
			// 			})
			// 			.animate_(true)
			// 			.clearOnRefresh_(true)
			// 			.mouseDownAction_({ stopChildRecorder.() });


			//------------------------------------------------------
			autoBtn = Button()
						.minHeight_(btnHeight)
						.states_([["Auto Off",Color.black],["Auto On",Color.red]])
						.value_(lxAuto)
						.action_({|btn|
							lxAuto = btn.value.asBoolean;
						});

			//------------------------------------------------------
			speakButton = Button()
						.minHeight_(btnHeight)
						.states_([
							["SPEAK"],
							["SPEAK", bgColor:Color.green]
						])
						.action_({|b|
							switch(b.value,
								0,{

									stopRecordingQuestion.();
									startChildRecorder.(spokenQuestionTitle.basename.splitext[0]);
								},
								1,{
									stopChildRecorder.();

									g = Date.getDate.format("%H_%M_%S");
									spokenQuestionTitle = "SpokenQuestion"++g++".wav";

									startRecordingQuestion.(spokenQuestionTitle);

								}
							);
						});

			//------------------------------------------------------
			pluginView = PopUpMenu()
					.minHeight_(btnHeight)
					.items_(pluginNames)
					.action_({|menu|
						pluginIndex = menu.value;
					});
			//------------------------------------------------------
			exitButton  = Button()
						.minHeight_(btnHeight)
						.states_([["EXIT"]])
						.action_({|b|
							
							listView.items = Array.newClear;
							//stopRecordingQuestion.(); ????

							stopChildRecorder.();
							bgMusicStop.();

							stopRecordingSession.();


							Buffer.freeAll;
							s.freeAll;

							toConsole.("session ending....");

							mainView.index = 0;
							bgMusicStart.();

							listenGroup = Group.new;
							listenSynth = Synth.head(listenGroup,\listenToChild);

						});

			//------------------------------------------------------
			levelIndicator = LevelIndicator()
				.drawsPeak_(true)
				.numTicks_(11)
				.warning_(-6.dbamp)
				.critical_(-3.dbamp)
				;

			//------------------------------------------------------

			gainSlider = Slider()
				.maxHeight_(150)
				.valueAction_(gainSpec.unmap(gainSpec.default))
				.action_({|o|
					gainLabel.string = gainSpec.map(o.value)+"db";
					listenSynth.set(\gain,gainSpec.map(o.value));
					//childRecordSynth.set(\gain,gainSpec.map(o.value));

				});



			//------------------------------------------------------

			monitorSlider = Slider()
				.maxHeight_(150)
				.valueAction_(monitorSpec.unmap(monitorSpec.default))
				.action_({|o|
					monitorLabel.string = monitorSpec.map(o.value)+"db";
					listenSynth.set(\monitor,monitorSpec.map(o.value));
				});


			//------------------------------------------------------




			//------------------------------------------------------
			// layout
			//------------------------------------------------------
			View().layout_( HLayout(

				//------------------------------------------------------
				listView = ListView()
					.background_(Color.grey(0.3))
					.maxHeight_(700)
					.font_(Font("Avenir", 24))
					.enterKeyAction_({|v|
						v.items.at(v.value).postln;
					}),

				//------------------------------------------------------
				View().layout_( GridLayout.rows(
					[
						StaticText().maxHeight_(24).string_("INPUT").align_(\center).font_(Font(size:11)),
						StaticText().maxHeight_(24).string_("GAIN").align_(\center).font_(Font(size:11)),
						StaticText().maxHeight_(24).string_("MONITOR").align_(\center).font_(Font(size:11))
					],
					[levelIndicator, gainSlider, monitorSlider],
					[
						StaticText().maxHeight_(24).string_("").align_(\center).font_(Font(size:11)),
						gainLabel = StaticText().maxHeight_(24).string_(gainSpec.default.asInteger+"db").align_(\center).font_(Font(size:11)),
						monitorLabel = StaticText().maxHeight_(24).string_(monitorSpec.default.asInteger+"db").align_(\center).font_(Font(size:11))
					],
					[prevButton, playButton, nextButton],
					[speakButton, pluginView, exitButton]
					)


				).minWidth_(200).background_(Color.grey(0.3)),
				//------------------------------------------------------

				View().layout_( VLayout(
					lxControls,autoBtn
				)).background_(Color.grey(0.3)),
				//------------------------------------------------------
				View().maxHeight_(700).layout_(VLayout(
					StackLayout(colorView,slider2DView).mode_(\stackAll).index_(1)
				)).minWidth_(400).background_(Color.grey(0.3));
			));

		});
		//------------------------------------------------------
		refreshGUI = {

			// clear listView
			listView.items = Array.newClear;

			if(sessionData["keyPaths"].isEmpty == false,{
				sessionData["keyPaths"].values.collect(_.asInteger).asSortedList.postln;
				// repopulate in order of assigned midi note
				sessionData["keyPaths"].values.asSortedList.collect(_.asInteger).do({ |val|
					t = sessionData["keyPaths"].findKeyForValue(val.asString);
					listView.items = listView.items.add(t);
				});
				//t = listView.items.at(listView.value).key;
			});
		};

		//------------------------------------------------------
		onPlayLevel = OSCFunc({|msg, time, addr, recvPort|{
				//scale = msg[4].ampdb.linexp(-40, 0, 1, 2.0);
				scale = msg[3].ampdb.linlin(-40, 0, 0, 1);

				levelIndicator.value = scale;
  			}.defer;

		}, '/peakListener');



		//------------------------------------------------------
		window = Window("",Rect(0, 0, Window.screenBounds.width, 380+60).center_(Window.availableBounds.center))
			.layout_( VLayout(
				mainView = StackLayout(
					loadView.(),
					sessionView.(),
					errorView.()
				);
			))
			.front;

		window.toFrontAction = {
			say.("welcome to, voicelab");
		};

		window.drawFunc = {
			var c = Color.hsv(1.0.rand,1.0,1-0.4.rand,1.0); 
			Pen.addRect(window.view.bounds.insetBy(2));
			Pen.fillAxialGradient(window.view.bounds.leftTop, window.view.bounds.rightBottom,
				c, c.complementary);
			Pen.scale(4.5,4.5);
			Pen.drawImage( Point(0,-30), image, operation: 'sourceOver', opacity:0.1);

		};

		window.view.mouseDownAction = {
			window.refresh();
		};
		window.onClose = ({

			~routine.stop();
			bgMusicStop.();

			onPlayLevel.free;
			disconnectArduino.();

			Buffer.freeAll;
			s.freeAll;

			toConsole.("app closing....");
		});

		CmdPeriod.doOnce({window.close});
	});


	//--------------------------------------------------------------------------------
	//
	//--------------------------------------------------------------------------------

	~routine = Routine{ |in|
		inf.do{|i|

			var delta = pi/180.0;
			var x = diameter.half + (sin(angle.mod(2pi)) * diameter.half * fix * lxDepth);
			var y = diameter.half + (cos(angle.mod(2pi)) * diameter.half * fix * lxDepth);
			var colors = [];

			case
			{ lxAuto == true}{
					angle = angle + (delta  * lxSpeed);
					point.set(x,y);
			};

			3.do({|i|
				colors = colors.add(calculateColorForPoint.(
					pointDelta.x + i,
					pointDelta.y + i,
					1.0
				));
			});

			arduino!?{
				r = 255.0 * lxBrightness;
				3.do({|i|
				arduino.dmxc_(i+1,colors[i].red*r,colors[i].green*r,colors[i].blue*r);
				});
			};

			pointDelta.tween(point);

			slider2DView.refresh();
			fps.wait;
		}
	};


	//--------------------------------------------------------------------------------
	// BOOT UP
	//--------------------------------------------------------------------------------

	connectArduino.();

	//we want the LX to run all the time...
	~routine.play(AppClock);

	point.set(radius,radius);

	bgMusicStart.();

	{
		listenGroup = Group.new;
		listenSynth = Synth.head(listenGroup,\listenToChild);

		toConsole.("Start listening");
	}.defer(1);

	initGUI.();

)

