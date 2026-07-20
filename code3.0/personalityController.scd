// Derive lists/personalities from this checkout so any machine (RPI, laptop,
// mac mini, M1) works without editing paths. Same pattern conductorController
// already uses for its data paths.
var airkitRoot = thisProcess.nowExecutingPath.dirname.dirname;
var listsDir = airkitRoot +/+ "lists" ++ "/";
var personalityDir = airkitRoot +/+ "personalities" ++ "/";
// var listsDir = "~/Develop/SuperCollider/Projects/AirKit/lists/";//RPI (pre-2026-07-14 hardcode)
// var personalityDir = "~/Develop/SuperCollider/Projects/AirKit/personalities/";//RPI

var list = "list_cotf.sc";
// var list = "list_performanceTests.sc";
// var list = "list_ITR_Mel.sc";
// var list = "list_SiS_Alessio.sc";
// var list = "list_yourDNA26.sc";

// var list = "list_glenroy.sc";
var defaultLists = list!5;


// var defaultLists = [
//     "list_MIM25_James.sc",
//     "list_MIM25_Caz.sc",
//     "list_MIM25_Tim.sc",
//     "list_MIM25_Matt.sc",
//     "list_MIM25_Heather.sc",
// ];



// var defaultLists = [
//     "list_ITR_Mel.sc",
//     "list_ITR_Nic.sc",
//     "list_ITR_Brenton.sc",
//     "list_ITR_Alessio.sc",
//     "list_ITR_Mel.sc",
// ];

var com = (
    \root: 0,
    \dur: 1,
    \accelMass: 0,
    \rrateMass: 0,
);

//------------------------------------------------------------
// 
//------------------------------------------------------------
var loadPersonalityList = {|i|
    var path = PathName.new(listsDir++defaultLists[i]);
    var file = File.new(path.asAbsolutePath,"r");
    var str = file.readAllString;
    // var time = File.mtime(path.asAbsolutePath);
    // time.postln;
    interpret(str) // returns array of personality files
};

