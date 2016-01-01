BLE-V-Monitor is a battery voltage monitor for vehicles (cars, motorbikes). It consists of an Arduino-based monitoring device and an Android app. The BLE-V-Monitor device is connected to the car battery to monitor the battery voltage and record voltage histories. The app queries the current voltage and voltage history via Bluetooth Low Energy (BLE) and displays them to the user.

The main features of BLE-V-Monitor are:

- Current voltage and battery charge status monitoring
- Recording of minutely, hourly, and daily voltage histories
- Bluetooth Low Energy (BLE) to transmit voltage samples to smartphones, tablets, Internet gateways, etc.
- Very low energy consumption
- Android app for displaying current voltage and voltage histories
- Open source hardware (CERN Open Hardware Licence v1.2) and software (Apache License 2.0)

The following images show the BLE-V-Monitor device and screenshots from the app.

![BLE-V-Monitoring device](/img/ble-v-monitor_device.jpg)

![BLE-V-Monitoring device with case](/img/ble-v-monitor_device_case.jpg)

![BLE-V-Monitor app: current voltage](/img/screenshot_current_voltage.png)

![BLE-V-Monitor app: history](/img/screenshot_voltage_history.png)

# Implementation

BLE-V-Monitor consists of two parts: 

* the BLE-V-Monitor device (PCB and Arduino software)
* a smartphone app for Android. 

You can find the following files here:

* Schematics and PCB layout (Eagle files) of the monitoring device: directory `eagle`.
* Arduino source code for monitor device: directory `src`.
* BLE service definition for nRF8001 used by monitoring device: directory `nRFgo_studio`. 
* Android app source code: directory `android`.

The BLE-V-Monitor device periodically samples the voltage of the battery, and the app uses Bluetooth Low Energy (BLE) to query the battery voltage when the smartphone is close to the car. Instead of using a smartphone, you could also implement a client for other devices, e.g., a Raspberry Pi with a Bluetooth USB stick (not included so far).

## Prerequisites

To compile the code for the monitoring device, you need the Arduino IDE (tested with version 1.6.5).

Moreover, you need the Arduino BLE SDK from Nordic Semiconductors available 
from GitHub:

https://github.com/NordicSemiconductor/ble-sdk-arduino

1. Download SDK: `git clone https://github.com/NordicSemiconductor/ble-sdk-arduino.git`
2. Start the Arduino IDE (tested with v1.6.5)
3. Import the library: select `Sketch/Import library/Add` library and 
   then choose folder `libraries/BLE` from the downloaded SDK.
4. Check the availability of the library. There should be an entry `BLE`
   in menu `Sketch/Import library`.

The BLE services of nRF8001 are configured by the freely available nRFgo 
Studio tool (Windows application) [4]. This tool generates a file 
(`services.h`), which is also included in the repository. Thus, you  
only need to install this tool if you want to modify the BLE service 
description for some reason.

The Android app requires at least Android 4.3 (API level 18). You need to install Android Studio to edit the project provided in this repository.

## BLE-V-Monitor Device

The BLE-V-Monitor device is based on the Arduino platform. It uses an ATmega 328P microcontroller and the BLE module MOD-nRF8001 from Olimex [2] (Nordic Semiconductors nRF8001 BLE chip [1]). The ATmega is programmed via an in-system programmer (ISP) and interfaces with the BLE module through SPI. Due to small duty cycles, the current consumption can be below 100 microampere while no client is connected via BLE.

To measure voltage, we use the 10 bit analog/digital converter (ADC) of the ATmega. The voltage range that can be measured ranges from 0 to 18 V, thus, the resolution is 18 V / 1024 = 17.6 mV. Note that while the car is running, the car's alternator provides more than 12 V to charge the battery (about 15 V for my car as can be seen from the history screenshot). A voltage divider with large resistor values (to save energy) is used to divide the battery voltage. Since we use 2.5 V reference voltage, 18 V is mapped to 2.5 V by the voltage divider. A precise 2.5 V reference voltage is provided by the micropower voltage reference diode LM285-2.5, which is only powered on demand through a GPIO pin of the ATmega during sampling. Since the resistors of the voltage divider have large values to save energy, a 100 nF capacitor in parallel to the second resistor of the voltage divider provides a low impedance source to the ADC (this 100 nF capacitor is much larger than the 14 pF sampling capacitor of the ATmega).  

