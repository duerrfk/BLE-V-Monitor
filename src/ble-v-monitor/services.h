/**
* This file is autogenerated by nRFgo Studio 1.20.0.2
*/

#ifndef SETUP_MESSAGES_H__
#define SETUP_MESSAGES_H__

#include "hal_platform.h"
#include "aci.h"


#define SETUP_ID 0
#define SETUP_FORMAT 3 /** nRF8001 D */
#define ACI_DYNAMIC_DATA_SIZE 147

/* Service: BLE_V_Monitor - Characteristic: Battery Voltage - Pipe: TX */
#define PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_TX          1
#define PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_TX_MAX_SIZE 2

/* Service: BLE_V_Monitor - Characteristic: Battery Voltage - Pipe: SET */
#define PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_SET          2
#define PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_SET_MAX_SIZE 2

/* Service: BLE_V_Monitor - Characteristic: Battery Voltage History Minutely - Pipe: TX_ACK */
#define PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_HISTORY_MINUTELY_TX_ACK          3
#define PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_HISTORY_MINUTELY_TX_ACK_MAX_SIZE 2

/* Service: BLE_V_Monitor - Characteristic: Battery Voltage History Daily - Pipe: TX_ACK */
#define PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_HISTORY_DAILY_TX_ACK          4
#define PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_HISTORY_DAILY_TX_ACK_MAX_SIZE 2

/* Service: BLE_V_Monitor - Characteristic: Battery Voltage History Hourly - Pipe: TX_ACK */
#define PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_HISTORY_HOURLY_TX_ACK          5
#define PIPE_BLE_V_MONITOR_BATTERY_VOLTAGE_HISTORY_HOURLY_TX_ACK_MAX_SIZE 2


#define NUMBER_OF_PIPES 5

#define SERVICES_PIPE_TYPE_MAPPING_CONTENT {\
  {ACI_STORE_LOCAL, ACI_TX},   \
  {ACI_STORE_LOCAL, ACI_SET},   \
  {ACI_STORE_LOCAL, ACI_TX_ACK},   \
  {ACI_STORE_LOCAL, ACI_TX_ACK},   \
  {ACI_STORE_LOCAL, ACI_TX_ACK},   \
}

#define GAP_PPCP_MAX_CONN_INT 0xffff /**< Maximum connection interval as a multiple of 1.25 msec , 0xFFFF means no specific value requested */
#define GAP_PPCP_MIN_CONN_INT  0xffff /**< Minimum connection interval as a multiple of 1.25 msec , 0xFFFF means no specific value requested */
#define GAP_PPCP_SLAVE_LATENCY 0
#define GAP_PPCP_CONN_TIMEOUT 0xffff /** Connection Supervision timeout multiplier as a multiple of 10msec, 0xFFFF means no specific value requested */