var interpretPersonality = {|d|

    var path = PathName.new(personalityDir++d.name++".sc");
    var file = File.new(path.asAbsolutePath,"r");
    var str = file.readAllString;

    // after adding personality to an Environment, add useful functions to be used by anyone
    var env = Environment.make {
        ~filePath = path.asAbsolutePath;
        ~fileTime = File.mtime(path.asAbsolutePath);
        // ~group = Group.new;
        ~model = (
            \com: com,
            \name: d.name,
            \ptn: Array.fill(16,{|i|i=90.rrand(65).asAscii.toLower}).join(),
            \rrateMass: 0,
            \rrateMassFiltered: 0,
            \rrateMassFilteredAttack: 0.9,
            \rrateMassFilteredDecay: 0.7,

            // \rrateMassThreshold: 0.21, //use for isMoving
            // \rrateMassThresholdSpec: ControlSpec(0.07, 0.4, \lin, 0.01, 0.21),

            \accelMass: 0,
            \accelMassFiltered: 0,
            \accelMassFilteredAttack: 0.9,
            \accelMassFilteredDecay: 0.7,

            // \accelMassAmpThreshold: 2.0,
            // \accelMassThresholdSpec: ControlSpec(0.4, 3.0, \lin, 0.1, 2.0),

            \gyroXFiltered: 0,
            \gyroYFiltered: 0,
            \gyroZFiltered: 0,
            \gyroFilteredAttack: 0.9,
            \gyroFilteredDecay: 0.7,


            // \isHit: false,
            // \isMoving: true,
            // \accelMassAmp: 0.0,

        );
        ~device = d;
        //------------------------------------------------------------
        // frame rate of rout
        ~secs = 0.03;

        //------------------------------------------------------------
        // Output routing convention (COTF-compatible, additive).
        // Personalities route audio via ~outBus (\out, ~outBus in Pbinds).
        // Standalone: 0 (hardware out, today's behaviour). Under the COTF
        // profile, topEnvironment holds ~cotfSeatBus (seat -> private Bus)
        // and each device's audio lands on its seat bus -> monitor -> final.
        ~outBus = topEnvironment.at(\cotfSeatBus) !? { |map|
            map.at(d.index) !? { |bus| bus.index } ? 0
        } ? 0;

        //------------------------------------------------------------
        // process data->model
        ~processDeviceData = {|d|
            ~model.accelMass = d.sensors.accelEvent.sumabs * 0.33; // scale it
            ~model.rrateMass = d.sensors.rrateEvent.sumabs;
            ~model.accelMassFiltered = ~smooth.(
                ~model.accelMass,
                ~model.accelMassFiltered,
                ~model.accelMassFilteredAttack,
                ~model.accelMassFilteredDecay
            );
            ~model.rrateMassFiltered = ~smooth.(
                ~model.rrateMass,
                ~model.rrateMassFiltered,
                ~model.rrateMassFilteredAttack,
                ~model.rrateMassFilteredDecay
            );
            // normalise filtered gyro
            ~model.gyroXFiltered = ~smooth.(
                d.sensors.gyroEvent.x / pi,
                ~model.gyroXFiltered,
                ~model.gyroFilteredAttack,
                ~model.gyroFilteredDecay
            );
            ~model.gyroYFiltered = ~smooth.(
                d.sensors.gyroEvent.y / pi.half,
                ~model.gyroYFiltered,
                ~model.gyroFilteredAttack,
                ~model.gyroFilteredDecay
            );
            ~model.gyroZFiltered = ~smooth.(
                // (d.sensors.gyroEvent.z / pi).fold(-0.5,0.5), // nice to have
                d.sensors.gyroEvent.z / pi,
                ~model.gyroZFiltered,
                ~model.gyroFilteredAttack,
                ~model.gyroFilteredDecay
            );
        };

        //------------------------------------------------------------
        ~play = {
            postf("play : % \n",~model.name);
            Pdef(~model.ptn).play(0.125);

        };

        ~stop = {
            postf("stop : % \n",~model.name);
            Pdef(~model.ptn).stop();
        };

        //------------------------------------------------------------
        ~init = {
            postf("init : % [%] \n",~model.name, ~model.ptn);
        };

        //------------------------------------------------------------
        //------------------------------------------------------------
        ~deinit = {
            postf("deinit : % [%] \n",~model.name, ~model.ptn);
        };

        //------------------------------------------------------------
        // Beat-aligned hooks dispatched from conductorController's score
        // Routine (see ~scheduleFromIndex). Personalities override any of
        // these by simple top-level assignment (~onBeat = { |ctx| ... }).
        // ctx is an Event with: idx, beatInBar, isDown, isTrueBeat, barIdx,
        // half, sectionId, phraseId, chord ("root roman"), key, mode, scale;
        // matching prev* fields for each change-triggered value;
        // voicePool; loudness, tension, brightness, density, register
        // (raw score features as floats); p (score bar entry); m (meta bar entry).
        // Change-triggered hooks fire only when the tracked field differs
        // from its previous value.
        ~onTick    = { |ctx| };  // every beats.txt event (per subdivision)
        ~onHalf    = { |ctx| };  // when the half-bar index changes
        ~onBeat    = { |ctx| };  // every true musical beat
        ~onBar     = { |ctx| };  // every tracked downbeat (\D)
        ~onPhrase  = { |ctx| };  // when p["phrase_id"] changes
        ~onSection = { |ctx| };  // when p["section_id"] changes
        ~onChord   = { |ctx| };  // when chord_root + chord_roman changes
        ~onKey     = { |ctx| };  // when key + mode changes
        ~onScale   = { |ctx| };  // when p["active_scale"] changes
        ~onRoomState = { |ctx| };  // when /airkit/state changes; ctx = (state:, prevState:, stateChanged:)

        //------------------------------------------------------------
        // State-gated ~next variants. Called from procRout in addition to
        // ~next, only when the current room state matches. Same signature
        // as ~next: receives device `d`, runs at IMU rate (~30 Hz).
        // Second arg is ~stateCtx — a trimmed snapshot of the score
        // context (state, voicePool, loudness/tension/brightness/
        // density/register, sectionId, phraseId, chord, key, scale, p, m).
        // No change flags — those belong to beat hooks. Lets state ticks
        // mix gesture with score features at IMU rate.
        ~idleNext    = { |d, ctx| };  // only when ~roomState == \idle
        ~tuningNext  = { |d, ctx| };  // only when ~roomState == \tuning
        ~pieceNext   = { |d, ctx| };  // only when ~roomState == \piece
        ~curtainNext = { |d, ctx| };  // only when ~roomState == \curtain
        // note: \silent has no per-tick hook — it's a one-shot hard mute.
        // Personalities handle it in ~onRoomState by setting amp/gain to 0
        // on the \silent case (and restoring on state exit as needed).

        //------------------------------------------------------------
        interpret(str);
        //------------------------------------------------------------

        ~smooth= {|input,history, attack=0.5, decay=0.05|
            var coeff = attack;
            if(history > input, {coeff = decay});
            (coeff * input + ((1 - coeff) * history))
            // history + coeff * (input - history)
        };

        ~slope = {|input,history|
            history - input
        };
    };

    env
};