A 18 V varistor protects from transient voltage spikes above 18 V. Since varistors typically age whenever they shunt excessive voltage, a (slow) fuse limits the current to protect against a short circuit of the varistor.

A micropower voltage regulator (LP295x) provides 3.3 V to the ATmega and BLE module. The 100 mA that can be provided by this regulator are more than sufficient to power the ATmega and BLE module while being active, and a very low quiescent current of only 75 microampere ensures efficient operation with small duty cycles.

## Programming the Monitoring Device

The monitoring device is programmed via ISP. The 6 pin ISP6 connector has the usual layout:

    MISO <-- 1 2 --> VCC
     SCK <-- 3 4 --> MOSI
     RST <-- 5 6 --> GND

You can leave the BLE module connected while programming the ATmega (the 
4.7 k resistors shield the SPI pins of the nRF8001 during programming). However,
you should use a 3.3 V programmer since the maximum voltage of the nRF8001
is 3.6 V.

To preprare the ATmega328P, program the following fuses (note that "0" means 
that the fuse is programmed):

* CKSEL = 0010: use internal 8 MHz RC oscillator
* CKDIV8 = 1: 8 MHz system clock (do not divide the 8 MHz internal clock by 8)
* SPIEN = 0: enable serial programming
* BODLEVEL = 101: brown-out detection set to 2.7 V
* SUT = 00: lowest possible startup time (6 CK from power-down; since we use
  BOD, we do not need additional waiting time for the power source to come up)
    
Using avrdude, the command looks like this (note: under Linux you might need
to execute this command as root depending on your programmer):

    $ avrdude -c usbasp -p m328p -U lfuse:w:0xc2:m -U hfuse:w:0xd9:m -U efuse:w:0x05:m

Make sure to compile your code for the correct board. In the repository, 
you will find a suitable board definition for the Arduino IDE (see folder 
`board_definition`). Copy the directory `ble-v-monitor` into the folder 
`hardware` in your Arduino sketchbook. Then you should find and select the 
board  called `BLE-V-Monitor -- ATmega328P @ 8 MHz` under the menu item `Tools/Board`. 

Compiling generates a hex file that we need to program the ATmega. This hex 
file is a little bit hidden in the temporary build directory of the Arduino
IDE. If you use Linux  and Arduino IDE 1.6, have a look at the `/tmp` 
directory. After hitting the  compile button in the Arduino IDE, search for the
latest hex file called  `ble-v-monitor.cpp.hex` in a temporary directory named 
`/tmp/build...`. If you have found the hex file, you can write it using avrdude
(again, you might need root right):

    $ avrdude -p m328p -c usbasp -v -U flash:w:/tmp/build6535101126624677692.tmp/ble-v-monitor.cpp.hex

## BLE-V-Monitor App

The BLE-V-Monitor App is implemented for Android 4.3 and above. It consists of a tabed view with a fragment to display the current voltage, and three more fragments to display minutely, hourly, and daily voltage histories, respectively.

The charge status of a lead–acid car battery can be quite easily derived from its voltage. We use the following voltage levels to estimate the charge status: 

- 100 % charged (fully charged): about 12.66 V
- 75 % charged (charged): about 12.35 V
- 50 % charged (weakly charged): about 12.10 V
- 25 % charged (discharged): about 11.95 V
- 0 % charged (over discharged): about 11.7 V

# Licensing

BLE-V-Monitor uses two licenses for open hardware and software, respectively:

* The software (source code) is licensed under the Apache License 2.0 [5]
* The hardware (schematic diagrams, circuit board layouts, hardware
  documentation) is licensed under the CERN Open Hardware Licence v1.2 [6]

# References

* [1] https://www.nordicsemi.com/eng/Products/Bluetooth-Smart-Bluetooth-low-energy/nRF8001 
* [2] https://www.olimex.com/Products/Modules/RF/MOD-nRF8001/
* [3] https://github.com/NordicSemiconductor/ble-sdk-arduino
* [4] https://www.nordicsemi.com/chi/node_176/2.4GHz-RF/nRFgo-Studio
* [5] http://www.apache.org/licenses/LICENSE-2.0
* [6] http://www.ohwr.org/attachments/2388/cern_ohl_v_1_2.txt