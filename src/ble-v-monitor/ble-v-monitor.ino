/**
 * This file is part of BLE-V-Monitor.
 * 
 * Copyright 2015 Frank Duerr
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
// Need to include EEPROM.h here although it is not reference from this file
// (at least Arduino 1.5.8 IDE fails to include it automatically). 
#include <EEPROM.h>
#include <SPI.h>
#include <avr/wdt.h>
#include <avr/sleep.h>
#include <lib_aci.h>
#include <aci_setup.h>
#include "services.h"
#include "commands.h"

// Define your MCU. 
//#define ARDUINO_PRO_MICRO
#define ATMEGA328P

// Debug output over serial is not available with the
// BLE-V-Monitor board. However, if you use Arduino Pro Micro
// for developing, it might be handy.
//#define DEBUG

// For production system (BLE-V-Monitor board), enable 
// power-down mode to save energy (micro-amps while sleeping).
// DO NOT ENABLE THIS ON Arduino Pro Micro SINCE A SLEEPING
// ARDUINO CANNOT BE PROGRAMMED RELIABLY VIA SERIAL CONNECTION!!!
#define DO_SLEEP

// Disabling brown-out detecting while sleeping saves about 25 uA.
// Not all ATmega MCUs support this. ATmega328P used by the 
// BLE-V-Monitor board does. ATmega32U4 (e.g., used by Arduino 
// Pro Micro) doesn't.
#define DISABLE_BOD_WHILE_SLEEPING

//// Arduino pins connected to nRF8001.
#ifdef ARDUINO_PRO_MICRO
    // The following values are valid for Arduino Pro Micro. 
    #define PIN_MOSI 16
    #define PIN_MISO 14
    #define PIN_SCK 15
    // nRF8001 uses two "slave select" pins, one from Arduino (master) to nRF8001
    // (slave) called REQN, and one from slave to master called RDYN for signaling 
    // events from nRF8001 to Arduino. For RDYN select a pin with attached 
    // hardware interrupt to enable wake-up from sleep mode. 
    #define PIN_REQN 7
    #define PIN_RDYN 3  
    #define PIN_RST 4

    // Analog pin used for measuring voltage
    #define PIN_ADC_IN A0
    // Digital pin powering reference voltage
    #define PIN_POWER_ADC_REF_VOLTAGE 5
#endif

#ifdef ATMEGA328P
    // The following pins are valid for ATmega328P
    #define PIN_MOSI 11
    #define PIN_MISO 12
    #define PIN_SCK 13
    // nRF8001 uses two "slave select" pins, one from Arduino (master) to nRF8001
    // (slave) called REQN, and one from slave to master called RDYN for signaling 
    // events from nRF8001 to Arduino. For RDYN select a pin with attached 
    // hardware interrupt to enable wake-up from sleep mode. 
    #define PIN_REQN 10
    #define PIN_RDYN 2 
    #define PIN_RST 3

    // Analog pin used for measuring voltage
    #define PIN_ADC_IN A0
    // Digital pin powering reference voltage
    #define PIN_POWER_ADC_REF_VOLTAGE 7
#endif

// We use this interrupt to react to RDYN events. nRF8001 pulls
// RDYN low, when it has some data to send to the MCU.
#define RDYN_INTR_NO 0

// nRF8001 supports max. 3 MHz SPI clock frequency.
// Our Arduino runs at 8 MHz. By dividing by 4, we
// set the SPI clock frequency to 2 MHz.    
#define SPI_CLOCK_DIV SPI_CLOCK_DIV4

// BLE advertisement interval in 0.625 ms, min 0x0020 (20 ms), 
// max 0x4000 (10.240 s). 1600 -> 1 s
#define ADV_INTERVAL 1600

//// nRF8001 data structures
#ifdef SERVICES_PIPE_TYPE_MAPPING_CONTENT
services_pipe_type_mapping_t services_pipe_type_mapping[NUMBER_OF_PIPES] = 
    SERVICES_PIPE_TYPE_MAPPING_CONTENT;
#else
#define NUMBER_OF_PIPES 0
services_pipe_type_mapping_t *services_pipe_type_mapping = NULL;
#endif

// Store the setup for the nRF8001 in the flash of the AVR (PROGMEM) to save 
// on RAM
const hal_aci_data_t setup_msgs[NB_SETUP_MESSAGES] PROGMEM = 
    SETUP_MESSAGES_CONTENT;

// External reference voltage in volts
const float ref_voltage = 2.5;
// Voltage scale-up factor due to external voltage divider 
// (R1 = 100k, R2 = 16k). 
const float voltage_scaleup = (100.0+16.0)/16.0;
// Compensate for constant voltage offset (in volts).
// Requires calibration of device. Alternatively, we can
// compensate for errors on the client side (in the "app").
const float voltage_offset = 0.0;

// nRF8001 can only process one system command at a time.
// After sending a request, we need to wait for the response event
// before sending the next command. In order to simplify implementation,
// we queue pending commands in a command queue. Whenever a response
// event is received, the next command is taken out of the queue and sent
// to nRF8001. The command queue is implemented as a ring buffer.
#define CMD_QUEUE_SIZE 16
#define CMD_QUEUE_SIZE_MOD_MASK 0x0f 
enum commands cmd_queue[CMD_QUEUE_SIZE];
unsigned int cmd_queue_head = 0;
unsigned int cmd_queue_tail = 0;
unsigned int cmd_queue_free = CMD_QUEUE_SIZE;
boolean cmd_queue_pending = false;

struct aci_state_t aci_state;
hal_aci_evt_t  aci_data;
hal_aci_data_t aci_cmd;
boolean is_connected = false;

volatile boolean ble_ready = false;
volatile boolean timer_fired = false;

// This number indicates an invalid voltage
#define INVALID_VOLTAGE -1
// Voltage in micro-volts. 
int16_t voltage = INVALID_VOLTAGE;

// Timeout for receiving data credits in seconds.
#define CREDIT_TIMEOUT_THRESHOLD 180
unsigned int credit_timeout = 0;

// Timeout to take voltage samples in seconds.
// Should be an integral divisor of 60 (= one minute) to correctly
// calculate the minute timeout threshold below.
#define SAMPLE_TIMEOUT_THRESHOLD 10
unsigned int sample_timeout = 0;
const unsigned int minute_timeout_threshold = 60/SAMPLE_TIMEOUT_THRESHOLD;
unsigned int minute_timeout = 0;

unsigned int hour_timeout = 0;

unsigned int day_timeout = 0;

#define MAX_SEQ_NO 0xffffffffUL
#define HISTORY_RING_SIZE 128
#define HISTORY_RING_SIZE_MODMASK 0x7f

boolean is_voltage_notification_due = false;

boolean is_minutely_history_due = false;
uint32_t minutely_history_send_seqno = 0;
uint32_t minutely_history_head = 0;
int16_t minutely_history_ring[HISTORY_RING_SIZE];

boolean is_hourly_history_due = false;
uint32_t hourly_history_send_seqno = 0;
uint32_t hourly_history_head = 0;
int16_t hourly_history_ring[HISTORY_RING_SIZE];

boolean is_daily_history_due = false;
uint32_t daily_history_send_seqno = 0;
uint32_t daily_history_head = 0;
int16_t daily_history_ring[HISTORY_RING_SIZE];

boolean is_pipe_open_minutely_history = false;
boolean is_pipe_open_hourly_history = false;
boolean is_pipe_open_daily_history = false;

byte adcsra_save = ADCSRA;

/**
 * Called after a fatal error. 
 */