//------------------------------------------------------------
// 
//------------------------------------------------------------

var createProcRout = {|d, index|
    Routine {
        loop{
            d.env.use{
                if(~fileTime != File.mtime(~filePath),{
                    ~fileTime = File.mtime(~filePath);
                    "[file saved]".postln;
                    NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/loadPersonality", d.port, index);
                }); 
                if(d.enabled == true,{
                    // pre-process data -> personality model
                    ~processDeviceData.(d);
                    //post process : make changes in personlity file
                    ~next.(d);
                    // State-gated tick: fires the ~<state>Next hook if
                    // defined. ~roomState lives in topEnvironment (set by
                    // conductorController), so we read it explicitly since
                    // we're inside d.env.use.
                    // No case for \silent — mute is handled one-shot in the
                    // personality's ~onRoomState \silent branch. Continuous
                    // amp updates would just re-set 0 every 30 ms.
                    // Second arg is the score-ctx snapshot (see conductor).
                    block {
                        var ctx = topEnvironment[\stateCtx];
                        switch(topEnvironment[\roomState],
                            \idle,    { ~idleNext.(d, ctx) },
                            \tuning,  { ~tuningNext.(d, ctx) },
                            \piece,   { ~pieceNext.(d, ctx) },
                            \curtain, { ~curtainNext.(d, ctx) }
                        );
                    };
                });
                (~secs.()).yield;
            };
        };
    }
};
//------------------------------------------------------------
// s
//------------------------------------------------------------

var loadPersonality = { |port, index|

    var d = ~devices.at(port);
    var devicePersonalityList = loadPersonalityList.(d.index-1);

    // d.personality = index;
    // stop personality
    d.procRout.stop;
    d.procRout.free;

    if(d.env != nil,{ d.env.use{
        Routine{
            s.sync;
            ~deinit.();
            s.sync;
            // ~group.free;
            s.sync;
        }.play;
    }});

    // change device to new personality
    index = index.mod(devicePersonalityList.size-1);
    d.name = devicePersonalityList[index];
    "loading personality: %".format(d.name).postln;

    NetAddr.new("127.0.0.1", NetAddr.langPort).sendMsg("/airkit/personalityName", d.port, d.name);
    d.env = interpretPersonality.(d);
    // ["TODO",d.enabled].postln;
    d.env.use{
        Routine{
            s.sync;
            ~init.(d);
            s.sync;
        }.play;
    };

    d.procRout = createProcRout.(d, index);
    d.procRout.reset.play(AppClock);

    ~devices.put(d.port,d);

    d

};
//------------------------------------------------------------
// 
//------------------------------------------------------------

