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