void die()
{  
    // Enable watchdog system reset mode to reboot system with next
    // watch dog event. 
    //WDTCSR |= _BV(WDE);
    
    // Go into endless loop.
    while (true) {
        #ifdef DEBUG
        Serial.println("DEAD");
        #endif
    }
}

/**
 * Enqueue a command into the command queue. 
 * 
 * @param command the command to be enqueued
 * 
 * @returns true if the command fits into the queue; false if
 *          there is no space in the queue anymore.
 */
boolean cmd_queue_enqueue(enum commands command)
{
    if (cmd_queue_free == 0)
        return false;

    cmd_queue[cmd_queue_head] = command;
    cmd_queue_head++;
    cmd_queue_head &= CMD_QUEUE_SIZE_MOD_MASK;
    cmd_queue_free--;
    
    // Try to send command
    cmd_queue_send_cmd();

    return true;
}

/**
 * If no other command is pending and there is at least
 * one queued command, send next command to nRF8001.
 */
void cmd_queue_send_cmd() 
{
    // Cannot send a command if either a command is pending 
    // or queue is empty
    if (cmd_queue_pending || cmd_queue_free == CMD_QUEUE_SIZE)
        return;
        
    enum commands cmd = cmd_queue[cmd_queue_tail];
    switch (cmd) {
    case cmd_start_advertising:
        // Start advertising in connectable mode.
        // First parameter defines the time how long to
        // advertise (0 = forever). Second parameter is advertisement 
        // interval in milli-seconds.
        #ifdef DEBUG
        Serial.println("ADVERTISE");
        #endif
        lib_aci_connect(0, ADV_INTERVAL);
        break;
    case cmd_change_timing:
        #ifdef DEBUG
        Serial.println("CHANGE TIMING");
        #endif
        lib_aci_change_timing_GAP_PPCP();
        break;
    case cmd_disconnect:
        #ifdef DEBUG
        Serial.println("DISCONNECT");
        #endif
        lib_aci_disconnect(&aci_state, ACI_REASON_TERMINATE);
        break;
    case cmd_set_data_voltage:
        #ifdef DEBUG
        Serial.println("SET DATA");
        #endif
        // Data is expected in Little Endian order. ATmega is LE.
        lib_aci_set_local_data(&aci_state, 
                               PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_SET,
                               (uint8_t *) &voltage, sizeof(int16_t));    
    }
    
    cmd_queue_pending = true;
    
    cmd_queue_tail++;
    cmd_queue_tail &= CMD_QUEUE_SIZE_MOD_MASK;
    
    cmd_queue_free++;
}

