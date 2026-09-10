# OSC API Documentation

### /airkit/startOSCListening
- **Parameters**: [port]
- **Description**: Starts/triggers OSC listening for device detection

### /airkit/stopOSCListening
- **Parameters**: None
- **Description**: Stops OSC listening and frees listeners

### /airkit/addDevice
- **Parameters**: [port]
- **Description**: Adds a detected device to the system

### /airkit/loadPersonality
- **Parameters**: [port, personality_index]
- **Description**: Loads a personality for a specific device

### /airkit/personalityName
- **Parameters**: [port, name]
- **Description**: Sets/gets the personality name for a device

### /airkit/param
- **Parameters**: [port, name, value]
- **Description**: Sets a device param — `volume` or `sensitivity`. `value` is
  normalised and clamped to 0..1. Public, like `/airkit/mute`, so the panel,
  the webapp and MIDI can all drive it. An unknown device or an unknown param
  name is ignored, so OSC cannot add keys to the model.
- **Replies**: `/airkit/paramIs`

### /airkit/paramIs
- **Parameters**: [port, name, value]
- **Description**: Broadcast after a param actually changes, carrying the
  clamped value. Subscribe to this to keep a UI in sync rather than assuming
  the value you sent was the value taken. `controlView`'s slider does not
  subscribe — its draw loop reads the model each frame — but a Button-style
  widget or an out-of-process client should.

Params persist across restarts, keyed by the stick's own ID (`/Config/SetID`,
the number in its OSC address), not by IP — AirStick leases are dynamic. They
live in `state/deviceParams.scd`, which is gitignored and outside
`release_dirs`, so a web update never overwrites them.

