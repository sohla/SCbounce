
		(
		    var width = Window.screenBounds.width, height = Window.screenBounds.height ;
			var startup, shutdown, buildUI;

			var contentView = View().background_(Color.grey(0.2));

			var names = ["adam","george","harry","susan","eve"];
			var faces = ["🦆","🦊","🐴","🐯","🍎"];
			var possibleNames = names.copy;

			var reloadButton;
			var loopButton, stackButton;

			var createPlotterGroup, createGyroCanvas, createAccelCanvas, createTransportView;
			var createWindowView, addDeviceView;
			var startOSCListening, stopOSCListening, enableOSCListening, disableOSCListening;

			var addDevice, removeDevice;

		    var gryoListner, rotMatListner, rrateListener, accelListener, altListener, ampListener;
		    var buttonListener;

			var devices = Dictionary();

		    // data
			var deviceProto = (
				\name: "eve",
				\ip: "127.0.0.1",
				\port: "57120",
				\gyroEvent: (\pitch:0,\roll:0,\yaw:0),
				\rrateEvent: (\x:0,\y:0,\z:0),
				\rrateMass: 0,
				\accelEvent: (\x:0,\y:0,\z:0),
				\accelMass: 1,
				\ampValue: 0,
				\env: nil,	// Environment for injected code
				\rout: nil,	// Routine calls ~next every ~fps
				\dc: nil, //DataCollector
				\lc: nil, //DataCollector for playing
				\lcout: nil //DataCollector Routine re-player
				);

			var loadPersonality;
			var reloadDevice;
			var createRout;
			var createLCout;
			

			var dataSize = 100;
			var dataPath = "~/Develop/SuperCollider/Projects/SCbounce/data/";

			var pos3D = [0,0,0];

			var midiOut;
		//------------------------------------------------------------	
		// midi
		//------------------------------------------------------------	

		MIDIClient.init;
		MIDIClient.destinations;
		midiOut = MIDIOut(0).latency_(Server.default.latency);

		//------------------------------------------------------------	
		// 
		//------------------------------------------------------------	

		loadPersonality = {|name|

			var path = PathName.new("~/Develop/SuperCollider/Projects/SCbounce/personalities/"++name++".sc");
			var file = File.new(path.asAbsolutePath,"r");
			var str = file.readAllString;
			var env = Environment.make {interpret(str)};	

			env
		};

		//------------------------------------------------------------	
		// 
		//------------------------------------------------------------	
		createRout = {|d|

			Routine { 
				var i=0;
				loop{ 
					d.env.use{
						~next.(i,d);
						(~secs.()).yield;
						i=i+1;
					};
				}
			};
		};
		//------------------------------------------------------------	
		// 
		//------------------------------------------------------------	
		createLCout = {|d|

			Routine { 
				var i=0;
				loop{ 
						d.env.use{

							var data = [];
							i = (i+1).mod(d.lc.collection.size);
							data = d.lc.collection.reverse[i][\data];

							d.gyroEvent.pitch = data[0];
							d.gyroEvent.roll = data[1];
							d.gyroEvent.yaw = data[2];
							d.accelEvent.y = data[3];
							d.accelEvent.z = data[4];
							d.accelEvent.x = data[5];
							d.rrateEvent.x = data[6];
							d.rrateEvent.y = data[7];
							d.rrateEvent.z = data[8];
							
							(~secs.()).yield;
					};
				}
			};
		};
		//------------------------------------------------------------	
		// 
		//------------------------------------------------------------	
		startup = {

			DataCollector.makeBackupFolder();
			DataCollector.folder = PathName.new(dataPath).fullPath;

		};

		//------------------------------------------------------------	
		// 
		//------------------------------------------------------------	
		shutdown = {

			devices.keysValuesDo({|k,v|
				v.rout.stop();
				v.lcout.stop();
				v.env.use{ ~deinit.() };
			});

			stopOSCListening.();
		};
		//------------------------------------------------------------	
		// 
		//------------------------------------------------------------	

		addDevice = { |ip|

			var d = Event.new(proto:deviceProto);
			
			d.ip = ip;

			// hack : choose from a possible list : list is not kept or updated
			// use only for adding of device (ie. reloading not supported)
			//d.name = possibleNames.remove(possibleNames.choose);

			// data collector for replay and analysis
			d.dc = DataCollector.new(d.name++"_data");

			devices.put(ip,d);
			
			// load the data
			reloadDevice.(d);

			// data collector player created at start
			d.lcout = createLCout.(d);

			d // return the device (data)
		};

		reloadDevice = { |d|

			// stop personality

			d.rout.stop;
			if(d.env != nil,{ d.env.use{ ~deinit.() }});


			// start new personality
			d.env = loadPersonality.(d.name);
			d.env.use{ ~init.(midiOut) };
			d.rout = createRout.(d);
			d.rout.reset.play(AppClock);

		};

		//------------------------------------------------------------	
		// 
		//------------------------------------------------------------	
		buildUI = {
			
			var window;

			QtGUI.palette = QPalette.dark; 

			GUI.skin.plot.gridLinePattern = FloatArray[1, 0];
			GUI.skin.plot.gridColorX = Color.yellow(0.5);
			GUI.skin.plot.gridColorY = Color.yellow(0.5);
			GUI.skin.plot.background = Color.black;
			GUI.skin.plot.plotColor = Color.white;

			window = Window("gyro osc tester", Rect(0, height, width, height-150), false).front;
			window.view.keyDownAction_({|view,char,mods,uni,code,key|
				if(uni==114,{
					devices.keysValuesDo({|k,v|
						reloadDevice.(v);
					});
				});
				if(uni==108,{
					loopButton.valueAction_(loopButton.value.asBoolean.not);
				});
				if(uni==97,{
					stackButton.valueAction_((stackButton.value+1).mod(3));
				});
				if(uni==100,{
					//•disconnect
				});
				//uni.postln;
			});

			window.onClose = {
				shutdown.();
			};
			CmdPeriod.doOnce({window.close});
			
			createWindowView.(window);

		};

		createWindowView = {|view|

			var scroll = ScrollView(view,Rect(0,30,width,height - 150 )).background_(Color.yellow);
			
			StaticText(view)
					.stringColor_(Color.yellow)
					.font_(Font(size:14))
					.minHeight_(30)
					.minWidth_(200)
					.string_(" :: bounce");

			contentView.layout_(VLayout());
			scroll.canvas = contentView;

			// set this for quick connection to device
			//addDeviceView.(contentView, addDevice.("127.0.0.1"));
			//addDeviceView.(contentView, addDevice.("192.168.0.101"));

		};

		addDeviceView = { |view, d|

			var header;
			var va,vb,vc;
			var stackView, stackLayout;

			header = View(view).layout_( HLayout(
				
				stackButton = Button(view)
					.minWidth_(160)
					.states_([
						["⚾︎ attitude"],	
						["⊙ accelerometer"],
						["⊚ rotation rate"]
					])
					.action_({|b|
						stackLayout.index = b.value;
					}),

				StaticText(view)
					.stringColor_(Color.white)
					.font_(Font(size:14))
					.minWidth_(500)
					.string_("📡  ip ["+d.ip+"] port ["+d.port+"]"),

				reloadButton = Button(view)
					.maxWidth_(80)
					.states_([
						["💾 reload"],	
					])
					.action_({|b|
						reloadDevice.(d);
					}),

				PopUpMenu(view)
						.minWidth_(160)
						.items_(names.collect{|o,i|faces[i]+o})
						.valueAction_(names.find([d.name]))
						.action_({|b|
							d.name = names.at(b.value);
							reloadDevice.(d);
						}),

				Button()
					.maxWidth_(30)
					.states_([
						["->",Color.white]
					])
					.action_({|b|

						File.openDialog ("yo", {

							//• populate d.dc with new data...then set loop
							// d.dc.backup; 
							// d.lc = DataCollector.new(d.name++"_data");
							// d.lc.recover;
							// disableOSCListening.();
							// d.lcout.reset().play(AppClock);
						},{

							// d.lcout.stop;
							// enableOSCListening.();
						});

					}),
				loopButton = Button()
					.maxWidth_(80)
					.states_([
						["↻ loop",Color.white],
						["↻ loop",Color.green]
					])
					.action_({|b|

						if(b.value == 1,{

							d.dc.backup; 
							d.lc = DataCollector.new(d.name++"_data");
							d.lc.recover;
							disableOSCListening.();
							d.lcout.reset().play(AppClock);
						},{

							d.lcout.stop;
							enableOSCListening.();
						});

					}),

				Button(view)
					.maxWidth_(120)
					.states_([
						["✖️ disconect",Color.red(0.5)],	

					])
					.action_({|b|
						header.remove();
						stackView.remove();
						
						// stop personality
						d.env.use{ ~deinit.() };
						d.rout.stop;

						devices.removeAt(d.ip);

					})

			).setAlignment(0,\left).setAlignment(3,\left));

			view.layout.add(stackView = View().background_(Color.yellow).minHeight_(250).layout_( stackLayout = StackLayout(
					va = View().background_(Color.cyan(0.2)),
					vb = View().background_(Color.blue(0.2)),
					vc = View().background_(Color.magenta(0.2))
					)
				);
			);	

			createPlotterGroup.(va, Rect(250,0,400,250), [
				["pitch",Color.red,{|p|
					p.value = (d.dc.collection.collect({|a,i| a[\data][0]})); p.minval_(pi); p.maxval_(-pi);
				}],
				["roll",Color.green,{|p|
					p.value = (d.dc.collection.collect({|a,i| a[\data][1]})); p.minval_(pi); p.maxval_(-pi);
				}],
				["yaw",Color.blue,{|p|
					p.value = (d.dc.collection.collect({|a,i| a[\data][2]})); p.minval_(pi); p.maxval_(-pi);
				}],
			]);

			createPlotterGroup.(vb, Rect(250,0,400,250), [
				["aaccelX",Color.cyan,{|p|
					p.value = (d.dc.collection.collect({|a,i| a[\data][3]})); p.minval_(pi); p.maxval_(-pi);
				}],
				["accelY",Color.magenta,{|p|
					p.value = (d.dc.collection.collect({|a,i| a[\data][4]})); p.minval_(pi); p.maxval_(-pi);
				}],
				["accelZ",Color.yellow,{|p|
					p.value = (d.dc.collection.collect({|a,i| a[\data][5]})); p.minval_(pi); p.maxval_(-pi);
				}],
			]);

			createPlotterGroup.(vc, Rect(250,0,400,250), [
				["rrateX",Color.cyan,{|p|
					p.value = (d.dc.collection.collect({|a,i| a[\data][6]})); p.minval_(pi); p.maxval_(-pi);
				}],
				["rrateY",Color.magenta,{|p|
					p.value = (d.dc.collection.collect({|a,i| a[\data][7]})); p.minval_(pi); p.maxval_(-pi);
				}],
				["rrateZ",Color.yellow,{|p|
					p.value = (d.dc.collection.collect({|a,i| a[\data][8]})); p.minval_(pi); p.maxval_(-pi);
				}],
			]);

			createPlotterGroup.(va, Rect(650,0,620,250), [
				["a",Color.cyan,{|p|

					p.value = p.value.reverse;
					p.value = p.value.shift(-1).putLast(d.env.use{ ~plot.(d)});
					p.value = p.value.reverse;
					p.minval_(d.env.use{ ~plotMin});
					p.maxval_(d.env.use{ ~plotMax});
				}]
			]);


			createGyroCanvas.(va,d);
			createAccelCanvas.(vb,d);

			contentView.layout.add(nil);
		};

		//------------------------------------------------------------	
		// Plotters
		//------------------------------------------------------------	

		createPlotterGroup = {|view, bounds, plotters|

			var plotterView = UserView(view,bounds).background_(Color.black).animate_(true);
			var size = plotters.size;
			var pw = bounds.width;
			var ph = bounds.height / size;

			plotterView.drawFunc_({});

			plotters.do{ |specs,i|
				var max = 0;
				var plotter = Plotter(specs[0], Rect(pw*0.3,ph*i,pw*0.7,ph),plotterView)
					.value_((0..dataSize))
					.refresh;


				var st = StaticText(plotterView,Rect(pw*0.15,ph*i,pw*0.15,ph/2))
					.string_(specs[0])
					.align_(\center)
					.stringColor_(specs[1])
					.background_(Color.black);

				var su = StaticText(plotterView,Rect(pw*0.15,(ph*i)+(ph/2),pw*0.15,ph/2))
					.string_(max)
					.align_(\center)
					.stringColor_(specs[1])
					.background_(Color.black)
					.mouseUpAction_({max = 0});

				StaticText(plotterView,Rect(0,ph*i,pw*0.15,ph/2))
					.string_(specs[0])
					.align_(\center)
					.stringColor_(specs[1])
					.background_(Color.black);


				plotterView.drawFunc = plotterView.drawFunc <> {

					{
						var val = specs[2].(plotter).value[0].round(1e-4);
						st.string_(val);

						if(val > max, {max = val});

						su.string_("max :"+max);
						//plotter.minval_(-pi);
						//plotter.maxval_(pi);

						plotter.setProperties(\plotColor, specs[1]).refresh;

					}.defer(0.1);// need to delay to allow for construction 
				}
			};


		};

		//------------------------------------------------------------	
		// Gyro Canvas
		//------------------------------------------------------------	

		createGyroCanvas = { |view, data|
			var graph1;
			var cube, top, rate, loc, ico;
			var p1,p2,p3;
			var t = (1.0 + (5.0).sqrt) / 2.0;

			graph1 = Canvas3D(view, Rect(0, 0, 250, 250))
			    .scale_(200)
				.background_(Color.black)
			    .perspective_(0.5)
			    .distance_(3.5);


			// graph1.view.drawFunc= graph1.view.drawFunc<>{
			// 	("[data"+"}-->".scramble).draw
			// };
			graph1.add(cube = Canvas3DItem.cube
			    .color_(Color.red(0.8))
			    .width_(1)
			);

			graph1.add(top = Canvas3DItem.grid(4)
				.color_(Color.green(0.9))
			    .width_(1)
			);

			graph1.add(rate = Canvas3DItem.grid(2)
				.color_(Color.black(0.9))
			    .width_(1)
			);

			graph1.add(loc = Canvas3DItem.regPrism(3)
				.color_(Color.blue(0.9))
			    .width_(1)
			    //
			);


		     graph1.add(p1 = Canvas3DItem.grid(2)
		        .color_(Color.green)
				.fill_(false)
		        .width_(1)
				.transform(Canvas3D.mScale(1,t,1))
				//.transform(Canvas3D.mRotateY(pi/2))
		    );
		    graph1.add(p2 = Canvas3DItem.grid(2)
		        .color_(Color.red)
		        .width_(1)
				.fill_(false)
				.transform(Canvas3D.mScale(t,1,1))
				.transform(Canvas3D.mRotateY(pi/2))
		    );
		    graph1.add(p3 = Canvas3DItem.grid(2)
		        .color_(Color.blue)
		        .width_(1)
				.fill_(false)
				.transform(Canvas3D.mScale(t,1,t))
				.transform(Canvas3D.mRotateX(pi/2))
		    );

			 graph1.add(ico = Canvas3DItem()
		        .color_(Color.white.alpha_(0.1))
	    	    .width_(1)
				.fill_(true)
				.paths_([
					[p1.paths[0][0],p1.paths[0][1],p2.paths[0][1]],
		 			[p2.paths[0][1],p3.paths[0][0],p1.paths[0][0]],
				    [p1.paths[0][1],p3.paths[0][1],p2.paths[0][1]],
				   	[p1.paths[0][0],p2.paths[0][0],p1.paths[0][1]],
			    	[p1.paths[0][1],p3.paths[1][1],p2.paths[0][0]],
		    		[p1.paths[0][0],p3.paths[1][0],p2.paths[0][0]],

				   	[p1.paths[1][0],p2.paths[1][0],p1.paths[1][1]],
		    		[p1.paths[1][1],p3.paths[1][1],p2.paths[1][0]],
				    [p1.paths[1][0],p3.paths[1][0],p2.paths[1][0]],
				    [p1.paths[1][0],p2.paths[1][1],p1.paths[1][1]],
			    	[p1.paths[1][1],p3.paths[0][1],p2.paths[1][1]],
			    	[p1.paths[1][0],p3.paths[0][0],p2.paths[1][1]],

				    [p2.paths[0][1],p3.paths[0][0],p2.paths[1][1]],
				    [p2.paths[0][1],p3.paths[0][1],p2.paths[1][1]],

				    [p2.paths[0][0],p3.paths[1][1],p2.paths[1][0]],
				    [p2.paths[0][0],p3.paths[1][0],p2.paths[1][0]],

				    [p3.paths[0][0],p1.paths[1][0],p3.paths[1][0]],
				    [p3.paths[0][0],p1.paths[0][0],p3.paths[1][0]],

				    [p3.paths[0][1],p1.paths[1][1],p3.paths[1][1]],
				    [p3.paths[0][1],p1.paths[0][1],p3.paths[1][1]],
				])
		    );

			loc.transform(Canvas3D.mScale(0.02,0.02,0.2));

			// animate
			graph1.animate(30) {|t|
				

				var sx,sy,sz;

				// store data

				data.dc.addData([
					data.gyroEvent.pitch, 
					data.gyroEvent.roll, 
					data.gyroEvent.yaw,
					data.accelEvent.y.neg,
					data.accelEvent.z,
					data.accelEvent.x,
					data.rrateEvent.x,
					data.rrateEvent.y,
					data.rrateEvent.z
					],t);


			 	data.dc.collection = data.dc.getLast(dataSize); // limit size of collected samples

		    	pos3D = [0,0,0];
		        pos3D = Canvas3D.vectorMatrixMul(pos3D, Canvas3D.mScale(1,0.5,0.05));
		        pos3D = Canvas3D.vectorMatrixMul(pos3D, Canvas3D.mTranslate(0,0,1.5));
		        pos3D = Canvas3D.vectorMatrixMul(pos3D, Canvas3D.mRotateX(data.gyroEvent.roll + pi.half));
		        pos3D = Canvas3D.vectorMatrixMul(pos3D, Canvas3D.mRotateY(data.gyroEvent.yaw.neg));
		       	pos3D = Canvas3D.vectorMatrixMul(pos3D, Canvas3D.mRotateZ(data.gyroEvent.pitch.neg));


		        loc.transforms = [
			        Canvas3D.mRotateX(data.gyroEvent.roll + pi.half),
			        Canvas3D.mRotateY(data.gyroEvent.yaw.neg),
			        Canvas3D.mRotateZ(data.gyroEvent.pitch.neg),
		        	Canvas3D.mTranslate(pos3D[0],pos3D[1],pos3D[2]),
		        ];


				sx = ((pos3D[0] + 1.5) * ( 2.0 / 3.0)).floor;
				sy = ((pos3D[1] + 1.5) * ( 2.0 / 3.0)).floor;
				sz = ((pos3D[2] + 1.5) * ( 2.0 / 3.0)).floor;

				//( (sz*4) + (sy*2) + sx).postln;
				//graph1.background_(Color.hsv(( ( (sz*4) + (sy*2) + sx) / 8.0 ),0.8,0.8,1));

				cube.transforms = [ 
					Canvas3D.mScale(1,0.5,0.05),
					Canvas3D.mTranslate(0,0,1.5),
			        Canvas3D.mRotateX(data.gyroEvent.roll + pi.half),
			        Canvas3D.mRotateY(data.gyroEvent.yaw.neg),
			        Canvas3D.mRotateZ(data.gyroEvent.pitch.neg),
			    ];

				top.transforms = [ 
					Canvas3D.mScale(1,1,0.05),
					Canvas3D.mScale(1,0.5,0.05),
					Canvas3D.mTranslate(0,0,1.5),
			        Canvas3D.mRotateX(data.gyroEvent.roll + pi.half),
			        Canvas3D.mRotateY(data.gyroEvent.yaw.neg),
			        Canvas3D.mRotateZ(data.gyroEvent.pitch.neg),
					//Canvas3D.mTranslate(0,altValue.neg * 1  ,0)
			    ];

			    ico.transforms = [ 
					Canvas3D.mScale(0.6,0.6,0.6),
			        Canvas3D.mRotateX(data.gyroEvent.roll + pi.half),
			        Canvas3D.mRotateY(data.gyroEvent.yaw.neg),
			        Canvas3D.mRotateZ(data.gyroEvent.pitch.neg),
					//Canvas3D.mTranslate(0,altValue.neg * 1  ,0)
			    ];
			    
			    p1.transforms=ico.transforms;
				p2.transforms=ico.transforms;
				p3.transforms=ico.transforms;

				rate.transforms = [ 
					Canvas3D.mScale(0.01,data.rrateMass.half ,1.0),
					Canvas3D.mTranslate(-2.1,0,0)

			    ];

			};

		};


		//------------------------------------------------------------	
		// Accel Canvas
		//------------------------------------------------------------	
		createAccelCanvas = { |view, data|
			
		    var graph2, spot, accelRate, accelX, accelY, accelZ;

			graph2 = Canvas3D(view, Rect(0, 0, 250, 250))
			    .scale_(200)
				.background_(Color.black)
			    .perspective_(0.5)
			    .distance_(2);

			graph2.add(spot = Canvas3DItem.cube
			    .color_(Color.red(0.8))
			    .width_(1)

			);

			graph2.add(accelRate = Canvas3DItem.grid(2)
				.color_(Color.black(0.9))
			    .width_(1)
			);

			graph2.add(accelX = Canvas3DItem.regPrism()
				.color_(Color.red(0.9))
			    .width_(1)
			);
			graph2.add(accelY = Canvas3DItem.regPrism()
				.color_(Color.green(0.9))
			    .width_(1)
			);
			graph2.add(accelZ = Canvas3DItem.regPrism()
				.color_(Color.blue(0.9))
			    .width_(1)
			);
			graph2.animate(60) {|t|
				

				spot.transforms = [ 
					Canvas3D.mScale(0.8,0.8,0.8),
					Canvas3D.mScale(1.0,0.5,0.05),
				    Canvas3D.mRotateX(data.gyroEvent.roll + pi.half),
			        Canvas3D.mRotateY(data.gyroEvent.yaw.neg),
			        Canvas3D.mRotateZ(data.gyroEvent.pitch.neg),
					 Canvas3D.mTranslate(data.accelEvent.y.neg.half,data.accelEvent.z.half,data.accelEvent.x.half)
			    ];

				accelRate.transforms = [ 
					Canvas3D.mScale(0.01,data.accelMass ,1.0),
					Canvas3D.mTranslate(-2.2,0,0)
			    ];

				accelX.transforms = [
					Canvas3D.mScale(0.01,(data.accelEvent.x),0.01),
			        Canvas3D.mRotateX(data.gyroEvent.roll + pi.half),
			        Canvas3D.mRotateY(data.gyroEvent.yaw.neg),
			        Canvas3D.mRotateZ(data.gyroEvent.pitch.neg),
			    ];

				accelY.transforms = [ 
					Canvas3D.mScale((data.accelEvent.y),0.01,0.01),
			        Canvas3D.mRotateX(data.gyroEvent.roll + pi.half),
			        Canvas3D.mRotateY(data.gyroEvent.yaw.neg),
			        Canvas3D.mRotateZ(data.gyroEvent.pitch.neg),
			    ];
				accelZ.transforms = [ 
					Canvas3D.mScale(0.01,0.01,(data.accelEvent.z)),
			        Canvas3D.mRotateX(data.gyroEvent.roll + pi.half),
			        Canvas3D.mRotateY(data.gyroEvent.yaw.neg),
			        Canvas3D.mRotateZ(data.gyroEvent.pitch.neg),
			    ];


			};

		};

		//------------------------------------------------------------	
		// osc listneners
		//------------------------------------------------------------	

		startOSCListening = {

			buttonListener = OSCFunc({ |msg, time, addr, recvPort|
				//[msg, time, addr, recvPort].postln;
				if(msg[1].asFloat == 1.0, {
					if(devices.at(addr.ip) == nil,{
						{
							var d = addDevice.(addr.ip);
							addDeviceView.(contentView, d)
						}.defer;
					},{
						devices.removeAt(addr.ip);
					});
				});

			}, '/gyrosc/button');

			gryoListner = OSCFunc({ |msg, time, addr, recvPort|

				if(devices.at(addr.ip) != nil,{
					devices.at(addr.ip).gyroEvent = (
						\pitch:msg[1].asFloat,
						\roll:msg[2].asFloat,
						\yaw:msg[3].asFloat);
				});
			
			}, '/gyrosc/gyro');


			// rotMatListner = OSCFunc({ |msg, time, addr, recvPort|
			// 	// gyroEvent.pitch = msg[1].asFloat;
			// 	// gyroEvent.roll = msg[2].asFloat;
			// 	// gyroEvent.yaw = msg[3].asFloat;
			// 	//rotMatEvent = msg[1..].clumps([4]);
			// 	//rotMatEvent.postln;
			// }, '/gyrosc/rotmat');


			rrateListener = OSCFunc({ |msg, time, addr, recvPort|

				if(devices.at(addr.ip) != nil,{
					devices.at(addr.ip).rrateEvent = (
						\x:msg[1].asFloat,
						\y:msg[2].asFloat,
						\z:msg[3].asFloat);
				});

			}, '/gyrosc/rrate');

			accelListener = OSCFunc({ |msg, time, addr, recvPort|

				if(devices.at(addr.ip) != nil,{
					devices.at(addr.ip).accelEvent = (
						\x:msg[1].asFloat,
						\y:msg[2].asFloat,
						\z:msg[3].asFloat);
				});
			}, '/gyrosc/accel');

			// altListener = OSCFunc({ |msg, time, addr, recvPort|
			// 	altValue = (msg[1].asFloat);
			// }, '/gyrosc/alt');
				
			ampListener = OSCFunc({ |msg, time, addr, recvPort|
					if(devices.at(addr.ip) != nil,{
						devices.at(addr.ip).ampValue = msg[1].asFloat;
					});
			}, '/gyrosc/amp');

		};

		stopOSCListening = {
			buttonListener.free;
			ampListener.free;
			gryoListner.free;
			// altListener.free;
			rrateListener.free;
			//rotMatListner.free;
			accelListener.free;
		};

		enableOSCListening = {
			buttonListener.enable;
			ampListener.enable;
			gryoListner.enable;
			// altListener.enable;
			rrateListener.enable;
			//rotMatListner.enable;
			//accelListener.enable;
		};

		disableOSCListening = {
			buttonListener.disable;
			ampListener.disable;
			gryoListner.disable;
			//altListener.disable;
			rrateListener.disable;
			//rotMatListner.disable;
			accelListener.disable;
		};


		//------------------------------------------------------------	
		// 
		//------------------------------------------------------------	


		startup.();
		buildUI.();
		startOSCListening.();

	)