/**
 * Received response for pending command.
 */
void cmd_queue_response_recvd()
{
    cmd_queue_pending = false;

    // Can send next enqueued command now (if any is available)
    cmd_queue_send_cmd();
}
  
/**
 * This function is used by BLE library to signal failed assertions.
 */
void __ble_assert(const char *file, uint16_t line)
{
    while (true);
}

/**
 * ISR for RDYN low events
 */
void rdyn_isr()
{
    // This is a level interrupt that would fire again while the
    // signal is low. Thus, we need to detach the interrupt.
    detachInterrupt(RDYN_INTR_NO);
    ble_ready = true;  
}

/**
 * Watch dog interrupt. Will be fired every second.
 * 
 * The watch dog will keep runing even in power-down mode.
 * When it fires, it will wake-up the Arduino.
 */
ISR(WDT_vect)
{  
    timer_fired = true;
}

/**
 * Setup watch dog to trigger interrupts every 1 s.
 */
void start_watchdog()
{
    // WDTCSR: Watch Dog Control Status Register
    // WDIE: Watchdog Interrupt Enable
    // WDCE: Watchdog Change Enabled
    // WDE: Watchdog System Reset Enabled
    // WDRF: Watchdog Reset Flag
    /* Watch Dog Prescaler (WDP3-0)
    0         0      0        0             2K (2048) cycles      16 ms
    0         0      0        1             4K (4096) cycles      32 ms
    0         0      1        0             8K (8192) cycles      64 ms
    0         0      1        1            16K (16384) cycles     0.125 s
    0         1      0        0            32K (32768) cycles     0.25 s
    0         1      0        1            64K (65536) cycles     0.5 s
    0         1      1        0            128K (131072) cycles   1.0 s
    0         1      1        1            256K (262144) cycles   2.0 s
    1         0      0        0            512K (524288) cycles   4.0 s
    1         0      0        1            1024K (1048576) cycles 8.0 s
    */
    // Clear WDRF or it will overwrite cleared WDE.
    MCUSR &= ~_BV(WDRF);
    byte sreg_saved;
    sreg_saved = SREG;
    cli();
    // Watchdog modus:
    //  WDE, ~WDIE: system reset mode
    // ~WDE,  WDIE: interrupt mode
    //  WDE,  WDIE: interrupt mode; after first interrupt go to system reset 
    //              mode 
    // Changing timing and clearing WDE require a timed sequence:
    // Set WDCE bit; then change settings within 4 clock cycles.
    WDTCSR |= _BV(WDCE) | _BV(WDE);
    // Disable watchdog reset, enable interrupt mode, interval 1 s
    WDTCSR = _BV(WDP2) | _BV(WDP1) | _BV(WDIE);
    SREG = sreg_saved;
    wdt_reset();
}

