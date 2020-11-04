#include "GLM100C.h"

GLM100C::GLM100C() {}

void GLM100C::begin(Stream* stream, uint32_t timeout) {
    this->stream = stream;
    this->timeout = timeout;
}

long GLM100C::measure() {
    uint8_t status = sendCmd(glm_cmd_measure, sizeof(glm_cmd_measure), timeout);

    if (status==GLM_STATUS_OK) {
        uint32_t meas = 0;
        for (int i=0;i<4;i++) {
            meas |= ((uint32_t)(glmData[i]))<<(8*i);
        }
        //int mm = (int)((meas * 0.05) + 0.5);
        //return mm;
        return (long)((meas*0.5) + 0.5);
    }
    return -1;
}

int GLM100C::laserOn() {
    return sendCmd(glm_cmd_laser_on, sizeof(glm_cmd_laser_on), timeout);
}
int GLM100C::laserOff() {
    return sendCmd(glm_cmd_laser_off, sizeof(glm_cmd_laser_off), timeout);
}

