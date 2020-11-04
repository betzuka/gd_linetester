#include <Bounce2.h>
#include <AltSoftSerial.h>
#include <AutoPID.h>
#include "HX711.h"
#include "GLM100C.h"

/**
 * Green dragons line tester
 * 
 * Forces are in grams, lengths in mm
 */

#define LIMIT_EXTEND_PIN    11
#define LIMIT_RETRACT_PIN   12
#define MOTOR_EXTEND_PIN    6
#define MOTOR_RETRACT_PIN   5
#define HX_DAT_PIN          2
#define HX_CLK_PIN          3

#define STATE_ESTOP         1
#define STATE_READY         2
#define STATE_HOMING        3
#define STATE_TESTING       4

#define MOTOR_RETRACTING    1
#define MOTOR_EXTENDING     2
#define MOTOR_STOPPED       3

#define FORCE_HYST_FACTOR   0.15

#define PRE_TENSION_FORCE   2500
#define MAX_RETRACT_FORCE   200000
#define MAX_EXTEND_FORCE    -20000
#define LOAD_CELL_OFFSET    -7000
#define LOAD_CELL_SCALE     -0.0446
#define LOADCELL_GAIN       128

#define COMMAND_TERM        0x0D           //carriage return
#define COMMAND_BUF_CAPACITY 20

#define LASER_TIMEOUT       2000


struct SerialBuffer {
    int len;
    char buf[COMMAND_BUF_CAPACITY];
};

SerialBuffer command;

void resetCommand() {
    command.len = 0;
}

bool readInts(char* buf, long* ints, int len, int base) {
    char *rest = buf;
    char *token;
    int i=0;
    while (i<len && (token = strtok(rest, ","))!=NULL) {
        ints[i++] = strtol(token, NULL, base);
        rest = NULL;
    }
    return i==len;
}

bool readCommand(Stream* stream) {
    if (stream->available()) {
        uint8_t next = (uint8_t)stream->read();
        command.buf[command.len++] = next;
        if (next==COMMAND_TERM) {
            //terminate string
            command.buf[command.len-1] = '\0';
            return true;
        } else if (command.len>=COMMAND_BUF_CAPACITY) {
            resetCommand();
        }
    }
    return false;
}



AltSoftSerial btSerial;
GLM100C laser;

HX711 loadcell;
Bounce extendLimit = Bounce();
Bounce retractLimit = Bounce();


struct TestSpec {
    long force;
    int holdTime;
    bool returnHome;
};

TestSpec testSpec;

void sendTestReport(bool success, int state, long targetForce, long force, long lastForce, long length, bool lineBreak) {
    Serial.print("M,");
    Serial.print(state);
    Serial.print(",");
    Serial.print(success ? "1" : "0");
    Serial.print(",");
    Serial.print(targetForce);
    Serial.print(",");
    Serial.print(force);
    Serial.print(",");
    Serial.print(lastForce);
    Serial.print(",");
    Serial.print(length);
    Serial.print(",");
    Serial.print(lineBreak ? "1" : "0");
    Serial.print("\n");
}

void sendResponse(bool success, int state, long force, bool extendLimitHit, bool retractLimitHit, bool forceLimitHit, long rawForce) {
    Serial.print("Z,");
    Serial.print(success ? "1" : "0");
    Serial.print(",");
    Serial.print(state);
    Serial.print(",");
    Serial.print(force);
    Serial.print(",");
    Serial.print(extendLimitHit ? "1" : "0");
    Serial.print(",");
    Serial.print(retractLimitHit ? "1" : "0");
    Serial.print(",");
    Serial.print(forceLimitHit ? "1" : "0");
    Serial.print(",");
    Serial.print(rawForce);
    Serial.print("\n");  
}

void setup() {
    btSerial.begin(38400);  
    laser.begin(&btSerial, LASER_TIMEOUT); 
    
    extendLimit.attach(LIMIT_EXTEND_PIN, INPUT_PULLUP);
    retractLimit.attach(LIMIT_RETRACT_PIN, INPUT_PULLUP);
    extendLimit.interval(10);
    retractLimit.interval(10);
    pinMode(MOTOR_EXTEND_PIN, OUTPUT);
    pinMode(MOTOR_RETRACT_PIN, OUTPUT);
    loadcell.begin(HX_DAT_PIN, HX_CLK_PIN);
    loadcell.set_gain(LOADCELL_GAIN);


    Serial.begin(115200);
}

long measureLength() {
    return laser.measure();
    //return -1;
}