/**
 * One-time setup.
 */
void setup()
{     
    wdt_disable();

    #ifdef DEBUG
    Serial.begin(9600);
    // Serial port of Arduino Pro Micro takes some time to be detected
    delay(5000);
    #endif

    // Configure pin providing reference voltage for ADC as output
    pinMode(PIN_POWER_ADC_REF_VOLTAGE, OUTPUT);
    // Switch it off to save energy while ADC is not used.
    digitalWrite(PIN_POWER_ADC_REF_VOLTAGE, LOW);
    
    // Use external voltage reference for ADC.
    analogReference(EXTERNAL);
    
    // Disable ADC (saves about 300 uA). ADC is re-enabled on demand 
    // when required.
    adcsra_save = ADCSRA;
    ADCSRA = 0;

    // Setup nRF800 using the definitions from services.h created by nRFgo 
    // Studio
    if (services_pipe_type_mapping != NULL) {
        aci_state.aci_setup_info.services_pipe_type_mapping = 
            &services_pipe_type_mapping[0];
    } else {
        aci_state.aci_setup_info.services_pipe_type_mapping = NULL;
    }
    aci_state.aci_setup_info.number_of_pipes = NUMBER_OF_PIPES;
    aci_state.aci_setup_info.setup_msgs = (hal_aci_data_t *) setup_msgs;
    aci_state.aci_setup_info.num_setup_msgs = NB_SETUP_MESSAGES;
    
    // Pin mapping from nRF8001 to Arduino. Not connected pins are defined as 
    // UNUSED.
    aci_state.aci_pins.board_name = BOARD_DEFAULT;
    aci_state.aci_pins.reqn_pin = PIN_REQN;
    aci_state.aci_pins.rdyn_pin = PIN_RDYN;
    aci_state.aci_pins.mosi_pin = PIN_MOSI;
    aci_state.aci_pins.miso_pin = PIN_MISO;
    aci_state.aci_pins.sck_pin = PIN_SCK;
    aci_state.aci_pins.reset_pin = PIN_RST;
    aci_state.aci_pins.active_pin = UNUSED;
    aci_state.aci_pins.optional_chip_sel_pin = UNUSED;
    
    aci_state.aci_pins.spi_clock_divider = SPI_CLOCK_DIV;
    
    // We implement our own interrupt handling to better control
    // when we spend time for nRF8001 event processing and when for 
    // other things like sensor readings. Moreover, this will help
    // us to implement a correct sleep/wake-up procedure that
    // is not missing events.
    // Therefore, we turn off interrupts here and poll nRF8001
    // in the event loop whenever we think it's suitable.
    aci_state.aci_pins.interface_is_interrupt = false;
    aci_state.aci_pins.interrupt_number = 0;
    
    // Reset nRF8001. 
    // Set second parameter to true for debug output.
    lib_aci_init(&aci_state, false);
    
    // Install interrupt for RDYN line of nRF8001 for event handling.
    // We use a level-interrupt that can also fire in sleep mode to
    // wake up the Arduino when an event is received.
    attachInterrupt(RDYN_INTR_NO, rdyn_isr, LOW);

    start_watchdog();
}

/**
 * Send pending data.
 * 
 * nRF8001 has a buffer for data items. Data credits are used to indicate
 * how many data items can be buffered by nRF8001. This function makes sure
 * that no more data is sent to nRF8001 than can be buffered.
 * 
 * Moreover, this function ensures that there is at most one pending 
 * acknowledged data item at a time.
 */
