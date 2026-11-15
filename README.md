# Guard the Vault

A Java arcade game controlled using an Arduino joystick and two buttons. Reach the vault, avoid the security sweep, and hold the action button to crack the vault.

## Features

- Three levels with different maps.
- Two obstacles in Level 1, three in Level 2, and four in Level 3.
- Strike limits: three, two, and one respectively.
- Time limits: 60, 50, and 40 seconds respectively.
- Green hiding areas protect a stationary player.
- Hold the action button near the vault for two seconds to complete a level.
- A confirmation prompt appears before advancing to the next level.
- Scores reward faster completion and fewer strikes.
- Local top-ten leaderboard.
- Arduino controls and keyboard controls.
- RGB feedback: blue during play, green while hidden, and red after detection.

## Software Requirements

- Java 17.
- Eclipse IDE or another Java IDE.
- jSerialComm library added to the Java build path.
- Arduino IDE with Arduino AVR Boards installed.

## Hardware

- LilyPad Arduino with ATmega328P.
- FTDI USB-to-serial adapter.
- Analog joystick.
- Two push buttons.
- RGB LED module with onboard resistors.
- Monitor connected to the computer.

The Java game runs on the computer. The Arduino reads the controls and operates the RGB LED.

## Connections

| Component | Arduino pin |
| --- | --- |
| Joystick X | A0 |
| Joystick Y | A1 |
| Start button | 3 |
| Action button | 4 |
| RGB red | 9 |
| RGB green | 10 |
| RGB blue | 11 |
| Common ground | GND |

Buttons connect between their input pin and GND and use INPUT_PULLUP.

Pin 5 is kept LOW; vibration feedback is disabled in this version.

## Setup

1. Download or clone this repository.
2. Open GuardTheVaultController.ino in Arduino IDE. If prompted, allow Arduino IDE to place it in a matching sketch folder.
3. Select LilyPad Arduino and the appropriate ATmega328P processor and serial port.
4. Upload the sketch.
5. Close Arduino Serial Monitor so the Java application can use the serial port.
6. Create a Java project in Eclipse using Java 17.
7. Copy the src folder into the project.
8. Add the jSerialComm JAR through Java Build Path > Libraries > Classpath > Add External JARs.
9. In ArduinoInput.java, set the serial port to the port shown in Arduino IDE.
10. Ensure both the Arduino sketch and Java connection use 9600 baud.
11. Run guardthevault.Main as a Java application.

## Controls

| Action | Arduino | Keyboard |
| --- | --- | --- |
| Move | Joystick | Arrow keys |
| Start or restart | Start button | Enter |
| Crack vault | Hold action button | Hold Space |
| Return to menu during gameplay | — | Escape |

Use the mouse or keyboard to respond to level confirmation dialogs.

## Scoring

Each completed level awards:

max(100, 1000 + remaining seconds × 20 − strikes × 150)

Points from completed levels are added together. An unfinished level awards no points. Leaderboard records are stored locally on the computer.

## Strategy Design Pattern

The security sweep uses the Strategy pattern:

- SweepStrategy defines the sweep behaviour.
- SteadySweep implements a continuous sweep.
- PauseAndSweep implements a sweep with pauses.
- GamePanel uses the selected strategy.
- The main menu lets the player choose a strategy before starting.

## Project Files

- Main.java: application window and main menu.
- GamePanel.java: gameplay, maps, scoring, and graphics.
- ArduinoInput.java: serial communication.
- SweepStrategy.java: strategy interface.
- SteadySweep.java: continuous sweep strategy.
- PauseAndSweep.java: paused sweep strategy.
- Leaderboard.java: local score storage and rankings.
- SerialTest.java: serial diagnostic utility.
- GuardTheVaultController.ino: Arduino controller firmware.

## Team Contributions

| Member | Contribution |
| --- | --- |
| Ahmed Almash | Software development and cabinet assembly |
| Rashed Alduwaihy | Software development |
| Rouda Alawadhi | Hardware and cabinet design |
| Haleema Saud | Hardware |

## Current Limitations

- Requires a computer to run the Java application.
- The serial port may need updating when changing computers or USB connections.
- Close other serial applications before running the game.
- ZigBee, door sensing, motion sensing, and vibration feedback are not implemented in this version.
