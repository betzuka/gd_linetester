#ifndef GLM100C_H
#define GLM100C_H

#include <Arduino.h>

#define GLM_MAX_DATA_SIZE 20
#define GLM_STATUS_OK 0
#define GLM_STATUS_COMM_TIMEOUT 1
#define GLM_STATUS_CS_ERR   3
#define GLM_STATUS_UNKNOWN_CMD  4
#define GLM_STATUS_INVALID_ACL 5
#define GLM_STATUS_HW_ERR 8
#define GLM_STATUS_DEV_NOT_READY 10
#define GLM_STATUS_NO_RESP 11

class GLM100C {
    private:
        uint8_t glm_cmd_measure[4] = {0xC0,0x40,0x00,0xEE};
        uint8_t glm_cmd_laser_on[4] = {0xC0,0x41,0x00,0x96};
        uint8_t glm_cmd_laser_off[4] = {0xC0,0x42,0x00,0x1E};
        uint8_t glm_cmd_backlight_on[4] = {0xC0,0x47,0x00,0x20};
        uint8_t glm_cmd_backlight_off[4] = {0xC0,0x48,0x00,0x62};
        uint8_t glm_cmd_get_serial[4] = {0xC0,0x06,0x00,0x4A};
        uint8_t glm_cmd_firmware[4] = {0xC0,0x04,0x00,0xBA};
        uint8_t glmData[GLM_MAX_DATA_SIZE];
        Stream* stream;
        uint32_t timeout;
        uint8_t sendCmd(uint8_t *cmd, int len, uint32_t timeout) {

            //clear any dross
            while (stream->available()) {
                stream->read();
            }
        
            uint8_t status = GLM_STATUS_NO_RESP;
            stream->write(cmd, len);
            uint32_t now = millis();
        
            while (stream->available()<2 && millis()-now<timeout) {
                
            }
        
            if (stream->available()>=2) {
        
                status = stream->read();
                uint8_t respLength = stream->read();
                       
                while (stream->available()<respLength+1 && millis()-now<timeout) {
                    
                }
        
                if (stream->available()>=respLength+1) {
                    for (int i=0;i<respLength;i++) {
                        uint8_t c = stream->read();
                        if (i<GLM_MAX_DATA_SIZE) {
                            glmData[i] = c;
                        }      
                    }
                   
                }
                
            }
          
            return status;
        }
    public:
        GLM100C();
        
        void begin(Stream* stream, uint32_t timeout);
        
        long measure();

        int laserOn();
        int laserOff();
};

#endif