void send_pending_data()
{
    if (aci_state.data_credit_available == 0 || aci_state.confirmation_pending)
        return;

    // No pending acknowledged operation; credits available -> can send more data

    if (is_voltage_notification_due) {
        if (lib_aci_is_pipe_available(&aci_state, 
                                      PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_TX)) {
            // Data is expected in Little Endian order. ATmega is LE.
            lib_aci_send_data(PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_TX,
                              (uint8_t *) &voltage, sizeof(int16_t));
            aci_state.data_credit_available--;
            credit_timeout = 0;
        }
        is_voltage_notification_due = false;
    }

    if (aci_state.data_credit_available == 0 || aci_state.confirmation_pending)
        return;
    
    if (is_minutely_history_due) {
        // The following cases are stopping conditions:
        // - Previously sent seq. no. 0 (cannot go before seq. no. 0): 
        //   minutely_history_send_seqno == MAX_SEQ_NO:   
        // - head ran into history tail while sending history:
        //   minutely_history_head >= minutely_history_send_seqno+HISTORY_RING_SIZE
        if (minutely_history_head >= minutely_history_send_seqno+HISTORY_RING_SIZE || 
            minutely_history_send_seqno == MAX_SEQ_NO) {
            // No data in the ring or already sent all data.
            // Send invalid voltage value to signal "end of history"
            int16_t data = INVALID_VOLTAGE;
            // Data is expected in Little Endian order. ATmega is LE.
            lib_aci_send_data(PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_HISTORY_MINUTELY_TX_ACK,
                              (uint8_t *) &data, sizeof(int16_t));
            aci_state.data_credit_available--;
            aci_state.confirmation_pending = true;
            credit_timeout = 0;
            is_minutely_history_due = false;
        } else {
            // There is more history data to be sent
            int16_t data = minutely_history_ring[minutely_history_send_seqno&HISTORY_RING_SIZE_MODMASK];
            
            // Data is expected in Little Endian order. ATmega is LE.
            lib_aci_send_data(PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_HISTORY_MINUTELY_TX_ACK,
                              (uint8_t *) &data, sizeof(int16_t));
            aci_state.data_credit_available--;
            aci_state.confirmation_pending = true;
            credit_timeout = 0;
            // One step backwards in history. We do not need to deal explicitly with
            // the warp around at 0--. According to the rules, 0-- will produce 
            // max(uint32_t), which we check above as stopping condition.
            minutely_history_send_seqno--;
        }
    }

    if (aci_state.data_credit_available == 0 || aci_state.confirmation_pending)
        return;

    if (is_hourly_history_due) {
        if (hourly_history_head >= hourly_history_send_seqno+HISTORY_RING_SIZE || 
            hourly_history_send_seqno == MAX_SEQ_NO) {
            // No data in the ring or already sent all data.
            // Send invalid voltage value to signal "end of history"
            int16_t data = INVALID_VOLTAGE;
            // Data is expected in Little Endian order. ATmega is LE.
            lib_aci_send_data(PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_HISTORY_HOURLY_TX_ACK,
                              (uint8_t *) &data, sizeof(int16_t));
            aci_state.data_credit_available--;
            aci_state.confirmation_pending = true;
            credit_timeout = 0;
            is_hourly_history_due = false;
        } else {
            // There is more history data to be sent
            int16_t data = hourly_history_ring[hourly_history_send_seqno&HISTORY_RING_SIZE_MODMASK];
            // Data is expected in Little Endian order. ATmega is LE.
            lib_aci_send_data(PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_HISTORY_HOURLY_TX_ACK,
                              (uint8_t *) &data, sizeof(int16_t));
            aci_state.data_credit_available--;
            aci_state.confirmation_pending = true;
            credit_timeout = 0;
            // One step backwards in history. We do not need to deal explicitly with
            // the warp around at 0--. According to the rules, 0-- will produce 
            // max(uint32_t), which we check above as stopping condition.
            hourly_history_send_seqno--;
        }
    }

    if (aci_state.data_credit_available == 0 || aci_state.confirmation_pending)
        return;

    if (is_daily_history_due) {
        if (daily_history_head >= daily_history_send_seqno+HISTORY_RING_SIZE || 
            daily_history_send_seqno == MAX_SEQ_NO) {
            // No data in the ring or already sent all data.
            // Send invalid voltage value to signal "end of history"
            int16_t data = INVALID_VOLTAGE;
            // Data is expected in Little Endian order. ATmega is LE.
            lib_aci_send_data(PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_HISTORY_DAILY_TX_ACK,
                              (uint8_t *) &data, sizeof(int16_t));
            aci_state.data_credit_available--;
            aci_state.confirmation_pending = true;
            credit_timeout = 0;
            is_daily_history_due = false;
        } else {
            // There is more history data to be sent
            int16_t data = daily_history_ring[daily_history_send_seqno&HISTORY_RING_SIZE_MODMASK];
            // Data is expected in Little Endian order. ATmega is LE.
            lib_aci_send_data(PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_HISTORY_DAILY_TX_ACK,
                              (uint8_t *) &data, sizeof(int16_t));
            aci_state.data_credit_available--;
            aci_state.confirmation_pending = true;
            credit_timeout = 0;
            // One step backwards in history. We do not need to deal explicitly with
            // the warp around at 0--. According to the rules, 0-- will produce 
            // max(uint32_t), which we check above as stopping condition.
            daily_history_send_seqno--;
        }
    }    
}

