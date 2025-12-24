#include "../../include/utils/logger_utils.h"

const char *cmd_to_string(int cmd) {
    switch (cmd) {
        case CMD_REGISTER:
            return "CMD_REGISTER";
        case CMD_REGISTER_RESP:
            return "CMD_REGISTER_RESP";
        case CMD_LOGIN:
            return "CMD_LOGIN";
        case CMD_LOGIN_RESP:
            return "CMD_LOGIN_RESP";
        default:
            return "CMD_UNKNOWN";
    }
}

const char *status_to_string(int status) {
    switch (status) {
        case STATUS_SUCCESS:
            return "SUCCESS";
        case STATUS_ERROR_AUTH:
            return "AUTH_ERROR";
        case STATUS_ERROR_USER_NOT_FOUND:
            return "USER_NOT_FOUND";
        case STATUS_ERROR_ALREADY_EXIST:
            return "ALREADY_EXIST";
        case STATUS_ERROR_DB:
            return "DB_ERROR";
        default:
            return "UNKNOWN_ERROR";
    }
}