#define NB_SETUP_MESSAGES 25
#define SETUP_MESSAGES_CONTENT {\
    {0x00,\
        {\
            0x07,0x06,0x00,0x00,0x03,0x02,0x42,0x07,\
        },\
    },\
    {0x00,\
        {\
            0x1f,0x06,0x10,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x04,0x00,0x05,0x00,0x06,0x00,0x00,0x06,0x00,0x05,\
            0xd0,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,\
        },\
    },\
    {0x00,\
        {\
            0x1f,0x06,0x10,0x1c,0x01,0x02,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,\
            0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x24,0x03,0x90,0x01,0xff,\
        },\
    },\
    {0x00,\
        {\
            0x1f,0x06,0x10,0x38,0xff,0xff,0x02,0x58,0x0a,0x05,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,\
            0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,\
        },\
    },\
    {0x00,\
        {\
            0x05,0x06,0x10,0x54,0x00,0x00,\
        },\
    },\
    {0x00,\
        {\
            0x1f,0x06,0x20,0x00,0x04,0x04,0x02,0x02,0x00,0x01,0x28,0x00,0x01,0x00,0x18,0x04,0x04,0x05,0x05,0x00,\
            0x02,0x28,0x03,0x01,0x02,0x03,0x00,0x00,0x2a,0x04,0x04,0x14,\
        },\
    },\
    {0x00,\
        {\
            0x1f,0x06,0x20,0x1c,0x0d,0x00,0x03,0x2a,0x00,0x01,0x42,0x4c,0x45,0x5f,0x56,0x5f,0x4d,0x6f,0x6e,0x69,\
            0x74,0x6f,0x72,0x6d,0x00,0x00,0x00,0x00,0x00,0x00,0x04,0x04,\
        },\
    },\
    {0x00,\
        {\
            0x1f,0x06,0x20,0x38,0x05,0x05,0x00,0x04,0x28,0x03,0x01,0x02,0x05,0x00,0x01,0x2a,0x06,0x04,0x03,0x02,\
            0x00,0x05,0x2a,0x01,0x01,0x00,0x00,0x04,0x04,0x05,0x05,0x00,\
        },\
    },\
    {0x00,\
        {\
            0x1f,0x06,0x20,0x54,0x06,0x28,0x03,0x01,0x02,0x07,0x00,0x04,0x2a,0x06,0x04,0x09,0x08,0x00,0x07,0x2a,\
            0x04,0x01,0xff,0xff,0xff,0xff,0x00,0x00,0xff,0xff,0x04,0x04,\
        },\
    },\
    {0x00,\
        {\
            0x1f,0x06,0x20,0x70,0x02,0x02,0x00,0x08,0x28,0x00,0x01,0x01,0x18,0x04,0x04,0x10,0x10,0x00,0x09,0x28,\
            0x00,0x01,0xb2,0xd3,0x19,0x85,0xe8,0x33,0x1a,0x9a,0x38,0x4d,\
        },\
    },\
    {0x00,\
        {\
            0x1f,0x06,0x20,0x8c,0xaf,0xf0,0x01,0x00,0x0e,0xde,0x04,0x04,0x13,0x13,0x00,0x0a,0x28,0x03,0x01,0x12,\
            0x0b,0x00,0xb2,0xd3,0x19,0x85,0xe8,0x33,0x1a,0x9a,0x38,0x4d,\
        },\
    },\
    {0x00,\
        {\
            0x1f,0x06,0x20,0xa8,0xaf,0xf0,0x00,0x01,0x0e,0xde,0x16,0x04,0x03,0x02,0x00,0x0b,0x01,0x00,0x02,0x00,\
            0x00,0x06,0x04,0x08,0x07,0x00,0x0c,0x29,0x04,0x01,0x0e,0xfd,\
        },\
    },\
    {0x00,\
        {\
            0x1f,0x06,0x20,0xc4,0x28,0x27,0x01,0x00,0x00,0x46,0x14,0x03,0x02,0x00,0x0d,0x29,0x02,0x01,0x00,0x00,\
            0x04,0x04,0x13,0x13,0x00,0x0e,0x28,0x03,0x01,0x22,0x0f,0x00,\
        },\
    },\
    {0x00,\
        {\
            0x1f,0x06,0x20,0xe0,0xb2,0xd3,0x19,0x85,0xe8,0x33,0x1a,0x9a,0x38,0x4d,0xaf,0xf0,0x00,0x02,0x0e,0xde,\
            0x26,0x04,0x03,0x02,0x00,0x0f,0x02,0x00,0x02,0x00,0x00,0x06,\
        },\
    },\
    {0x00,\
        {\
            0x1f,0x06,0x20,0xfc,0x04,0x08,0x07,0x00,0x10,0x29,0x04,0x01,0x0e,0xfd,0x28,0x27,0x01,0x00,0x00,0x46,\
            0x14,0x03,0x02,0x00,0x11,0x29,0x02,0x01,0x00,0x00,0x04,0x04,\
        },\
    },\
    {0x00,\
        {\
            0x1f,0x06,0x21,0x18,0x13,0x13,0x00,0x12,0x28,0x03,0x01,0x22,0x13,0x00,0xb2,0xd3,0x19,0x85,0xe8,0x33,\
            0x1a,0x9a,0x38,0x4d,0xaf,0xf0,0x00,0x04,0x0e,0xde,0x26,0x04,\
        },\
    },\
    {0x00,\
        {\
            0x1f,0x06,0x21,0x34,0x03,0x02,0x00,0x13,0x04,0x00,0x02,0x00,0x00,0x06,0x04,0x08,0x07,0x00,0x14,0x29,\
            0x04,0x01,0x0e,0xfd,0x28,0x27,0x01,0x00,0x00,0x46,0x14,0x03,\
        },\
    },\
    {0x00,\
        {\
            0x1f,0x06,0x21,0x50,0x02,0x00,0x15,0x29,0x02,0x01,0x00,0x00,0x04,0x04,0x13,0x13,0x00,0x16,0x28,0x03,\
            0x01,0x22,0x17,0x00,0xb2,0xd3,0x19,0x85,0xe8,0x33,0x1a,0x9a,\
        },\
    },\
    {0x00,\
        {\
            0x1f,0x06,0x21,0x6c,0x38,0x4d,0xaf,0xf0,0x00,0x03,0x0e,0xde,0x26,0x04,0x03,0x02,0x00,0x17,0x03,0x00,\
            0x02,0x00,0x00,0x06,0x04,0x08,0x07,0x00,0x18,0x29,0x04,0x01,\
        },\
    },\
    {0x00,\
        {\
            0x16,0x06,0x21,0x88,0x0e,0xfd,0x28,0x27,0x01,0x00,0x00,0x46,0x14,0x03,0x02,0x00,0x19,0x29,0x02,0x01,\
            0x00,0x00,0x00,\
        },\
    },\
    {0x00,\
        {\
            0x1f,0x06,0x40,0x00,0x01,0x00,0x02,0x00,0x82,0x04,0x00,0x0b,0x00,0x0d,0x02,0x00,0x02,0x00,0x04,0x04,\
            0x00,0x0f,0x00,0x11,0x04,0x00,0x02,0x00,0x04,0x04,0x00,0x13,\
        },\
    },\
    {0x00,\
        {\
            0x0f,0x06,0x40,0x1c,0x00,0x15,0x03,0x00,0x02,0x00,0x04,0x04,0x00,0x17,0x00,0x19,\
        },\
    },\
    {0x00,\
        {\
            0x13,0x06,0x50,0x00,0xb2,0xd3,0x19,0x85,0xe8,0x33,0x1a,0x9a,0x38,0x4d,0xaf,0xf0,0x00,0x00,0x0e,0xde,\
        },\
    },\
    {0x00,\
        {\
            0x0f,0x06,0x60,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,\
        },\
    },\
    {0x00,\
        {\
            0x06,0x06,0xf0,0x00,0x03,0x83,0x7e,\
        },\
    },\
}

#endif