/**
 * BLE event loop. Exits, if no more events are there to be processed at 
 * the moment.
 */
void aci_loop()
{  
    while (lib_aci_event_get(&aci_state, &aci_data)) {
        // Only entered if there is an event to be processed.
        aci_evt_t *aci_evt;
        aci_evt = &aci_data.evt;
        switch(aci_evt->evt_opcode) {
        case ACI_EVT_DEVICE_STARTED:
            aci_state.data_credit_total = 
                aci_evt->params.device_started.credit_available;
            switch(aci_evt->params.device_started.device_mode) {
            case ACI_DEVICE_SETUP:
                aci_state.device_state = ACI_DEVICE_SETUP;
                if (do_aci_setup(&aci_state) == SETUP_SUCCESS) {
                    #ifdef DEBUG
                    Serial.println("SETUP SUCCESS");
                    #endif
                } else {
                    #ifdef DEBUG
                    Serial.println("SETUP FAILED");
                    #endif
                    // This is a fatal error. We bail out.
                    die();
                }
                break;
            case ACI_DEVICE_STANDBY:
                aci_state.device_state = ACI_DEVICE_STANDBY;
                // Start advertising.
                cmd_queue_enqueue(cmd_start_advertising);
            }
            break;
        case ACI_EVT_CMD_RSP:
            #ifdef DEBUG
            Serial.println("CMD_RSP");
            #endif
            
            // Signal to the command queue that no command is pending anymore
            // and the next enqueued command can be sent now.
            cmd_queue_response_recvd();           
            break;
        case ACI_EVT_DATA_ACK:
            // Data has been acknowledged by peer.
            #ifdef DEBUG
            Serial.println("ACK");
            #endif
            aci_state.confirmation_pending = false;
            // Try to send pending data.
            send_pending_data();
            break;    
        case ACI_EVT_PIPE_STATUS:
            // Status of a pipe has changed.
            // A connection event is followed by a pipe status event when the pipe
            // becomes available.
            #ifdef DEBUG
            Serial.println("PIPE STATUS");
            #endif
            if (lib_aci_is_pipe_available(&aci_state, 
                                          PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_SET)) {
                // Battery voltage characteristic now available.
                cmd_queue_enqueue(cmd_set_data_voltage);
            }
            if (lib_aci_is_pipe_available(&aci_state, 
                                          PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_HISTORY_MINUTELY_TX_ACK)) {
                if (!is_pipe_open_minutely_history) {
                    // Pipe just became available. Start sending indications for each item
                    // in the history buffer. We start from the ring buffer head and proceed 
                    // backwards.
                    is_pipe_open_minutely_history = true;
                    is_minutely_history_due = true;
                    // minutely_history_head is the next place to insert a new element.
                    // Thus, we need to start sending history values at position head-1,
                    // which is the last position written.
                    minutely_history_send_seqno = minutely_history_head-1;
                    send_pending_data();
                }
            } else {
                is_pipe_open_minutely_history = false;
                is_minutely_history_due = false;
            }
            if (lib_aci_is_pipe_available(&aci_state, 
                                          PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_HISTORY_HOURLY_TX_ACK)) {
                if (!is_pipe_open_hourly_history) {
                    // Pipe just became available. Start sending indications for each item
                    // in the history buffer. We start from the ring buffer head and proceed 
                    // backwards.
                    is_pipe_open_hourly_history = true;
                    is_hourly_history_due = true;
                    hourly_history_send_seqno = hourly_history_head-1;
                    send_pending_data();
                }
            } else {
                is_pipe_open_hourly_history = false;
                is_hourly_history_due = false;
            }
            if (lib_aci_is_pipe_available(&aci_state, 
                                          PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_HISTORY_DAILY_TX_ACK)) {
                if (!is_pipe_open_daily_history) {
                    // Pipe just became available. Start sending indications for each item
                    // in the history buffer. We start from the ring buffer head and proceed 
                    // backwards.
                    is_pipe_open_daily_history = true;
                    is_daily_history_due = true;
                    daily_history_send_seqno = daily_history_head-1;
                    send_pending_data();
                }
            } else {
                is_pipe_open_daily_history = false;
                is_daily_history_due = false;
            }
            break;
        case ACI_EVT_TIMING:
            // Timing of connection changed.
	          aci_state.connection_interval = aci_evt->params.timing.conn_rf_interval;
            aci_state.slave_latency = aci_evt->params.timing.conn_slave_rf_latency;
            break;
        case ACI_EVT_CONNECTED:
            // Connection established.
            #ifdef DEBUG
            Serial.println("CONNECTED");
            #endif
            is_connected = true; 
            aci_state.data_credit_available = aci_state.data_credit_total;
            aci_state.confirmation_pending = false;
            aci_state.slave_latency = 0;
            is_pipe_open_minutely_history = false;
            is_pipe_open_hourly_history = false;
            is_pipe_open_daily_history = false;
            // Request to change to the preferred link timing as set in 
            // nRFgo Studio application.
            cmd_queue_enqueue(cmd_change_timing);
            break;
        case ACI_EVT_DATA_CREDIT:
            // Buffers used by nRF8001 for data packets became available.
            #ifdef DEBUG
            Serial.println("CREDIT");
            #endif
            aci_state.data_credit_available += aci_evt->params.data_credit.credit;
            // Reset the credit timer.
            credit_timeout = 0;
            // Try to send pending data.
            send_pending_data();
            break;
        case ACI_EVT_PIPE_ERROR:
            #ifdef DEBUG
            Serial.println("PIPE ERROR");
            #endif
            if (aci_evt->params.pipe_error.error_code != 
                ACI_STATUS_ERROR_PEER_ATT_ERROR) {
                // Sending data failed. 
                // This can happen if a pipe becomes unavailable by 
                // unsubscribing to the characteristic or when the link is 
                // disconnected after the data packet has been sent.
                // Increment the credit since ACI_EVT_DATA_CREDIT will not come.
                aci_state.data_credit_available++;
                // If the error comes from an acknowledged pipe, do no wait
                // for the ACK since ACI_EVT_DATA_ACK will not come.
                switch (aci_evt->params.pipe_error.pipe_number) {
                case PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_HISTORY_MINUTELY_TX_ACK:
                case PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_HISTORY_HOURLY_TX_ACK:
                case PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_HISTORY_DAILY_TX_ACK:
                    aci_state.confirmation_pending = false;
                    break;
                }
            }
            break;
        case ACI_EVT_DISCONNECTED:
            // Connection closed.
            #ifdef DEBUG
            Serial.println("DISCONNECTED");
            #endif
            if (aci_evt->params.disconnected.aci_status == 
                ACI_STATUS_ERROR_ADVT_TIMEOUT) {
                // Should never happen since we advertise forever.
            } else {
                // Lost the link. New connections can be made now. 
                // Start advertising in connectable mode.
                cmd_queue_enqueue(cmd_start_advertising);
            }
            is_connected = false;
            break;
        case ACI_EVT_HW_ERROR:
            // Hardware error. This is a fatal error. We bail out.
            #ifdef DEBUG
            Serial.println("HARDWARE ERROR");
            #endif
            die();
            break;
        }
    } 
}