var unLoadPersonality = { |port, index|

    var d = ~devices.at(port);
    var devicePersonalityList = loadPersonalityList.(d.index-1);

    // d.personality = index;
    // stop personality
    d.procRout.stop;
    d.procRout.free;

    if(d.env != nil,{ d.env.use{
        Routine{
            s.sync;
            ~deinit.();
            s.sync;
            // ~group.free;
            // s.sync;
        }.play;
    }});
    d
};

//------------------------------------------------------------
// 
//------------------------------------------------------------

var reLoadPersonality = { |port, index|

    var d = ~devices.at(port);
    // var devicePersonalityList = loadPersonalityList.(d.index-1);

    // change device to new personality
    // index = index.mod(devicePersonalityList.size-1);
    // d.name = devicePersonalityList[index];
    // "loading personality: %".format(d.name).postln;

    // NetAddr.new("127.0.0.1", NetAddr.langPort).sendMsg("/airkit/personalityName", d.port, d.name);
    d.env = interpretPersonality.(d);
    // ["TODO",d.enabled].postln;
    d.env.use{
        Routine{
            s.sync;
            ~init.(d);
            s.sync;
        }.play;
    };

    d.procRout = createProcRout.(d, index);
    d.procRout.reset.play(AppClock);

    ~devices.put(d.port,d);

    d

};

//------------------------------------------------------------
// 
//------------------------------------------------------------
~devices = Dictionary();

OSCFunc({ |msg, time, addr, recvPort|
    loadPersonality.(msg[1], msg[2]);
},'/airkit/loadPersonality'); //* not having an NetAddr makes this public ie. external control

OSCFunc({ |msg, time, addr, recvPort|
    unLoadPersonality.(msg[1], msg[2]);
},'/airkit/unLoadPersonality', NetAddr("127.0.0.1", NetAddr.langPort));

OSCFunc({ |msg, time, addr, recvPort|
    reLoadPersonality.(msg[1], msg[2]);
},'/airkit/reLoadPersonality', NetAddr("127.0.0.1", NetAddr.langPort));

// OSCFunc({ |msg, time, addr, recvPort|
//     NetAddr.new("127.0.0.1", NetAddr.langPort).sendMsg("/airkit/personalityName", msg[1], ~devices.at(msg[1]).name);
// },'/airkit/getPersonalityName', NetAddr("127.0.0.1", NetAddr.langPort));

//------------------------------------------------------------
// cotf: roster query — replies to the sender with the loaded personality
// names in index order (index == loadPersonality index), both `silence`
// entries included. loadPersonalityList is only in scope here (it's a
// closure-captured local, not a global ~ var), so this handler lives
// beside loadPersonality/unLoadPersonality rather than in
// conductorController.scd where getState lives. All 5 devices share the
// same defaultLists entry, so device 1's list (index 0) speaks for all.
// Reply idiom matches conductorController.scd's /airkit/getState handler.
// Additive; no composer behaviour change.
//------------------------------------------------------------
OSCFunc({ |msg, time, addr, recvPort|
    var names = loadPersonalityList.(0);
    addr.sendMsg("/airkit/roster/reply", *names);
},'/airkit/getRoster');

//------------------------------------------------------------
// cotf: live seats query — replies to the sender with flat
// (devicePort, personalityName) pairs for every live device, e.g.
// /airkit/seats/reply 9001 "cotf_harp1" 9002 "silence". Devices are
// created lazily on their first IMU packet (default personality
// "silence"), so the COTF server polls this to learn what is ACTUALLY
// loaded and re-push a saved assignment that a fresh device wiped —
// the boot-order race found on the 2026-07-15 bench. Additive; no
// composer behaviour change.
//------------------------------------------------------------
OSCFunc({ |msg, time, addr, recvPort|
    var pairs = List.new;
    ~devices.keysValuesDo{ |port, d| pairs.add(port); pairs.add(d.name) };
    addr.sendMsg("/airkit/seats/reply", *pairs.asArray);
},'/airkit/getSeats');