void loop() {
    static int state = STATE_READY;
    static long lastForce = 0;
    static uint32_t holdStartTime = 0;
    static int motorDir = MOTOR_STOPPED;
    static bool debugLoadCell = true;
    static uint32_t lastDebugMsg = 0;
    static bool measuring = false;
    
    uint32_t now = millis();
    //read command
    boolean commandSuccess = false;
    boolean commandReceived = false;
    
    if (readCommand(&Serial)) {
        commandReceived = true;
        //we have a valid command
        switch (command.buf[0]) {
            case 'E': //estop; break;
                state = STATE_ESTOP;
                commandSuccess = true;
                break;
            case 'R': //reset estop; break;
                if (state==STATE_ESTOP) {
                    state = STATE_READY;
                    commandSuccess = true;
                }
                break;
            case 'T': //start test; break;
                if (state==STATE_READY) {
                    long args[3];
                    if (readInts(command.buf+2, args, 3, 10)) {
                        if (args[0]<MAX_RETRACT_FORCE) {
                            testSpec.force = args[0];
                            testSpec.holdTime = args[1] * 1000;
                            testSpec.returnHome = args[2]==1;    
                            state = STATE_TESTING;
                            commandSuccess = true;
                            holdStartTime = now;
                            measuring = false;
                            
                        }
                    }
                }
                break;
            case 'H': //home; break;
                if (state==STATE_READY) {
                    state = STATE_HOMING;
                    commandSuccess = true;
                }
                break;
            case 'L': //visible laser on
                
                if (laser.laserOn()==GLM_STATUS_OK) {
                    commandSuccess = true;
                }
                
                break;
            case 'l': //visible laser off
            
                if (laser.laserOff()==GLM_STATUS_OK) {
                    commandSuccess = true;
                }
                
                break;
            case 'S': //return status; break;
                commandSuccess = true;
                break;
            case 'D': //debug load cell
                debugLoadCell = !debugLoadCell;
                commandReceived = false;
                break;
        }
        
        resetCommand();
    }
    
    
    //read limit switches
    extendLimit.update();
    retractLimit.update();
    bool extendLimitHit = extendLimit.read()==HIGH;
    bool retractLimitHit = retractLimit.read()==HIGH;

    long rawForce = loadcell.read();
    long force = (long)(LOAD_CELL_SCALE * (rawForce-LOAD_CELL_OFFSET));
  
    bool forceLimitHit = false;
    
    if (force<MAX_EXTEND_FORCE || force>MAX_RETRACT_FORCE) {
        state = STATE_ESTOP;
        forceLimitHit = true;
    }
    
    switch (state) {
        case STATE_HOMING:
            //check for limit switch
            if (extendLimitHit) {
                state = STATE_READY;   
                motorDir = MOTOR_STOPPED;             
            } else {
                motorDir = MOTOR_EXTENDING; 
            }
            break;
        case STATE_TESTING:
            //check for line break
            if (force > PRE_TENSION_FORCE && force < lastForce/2) {
                //line break!!!
                state = STATE_HOMING;
                sendTestReport(false, state, testSpec.force, force, lastForce, measureLength(), true);
            }
            //check for limit switch
            else if (retractLimitHit) {
                state = STATE_HOMING;
                sendTestReport(false, state, testSpec.force, lastForce, 0, 0, false);    
            } else {
                

                long error = force - testSpec.force;
                long hyst = (long)(FORCE_HYST_FACTOR * testSpec.force);
                
                if (!measuring && error>2*hyst) {
                    motorDir = MOTOR_EXTENDING;
                }
                else if (!measuring && error<hyst) {
                    //still taking up tension
                    motorDir = MOTOR_RETRACTING;
                } else {
                    motorDir = MOTOR_STOPPED;
                    if (!measuring) {
                        measuring = true;
                        holdStartTime = now;
                    }
                }

                if (measuring && now-holdStartTime>=testSpec.holdTime) {
                    motorDir = MOTOR_STOPPED;
                    //test complete send report
                    if (testSpec.returnHome) {
                        state = STATE_HOMING;
                    } else {
                        state = STATE_READY;
                    }
                    sendTestReport(true, state, testSpec.force, force, lastForce, measureLength(), false);
                }
                
                
            }
            break;
        case STATE_ESTOP:
            motorDir = MOTOR_STOPPED;
            break;
    }
    //safety check limits
    if (motorDir==MOTOR_RETRACTING && !retractLimitHit) {
        digitalWrite(MOTOR_EXTEND_PIN, LOW);
        digitalWrite(MOTOR_RETRACT_PIN, HIGH);
    } else if (motorDir==MOTOR_EXTENDING && !extendLimitHit) {
        digitalWrite(MOTOR_RETRACT_PIN, LOW);
        digitalWrite(MOTOR_EXTEND_PIN, HIGH);
    } else {
        digitalWrite(MOTOR_EXTEND_PIN, LOW);
        digitalWrite(MOTOR_RETRACT_PIN, LOW);
    }

    lastForce = force;
    if (commandReceived || (debugLoadCell && now-lastDebugMsg>200)) {
        sendResponse(commandSuccess, state, force, extendLimitHit, retractLimitHit, forceLimitHit, rawForce);
        lastDebugMsg = now;
    }
}