/**
 * Put device into power-down mode to save as much energy as possible.
 * 
 * The device will wake up again by interrupt, either the watch dog timer 
 * or RDYN going low (= nRF8001 sends an event to device).
 */
void do_sleep()
{
    set_sleep_mode(SLEEP_MODE_PWR_DOWN);
    // Disable interrupts until we sleep to avoid race conditions
    // (interrupt firing before going to sleep would prevent MCU from
    // waking by interrupt).
    cli();
    if (ble_ready || timer_fired) {
        // Last chance to stay awake.
        sei();
    } else {
        sleep_enable();
        #ifdef DISABLE_BOD_WHILE_SLEEPING
        // Disabling brown-out detection while sleeping 
        // Saves about 25 uA.
        // BODS: Brown-out Detection Sleep
        // BODSE: Brown-out Detection Sleep Enable
        // This is a timed sequence:
        // First, BODS and BODSE must me set to one.
        // Then, BODS must be set to one and BODSE to zero
        // within four clock cycles. Then, BODS stays active three
        // clock cycles, so sleep_cpu() must be called within
        // three cycles after setting BODS.
        MCUCR = bit(BODS) | bit(BODSE);
        MCUCR = bit(BODS);
        #endif 
        // Enable interrupts again. It is guranteed that the next
        // command (entering sleep mode) is executed *before* an 
        // interrupt is fired (no race condition). From the data sheet:
        // "When using the SEI instruction to enable interrupts, 
        // the instruction following SEI will be executed
        // before any pending interrupts."
        sei();
        sleep_cpu();
        // Wake again after interrupt.
        sleep_disable();
    }
}

/**
 * Take a voltage sample.
 */
void take_sample()
{
    // Turn on ADC again.
    ADCSRA = adcsra_save;
    
    // Turn on the reference voltage. The voltage reference is defined by 
    // an external diode powered through PIN_POWER_ADC_REF_VOLTAGE.
    digitalWrite(PIN_POWER_ADC_REF_VOLTAGE, HIGH);

    // Throw away first conversion result
    uint16_t sample = analogRead(PIN_ADC_IN);
    sample = analogRead(PIN_ADC_IN);

    // Measured voltage in micro volts.
    float v = ((float) sample/1024.0) * (ref_voltage*1000.0);
    // Consider voltage divider ratio.
    v = v*voltage_scaleup;
    // Compensate for constant offset.
    v += voltage_offset*1000.0;
    voltage = (int16_t) (v + 0.5);
    
    // Turn off reference voltage to save energy.
    digitalWrite(PIN_POWER_ADC_REF_VOLTAGE, LOW);

    // Turn off ADC to save energy
    ADCSRA = 0;
}

/**
 * Main loop
 */
void loop()
{                 
    if (ble_ready) {
        // Process all pending ACI events from nRF8001.
        aci_loop();
        ble_ready = false;
        // nRF8001 will cause an interrupt when more events are available
        attachInterrupt(RDYN_INTR_NO, rdyn_isr, LOW);
    }

    if (timer_fired) {
        #ifdef DEBUG
        Serial.println("TIMER EVENT"); // DEBUG
        #endif
        
        // "If no credit event is received within 180 seconds after issuing
        // a data command, then the application controller should issue the 
        // disconnect command to recover from this error condition."
        credit_timeout++;
        boolean credit_pending = aci_state.data_credit_available < 
                                 aci_state.data_credit_total;                         
        if (is_connected && credit_pending && 
            credit_timeout >= CREDIT_TIMEOUT_THRESHOLD)
            cmd_queue_enqueue(cmd_disconnect);

        sample_timeout++;
        if (sample_timeout > SAMPLE_TIMEOUT_THRESHOLD) {
            #ifdef DEBUG
            Serial.println("SAMPLE TIMEOUT");
            #endif
            take_sample();
            sample_timeout = 0;
            // Update voltage characteristic.
            if (lib_aci_is_pipe_available(&aci_state, 
                                      PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_SET)) {
                cmd_queue_enqueue(cmd_set_data_voltage);
            }
            // Send notification if there is a subscriber
            is_voltage_notification_due = true;
            send_pending_data();

            minute_timeout++;
            if (minute_timeout >= minute_timeout_threshold) {
                // Completed another minute
                #ifdef DEBUG
                Serial.println("MINUTE TIMEOUT");
                #endif
                minutely_history_ring[minutely_history_head&HISTORY_RING_SIZE_MODMASK] = voltage;
                minutely_history_head++;
                minute_timeout = 0;
                
                // Hour timeout is measured in minutes
                hour_timeout++;
                if (hour_timeout >= 60) {
                    // Completed another hour
                    #ifdef DEBUG
                    Serial.println("HOUR TIMEOUT");
                    #endif
                    hourly_history_ring[hourly_history_head&HISTORY_RING_SIZE_MODMASK] = voltage;
                    hourly_history_head++;
                    hour_timeout = 0;
                    
                    // Day timeout is measured in hours
                    day_timeout++;
                    if (day_timeout >= 24) {
                        #ifdef DEBUG
                        Serial.println("DAY TIMEOUT");
                        #endif
                        daily_history_ring[daily_history_head&HISTORY_RING_SIZE_MODMASK] = voltage;
                        daily_history_head++;
                        day_timeout = 0;
                    }
                }
            }
        }
       
        timer_fired = false;
    }

    #ifdef DO_SLEEP
    do_sleep();
    #endif
}

